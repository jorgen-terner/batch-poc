package com.batchjobapp.service;

import com.batchjobapp.model.ActionResponse;
import com.batchjobapp.model.JobMetricsResponse;
import com.batchjobapp.model.JobReportRequest;
import com.batchjobapp.model.JobReportSnapshot;
import com.batchjobapp.model.JobStatusResponse;
import com.batchjobapp.store.JobReportStore;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
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
import java.util.Optional;

@ApplicationScoped
public class JobControlService {
    private static final Logger LOG = LoggerFactory.getLogger(JobControlService.class);

    private final KubernetesClient client;
    private final JobReportStore reportStore;
    private final KubernetesJobGateway kubernetesJobGateway;
    private final JobCompletionWatcher jobCompletionWatcher;
    private final JobPhaseResolver jobPhaseResolver;
    private final JobMetricsCollector jobMetricsCollector;

    @Inject
    public JobControlService(
        KubernetesClient client,
        JobReportStore reportStore,
        KubernetesJobGateway kubernetesJobGateway,
        JobCompletionWatcher jobCompletionWatcher,
        JobPhaseResolver jobPhaseResolver,
        JobMetricsCollector jobMetricsCollector
    ) {
        this.client = client;
        this.reportStore = reportStore;
        this.kubernetesJobGateway = kubernetesJobGateway;
        this.jobCompletionWatcher = jobCompletionWatcher;
        this.jobPhaseResolver = jobPhaseResolver;
        this.jobMetricsCollector = jobMetricsCollector;
    }

    public JobControlService(KubernetesClient client, JobReportStore reportStore) {
        this(
            client,
            reportStore,
            new KubernetesJobGateway(client),
            new JobCompletionWatcher(client, new JobPhaseResolver()),
            new JobPhaseResolver(),
            new JobMetricsCollector()
        );
    }

    public ActionResponse start(String namespace, String jobName) {
        kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, false);

        LOG.info("Started job {}/{} by unsuspending", namespace, jobName);
        return action(namespace, jobName, "start", "RUNNING", "Job unsuspended");
    }

    public JobStatusResponse startAndWait(String namespace, String jobName, long intervalSeconds, Long timeoutSeconds) {
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("intervalSeconds must be >= 1");
        }
        if (timeoutSeconds != null && timeoutSeconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 1 when provided");
        }

        start(namespace, jobName);

        JobStatusResponse immediateStatus = status(namespace, jobName);
        if (jobPhaseResolver.isTerminalPhase(immediateStatus.phase())) {
            return immediateStatus;
        }

        long resyncMillis = intervalSeconds * 1000;
        jobCompletionWatcher.awaitCompletion(namespace, jobName, resyncMillis, timeoutSeconds);
        return status(namespace, jobName);
    }

    public ActionResponse stop(String namespace, String jobName) {
        kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, true);
        int deletedPods = kubernetesJobGateway.deletePods(namespace, jobName);

        LOG.info("Stopped job {}/{} and deleted {} pod(s)", namespace, jobName, deletedPods);
        return action(namespace, jobName, "stop", "SUSPENDED", "Job suspended and active pods deleted: " + deletedPods);
    }

    public ActionResponse restart(String namespace, String jobName) {
        Job existing = kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, true);
        int deletedPods = kubernetesJobGateway.deletePods(namespace, jobName);

        kubernetesJobGateway.deleteJob(namespace, jobName);
        kubernetesJobGateway.waitForDeletion(namespace, jobName, 1000, 30);

        Job recreated = kubernetesJobGateway.createRestartedJob(existing);
        kubernetesJobGateway.createJob(namespace, recreated);

        LOG.info("Restarted job {}/{} by delete/recreate. Deleted {} pod(s)", namespace, jobName, deletedPods);
        return action(namespace, jobName, "restart", "RUNNING", "Job recreated and started");
    }

    public JobStatusResponse status(String namespace, String jobName) {
        Job job = kubernetesJobGateway.requireJob(namespace, jobName);
        JobStatus status = job.getStatus();

        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        boolean suspended = job.getSpec() != null && Boolean.TRUE.equals(job.getSpec().getSuspend());

        Instant startTime = parseInstant(status == null ? null : status.getStartTime());
        Instant completionTime = parseInstant(status == null ? null : status.getCompletionTime());
        Long elapsed = computeElapsedSeconds(startTime, completionTime);

        String phase = jobPhaseResolver.resolvePhase(job, active, succeeded, failed, suspended);
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
        Job job = kubernetesJobGateway.requireJob(namespace, jobName);
        PodList podList = client.pods().inNamespace(namespace).withLabel("job-name", jobName).list();
        List<Pod> pods = podList.getItems();
        JobMetricsCollector.PodMetricsSummary podMetrics = jobMetricsCollector.collect(pods);

        JobStatus jobStatus = job.getStatus();
        Instant startTime = parseInstant(jobStatus == null ? null : jobStatus.getStartTime());
        Instant completionTime = parseInstant(jobStatus == null ? null : jobStatus.getCompletionTime());
        Long elapsed = computeElapsedSeconds(startTime, completionTime);

        Optional<JobReportSnapshot> report = reportStore.get(namespace, jobName);
        Map<String, String> fallbackAttributes = new LinkedHashMap<>();
        if (podMetrics.lastTerminationReason() != null) {
            fallbackAttributes.put("terminationReason", podMetrics.lastTerminationReason());
        }
        if (podMetrics.lastTerminationMessage() != null) {
            fallbackAttributes.put("terminationMessage", podMetrics.lastTerminationMessage());
        }

        return new JobMetricsResponse(
            namespace,
            jobName,
            pods.size(),
            podMetrics.totalRestarts(),
            podMetrics.lastExitCode(),
            startTime,
            completionTime,
            elapsed,
            report.map(JobReportSnapshot::status).orElse(null),
            report.map(JobReportSnapshot::metrics).orElse(null),
            report.map(JobReportSnapshot::attributes).orElse(fallbackAttributes.isEmpty() ? null : Map.copyOf(fallbackAttributes))
        );
    }

    public ActionResponse report(String namespace, String jobName, JobReportRequest request) {
        kubernetesJobGateway.requireJob(namespace, jobName);

        if (request == null || isBlankReport(request)) {
            LOG.info("Accepted empty report for job {}/{}", namespace, jobName);
            return action(namespace, jobName, "report", "REPORTED", "Empty report accepted");
        }

        reportStore.put(namespace, jobName, request);

        LOG.info("Stored report for job {}/{} with status {}", namespace, jobName, request.status());
        return action(namespace, jobName, "report", "REPORTED", "Report accepted");
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

}
