package com.batchjobapp.service;

import com.batchjobapp.model.ActionResponse;
import com.batchjobapp.model.JobMetricsResponse;
import com.batchjobapp.model.JobReportRequest;
import com.batchjobapp.model.JobReportSnapshot;
import com.batchjobapp.model.JobStatusResponse;
import com.batchjobapp.store.JobReportStore;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobCondition;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@ApplicationScoped
public class JobControlService {
    private static final Logger LOG = LoggerFactory.getLogger(JobControlService.class);

    private final KubernetesClient client;
    private final JobReportStore reportStore;

    @Inject
    public JobControlService(KubernetesClient client, JobReportStore reportStore) {
        this.client = client;
        this.reportStore = reportStore;
    }

    public ActionResponse start(String namespace, String jobName) {
        requireJob(namespace, jobName);
        patchSuspend(namespace, jobName, false);

        LOG.info("Started job {}/{} by unsuspending", namespace, jobName);
        return action(namespace, jobName, "start", "RUNNING", "Job unsuspended");
    }

    public ActionResponse stop(String namespace, String jobName) {
        requireJob(namespace, jobName);
        patchSuspend(namespace, jobName, true);
        int deletedPods = deletePods(namespace, jobName);

        LOG.info("Stopped job {}/{} and deleted {} pod(s)", namespace, jobName, deletedPods);
        return action(namespace, jobName, "stop", "SUSPENDED", "Job suspended and active pods deleted: " + deletedPods);
    }

    public ActionResponse restart(String namespace, String jobName) {
        Job existing = requireJob(namespace, jobName);
        patchSuspend(namespace, jobName, true);
        int deletedPods = deletePods(namespace, jobName);

        client.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
        waitForDeletion(namespace, jobName);

        Job recreated = createRestartedJob(existing);
        client.batch().v1().jobs().inNamespace(namespace).resource(recreated).create();

        LOG.info("Restarted job {}/{} by delete/recreate. Deleted {} pod(s)", namespace, jobName, deletedPods);
        return action(namespace, jobName, "restart", "RUNNING", "Job recreated and started");
    }

    public JobStatusResponse status(String namespace, String jobName) {
        Job job = requireJob(namespace, jobName);
        JobStatus status = job.getStatus();

        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        boolean suspended = job.getSpec() != null && Boolean.TRUE.equals(job.getSpec().getSuspend());

        Instant startTime = parseInstant(status == null ? null : status.getStartTime());
        Instant completionTime = parseInstant(status == null ? null : status.getCompletionTime());
        Long elapsed = computeElapsedSeconds(startTime, completionTime);

        String phase = resolvePhase(job, active, succeeded, failed, suspended);
        JobReportSnapshot latestReport = reportStore.get(namespace, jobName).orElse(null);

        return new JobStatusResponse(
            namespace,
            jobName,
            phase,
            suspended,
            active,
            succeeded,
            failed,
            startTime,
            completionTime,
            elapsed,
            latestReport
        );
    }

