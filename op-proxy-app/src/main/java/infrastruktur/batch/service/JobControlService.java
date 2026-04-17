package infrastruktur.batch.service;

import infrastruktur.batch.model.ActionResponse;
import infrastruktur.batch.model.JobParameterVO;
import infrastruktur.batch.model.JobMetricsResponse;
import infrastruktur.batch.model.JobReportRequest;
import infrastruktur.batch.model.JobReportSnapshot;
import infrastruktur.batch.model.JobStatusResponse;
import infrastruktur.batch.model.RestartJobRequestVO;
import infrastruktur.batch.model.StartJobRequestVO;
import infrastruktur.batch.metrics.JobMetricsReporter;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class JobControlService {
    private static final Logger LOG = LoggerFactory.getLogger(JobControlService.class);
    private static final long DEFAULT_DELETION_POLL_INTERVAL_MILLIS = 1000L;
    private static final int DEFAULT_DELETION_MAX_ATTEMPTS = 30;

    private final KubernetesClient client;
    private final JobMetricsReporter jobMetricsReporter;
    private final KubernetesJobGateway kubernetesJobGateway;
    private final JobPhaseResolver jobPhaseResolver;
    private final JobMetricsCollector jobMetricsCollector;
    private final long deletionPollIntervalMillis;
    private final int deletionMaxAttempts;

    @Inject
    public JobControlService(
        KubernetesClient client,
        JobMetricsReporter jobMetricsReporter,
        KubernetesJobGateway kubernetesJobGateway,
        JobPhaseResolver jobPhaseResolver,
        JobMetricsCollector jobMetricsCollector,
        @ConfigProperty(name = "batch.job.deletion.poll-interval-millis", defaultValue = "1000") long deletionPollIntervalMillis,
        @ConfigProperty(name = "batch.job.deletion.max-attempts", defaultValue = "30") int deletionMaxAttempts
    ) {
        this.client = client;
        this.jobMetricsReporter = jobMetricsReporter;
        this.kubernetesJobGateway = kubernetesJobGateway;
        this.jobPhaseResolver = jobPhaseResolver;
        this.jobMetricsCollector = jobMetricsCollector;
        if (deletionPollIntervalMillis < 1) {
            throw new IllegalArgumentException("batch.job.deletion.poll-interval-millis must be >= 1");
        }
        if (deletionMaxAttempts < 1) {
            throw new IllegalArgumentException("batch.job.deletion.max-attempts must be >= 1");
        }
        this.deletionPollIntervalMillis = deletionPollIntervalMillis;
        this.deletionMaxAttempts = deletionMaxAttempts;
    }

    public JobControlService(KubernetesClient client) {
        this(
            client,
            (namespace, jobName, snapshot) -> {
            },
            new KubernetesJobGateway(client),
            new JobPhaseResolver(),
            new JobMetricsCollector(),
            DEFAULT_DELETION_POLL_INTERVAL_MILLIS,
            DEFAULT_DELETION_MAX_ATTEMPTS
        );
    }

    public ActionResponse start(String namespace, String jobName, StartJobRequestVO request) {
        Long timeoutSeconds = request == null ? null : request.timeoutSeconds();
        Map<String, String> parameters = normalizeParameters(request == null ? null : request.parameters());

        validateTimeoutSeconds(timeoutSeconds);
        if (parameters.isEmpty()) {
            kubernetesJobGateway.requireJob(namespace, jobName);
            kubernetesJobGateway.patchStartConfiguration(namespace, jobName, timeoutSeconds);
            LOG.info("Started job {}/{} by unsuspending (timeoutSeconds={})", namespace, jobName, timeoutSeconds);
            return action(namespace, jobName, "start", "RUNNING", "Job unsuspended");
        }

        Job existing = kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, true);
        int deletedPods = kubernetesJobGateway.deleteActivePods(namespace, jobName);
        kubernetesJobGateway.deleteJobPreservingPods(namespace, jobName);
        kubernetesJobGateway.waitForDeletion(namespace, jobName, deletionPollIntervalMillis, deletionMaxAttempts);

        Job recreated = kubernetesJobGateway.createRestartedJob(existing, timeoutSeconds, parameters);
        kubernetesJobGateway.createJob(namespace, recreated);

        LOG.info(
            "Started job {}/{} by recreate (deletedActivePods={}, timeoutSeconds={}, parameters={})",
            namespace,
            jobName,
            deletedPods,
            timeoutSeconds,
            parameters.keySet()
        );
        return action(namespace, jobName, "start", "RUNNING", "Job recreated and started");
    }

    public ActionResponse stop(String namespace, String jobName) {
        kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, true);
        int deletedPods = kubernetesJobGateway.deleteActivePods(namespace, jobName);

        LOG.info("Stopped job {}/{} and deleted {} active pod(s)", namespace, jobName, deletedPods);
        return action(namespace, jobName, "stop", "SUSPENDED", "Job suspended and active pods deleted: " + deletedPods);
    }

    public ActionResponse restart(String namespace, String jobName, RestartJobRequestVO request) {
        Long timeoutSeconds = request == null ? null : request.timeoutSeconds();
        boolean keepFailedPods = request == null || request.keepFailedPods() == null || request.keepFailedPods();
        Map<String, String> parameters = normalizeParameters(request == null ? null : request.parameters());

        validateTimeoutSeconds(timeoutSeconds);
        Job existing = kubernetesJobGateway.requireJob(namespace, jobName);
        kubernetesJobGateway.patchSuspend(namespace, jobName, true);
        int deletedPods = keepFailedPods
            ? kubernetesJobGateway.deleteActivePods(namespace, jobName)
            : kubernetesJobGateway.deletePods(namespace, jobName);

        if (keepFailedPods) {
            kubernetesJobGateway.deleteJobPreservingPods(namespace, jobName);
        } else {
            kubernetesJobGateway.deleteJob(namespace, jobName);
        }
        kubernetesJobGateway.waitForDeletion(namespace, jobName, deletionPollIntervalMillis, deletionMaxAttempts);

        Job recreated = kubernetesJobGateway.createRestartedJob(existing, timeoutSeconds, parameters);
        kubernetesJobGateway.createJob(namespace, recreated);

        LOG.info(
            "Restarted job {}/{} by delete/recreate. Deleted {} pod(s), timeoutSeconds={}, keepFailedPods={}, parameters={}",
            namespace,
            jobName,
            deletedPods,
            timeoutSeconds,
            keepFailedPods,
            parameters.keySet()
        );
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
            elapsed
        );
    }

    public JobMetricsResponse metrics(String namespace, String jobName) {
        Job job = kubernetesJobGateway.requireJob(namespace, jobName);
        PodList podList = client.pods().inNamespace(namespace).withLabel("job-name", jobName).list();
        List<Pod> pods = filterPodsForCurrentJob(job, podList.getItems());
        JobMetricsCollector.PodMetricsSummary podMetrics = jobMetricsCollector.collect(pods);

        String phase = jobPhaseResolver.resolvePhase(job);

        JobStatus jobStatus = job.getStatus();
        Instant startTime = parseInstant(jobStatus == null ? null : jobStatus.getStartTime());
        Instant completionTime = parseInstant(jobStatus == null ? null : jobStatus.getCompletionTime());
        Long elapsed = computeElapsedSeconds(startTime, completionTime);

        return new JobMetricsResponse(
            namespace,
            jobName,
            phase,
            pods.size(),
            podMetrics.totalRestarts(),
            podMetrics.lastExitCode(),
            startTime,
            completionTime,
            elapsed
        );
    }

    public ActionResponse report(String namespace, String jobName, JobReportRequest request) {
        kubernetesJobGateway.requireJob(namespace, jobName);

        if (request == null || isBlankReport(request)) {
            LOG.info("Accepted empty report for job {}/{}", namespace, jobName);
            return action(namespace, jobName, "report", "REPORTED", "Empty report accepted");
        }

        JobReportSnapshot snapshot = new JobReportSnapshot(
            Instant.now(),
            request.status() == null || request.status().isBlank() ? "UNKNOWN" : request.status().trim().toUpperCase(),
            request.metrics() == null ? Map.of() : Map.copyOf(request.metrics()),
            request.attributes() == null ? Map.of() : Map.copyOf(request.attributes())
        );
        jobMetricsReporter.report(namespace, jobName, snapshot);

        LOG.info("Forwarded report for job {}/{} with status {}", namespace, jobName, request.status());
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
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            LOG.warn("Failed to parse timestamp from Kubernetes status: {}", value);
            return null;
        }
    }

    private Long computeElapsedSeconds(Instant start, Instant completion) {
        if (start == null) {
            return null;
        }
        Instant end = completion == null ? Instant.now() : completion;
        return Duration.between(start, end).toSeconds();
    }

    private List<Pod> filterPodsForCurrentJob(Job job, List<Pod> pods) {
        String currentJobUid = job.getMetadata() == null ? null : job.getMetadata().getUid();
        if (currentJobUid == null || currentJobUid.isBlank()) {
            return pods;
        }

        List<Pod> filteredPods = new ArrayList<>();
        for (Pod pod : pods) {
            if (pod.getMetadata() == null || pod.getMetadata().getOwnerReferences() == null) {
                continue;
            }
            boolean belongsToCurrentJob = pod.getMetadata().getOwnerReferences().stream()
                .anyMatch(ref -> "Job".equals(ref.getKind()) && currentJobUid.equals(ref.getUid()));
            if (belongsToCurrentJob) {
                filteredPods.add(pod);
            }
        }
        return filteredPods;
    }

    private void validateTimeoutSeconds(Long timeoutSeconds) {
        if (timeoutSeconds != null && timeoutSeconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds must be >= 1 when provided");
        }
    }

    private Map<String, String> normalizeParameters(List<JobParameterVO> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> duplicateGuard = new java.util.HashSet<>();
        for (int i = 0; i < parameters.size(); i++) {
            JobParameterVO parameter = parameters.get(i);
            if (parameter == null) {
                throw new IllegalArgumentException("parameters[" + i + "] must not be null");
            }

            String rawName = parameter.name();
            String rawValue = parameter.value();
            if (rawName == null || rawName.isBlank()) {
                throw new IllegalArgumentException("parameters[" + i + "].name must not be blank");
            }
            if (rawValue == null) {
                throw new IllegalArgumentException("parameters[" + i + "].value must not be null");
            }

            String name = rawName.trim();
            if (!duplicateGuard.add(name)) {
                throw new IllegalArgumentException("Duplicate parameter name: " + name);
            }

            normalized.put(name, rawValue);
        }
        return Map.copyOf(normalized);
    }

}