    public JobMetricsResponse metrics(String namespace, String jobName) {
        Job job = requireJob(namespace, jobName);
        PodList podList = client.pods().inNamespace(namespace).withLabel("job-name", jobName).list();
        List<Pod> pods = podList.getItems();

        int totalRestarts = 0;
        Integer lastExitCode = null;
        String lastTerminationReason = null;
        String lastTerminationMessage = null;

        for (Pod pod : pods) {
            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                continue;
            }
            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                totalRestarts += cs.getRestartCount() == null ? 0 : cs.getRestartCount();
                if (cs.getState() != null && cs.getState().getTerminated() != null) {
                    ContainerStateTerminated terminated = cs.getState().getTerminated();
                    if (terminated.getExitCode() != null) {
                        lastExitCode = terminated.getExitCode();
                    }
                    if (terminated.getReason() != null && !terminated.getReason().isBlank()) {
                        lastTerminationReason = terminated.getReason();
                    }
                    if (terminated.getMessage() != null && !terminated.getMessage().isBlank()) {
                        lastTerminationMessage = terminated.getMessage();
                    }
                }
            }
        }

        JobStatus jobStatus = job.getStatus();
        Instant startTime = parseInstant(jobStatus == null ? null : jobStatus.getStartTime());
        Instant completionTime = parseInstant(jobStatus == null ? null : jobStatus.getCompletionTime());
        Long elapsed = computeElapsedSeconds(startTime, completionTime);

        Optional<JobReportSnapshot> report = reportStore.get(namespace, jobName);
        Map<String, String> fallbackAttributes = new LinkedHashMap<>();
        if (lastTerminationReason != null) {
            fallbackAttributes.put("terminationReason", lastTerminationReason);
        }
        if (lastTerminationMessage != null) {
            fallbackAttributes.put("terminationMessage", lastTerminationMessage);
        }

        return new JobMetricsResponse(
            namespace,
            jobName,
            pods.size(),
            totalRestarts,
            lastExitCode,
            startTime,
            completionTime,
            elapsed,
            report.map(JobReportSnapshot::status).orElse(null),
            report.map(JobReportSnapshot::metrics).orElse(null),
            report.map(JobReportSnapshot::attributes).orElse(fallbackAttributes.isEmpty() ? null : Map.copyOf(fallbackAttributes))
        );
    }

    public ActionResponse report(String namespace, String jobName, JobReportRequest request) {
        requireJob(namespace, jobName);

        if (request == null || isBlankReport(request)) {
            LOG.info("Accepted empty report for job {}/{}", namespace, jobName);
            return action(namespace, jobName, "report", "REPORTED", "Empty report accepted");
        }

        reportStore.put(namespace, jobName, request);

        LOG.info("Stored report for job {}/{} with status {}", namespace, jobName, request.status());
        return action(namespace, jobName, "report", "REPORTED", "Report accepted");
    }

    private Job requireJob(String namespace, String jobName) {
        Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
        if (job == null) {
            throw new NoSuchElementException("Job not found: " + namespace + "/" + jobName);
        }
        return job;
    }

    private void patchSuspend(String namespace, String jobName, boolean suspend) {
        client.batch().v1().jobs().inNamespace(namespace).withName(jobName).edit(j -> new JobBuilder(j)
            .editOrNewSpec()
            .withSuspend(suspend)
            .endSpec()
            .build());
    }

    private int deletePods(String namespace, String jobName) {
        PodList podList = client.pods().inNamespace(namespace).withLabel("job-name", jobName).list();
        int podCount = podList.getItems().size();
        if (podCount > 0) {
            client.pods().inNamespace(namespace).withLabel("job-name", jobName).delete();
        }
        return podCount;
    }

    private void waitForDeletion(String namespace, String jobName) {
        for (int i = 0; i < 30; i++) {
            Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
            if (job == null) {
                return;
            }
            sleep(1000);
        }
        throw new IllegalStateException("Timed out waiting for Job deletion: " + namespace + "/" + jobName);
    }

    private Job createRestartedJob(Job source) {
        String name = source.getMetadata().getName();
        String namespace = source.getMetadata().getNamespace();

        JobBuilder builder = new JobBuilder(source)
            .editOrNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withResourceVersion(null)
            .withUid(null)
            .withCreationTimestamp(null)
            .withGeneration(null)
            .withManagedFields((java.util.List<io.fabric8.kubernetes.api.model.ManagedFieldsEntry>) null)
            .endMetadata()
            .withStatus(null)
            .editOrNewSpec()
            .withSuspend(false)
            .endSpec();

        return builder.build();
    }

    private String resolvePhase(Job job, int active, int succeeded, int failed, boolean suspended) {
        if (suspended && (job.getStatus() == null || job.getStatus().getStartTime() == null)) {
            return "SUSPENDED";
        }
        if (active > 0) {
            return "RUNNING";
        }
        List<JobCondition> conditions = job.getStatus() == null ? null : job.getStatus().getConditions();
        if (conditions != null) {
            for (JobCondition condition : conditions) {
                if ("Complete".equalsIgnoreCase(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
                    return "SUCCEEDED";
                }
                if ("Failed".equalsIgnoreCase(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
                    return "FAILED";
                }
            }
        }
        if (succeeded > 0) {
            return "SUCCEEDED";
        }
        if (failed > 0) {
            return "FAILED";
        }
        return suspended ? "SUSPENDED" : "PENDING";
    }

    private ActionResponse action(String namespace, String jobName, String action, String state, String message) {
        return new ActionResponse(namespace, jobName, action, state, message, Instant.now());
    }

    private boolean isBlankReport(JobReportRequest request) {
        boolean hasStatus = request.status() != null && !request.status().isBlank();
        boolean hasMetrics = request.metrics() != null && !request.metrics().isEmpty();
        boolean hasAttributes = request.attributes() != null && !request.attributes().isEmpty();
        return !(hasStatus || hasMetrics || hasAttributes);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private Long computeElapsedSeconds(Instant start, Instant completion) {
        if (start == null) {
            return null;
        }
        Instant end = completion == null ? Instant.now() : completion;
        return Duration.between(start, end).toSeconds();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kubernetes operation", ex);
        }
    }
}
