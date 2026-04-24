package infrastruktur.batch.service;

import infrastruktur.batch.metrics.JobMetricsReporter;
import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.model.StartExecutionRequestVO;
import infrastruktur.batch.model.StopExecutionRequestVO;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class TemplateExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateExecutionService.class);
    private static final long DEFAULT_STOP_GRACEFUL_POLL_INTERVAL_MILLIS = 1000L;
    private static final int DEFAULT_STOP_GRACEFUL_MAX_ATTEMPTS = 20;

    private final KubernetesJobGateway kubernetesJobGateway;
    private final JobPhaseResolver jobPhaseResolver;
    private final JobMetricsReporter jobMetricsReporter;
    private final long stopGracefulPollIntervalMillis;
    private final int stopGracefulMaxAttempts;

    @Inject
    public TemplateExecutionService(
        KubernetesJobGateway kubernetesJobGateway,
        JobPhaseResolver jobPhaseResolver,
        JobMetricsReporter jobMetricsReporter,
        @ConfigProperty(name = "batch.execution.stop.graceful.poll-interval-millis", defaultValue = "1000") long stopGracefulPollIntervalMillis,
        @ConfigProperty(name = "batch.execution.stop.graceful.max-attempts", defaultValue = "20") int stopGracefulMaxAttempts
    ) {
        this.kubernetesJobGateway = kubernetesJobGateway;
        this.jobPhaseResolver = jobPhaseResolver;
        this.jobMetricsReporter = jobMetricsReporter;
        if (stopGracefulPollIntervalMillis < 1) {
            throw new IllegalArgumentException("batch.execution.stop.graceful.poll-interval-millis must be >= 1");
        }
        if (stopGracefulMaxAttempts < 1) {
            throw new IllegalArgumentException("batch.execution.stop.graceful.max-attempts must be >= 1");
        }
        this.stopGracefulPollIntervalMillis = stopGracefulPollIntervalMillis;
        this.stopGracefulMaxAttempts = stopGracefulMaxAttempts;
    }

    public TemplateExecutionService(
        KubernetesJobGateway kubernetesJobGateway,
        JobPhaseResolver jobPhaseResolver,
        JobMetricsReporter jobMetricsReporter
    ) {
        this(
            kubernetesJobGateway,
            jobPhaseResolver,
            jobMetricsReporter,
            DEFAULT_STOP_GRACEFUL_POLL_INTERVAL_MILLIS,
            DEFAULT_STOP_GRACEFUL_MAX_ATTEMPTS
        );
    }

    public ExecutionActionResponseVO start(String namespace, String templateName, StartExecutionRequestVO request) {
        validateTemplateName(templateName);

        String clientRequestId = request == null ? null : trimToNull(request.clientRequestId());
        Long timeoutSeconds = request == null ? null : request.timeoutSeconds();
        Map<String, String> parameters = JobHelper.normalizeParameters(request == null ? null : request.parameters());

        JobHelper.validateTimeoutSeconds(timeoutSeconds);
        Job createdExecution = kubernetesJobGateway.createExecutionFromTemplate(namespace, templateName, timeoutSeconds, parameters);
        String executionName = createdExecution.getMetadata() != null ? createdExecution.getMetadata().getName() : null;
        if (executionName == null || executionName.isBlank()) {
            throw new IllegalStateException("Created execution is missing metadata.name");
        }
        LOG.info("Started execution {}/{} from template {} (timeoutSeconds={}, parameters={})",
            namespace,
            executionName,
            templateName,
            timeoutSeconds,
            parameters.keySet());

        ExecutionActionResponseVO response = new ExecutionActionResponseVO(
            namespace,
            templateName,
            executionName,
            clientRequestId,
            "start",
            "PENDING",
            "Execution started",
            Instant.now()
        );
        reportExecutionAction(namespace, templateName, executionName, response, Map.of());
        return response;
    }

    public ExecutionStatusResponseVO status(String namespace, String executionName) {
        Job executionJob = kubernetesJobGateway.requireJob(namespace, executionName);
        JobStatus status = executionJob.getStatus();

        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        boolean suspended = executionJob.getSpec() != null && Boolean.TRUE.equals(executionJob.getSpec().getSuspend());
        boolean irrecoverablePodFailure = kubernetesJobGateway.hasIrrecoverablePodFailure(namespace, executionName);

        Instant startTime = JobHelper.parseInstant(status == null ? null : status.getStartTime());
        Instant completionTime = JobHelper.parseInstant(status == null ? null : status.getCompletionTime());
        Long elapsed = JobHelper.computeElapsedSeconds(startTime, completionTime);

        String phase = jobPhaseResolver.resolvePhase(executionJob, active, succeeded, failed, suspended);
        if (("RUNNING".equalsIgnoreCase(phase) || "PENDING".equalsIgnoreCase(phase)) && irrecoverablePodFailure) {
            phase = "FAILED";
        }
        return new ExecutionStatusResponseVO(
            namespace,
            resolveTemplateName(executionJob),
            executionName,
            phase,
            active,
            succeeded,
            failed,
            startTime,
            completionTime,
            elapsed
        );
    }

    public ExecutionActionResponseVO stop(String namespace, String executionName, StopExecutionRequestVO request) {
        Job executionJob = kubernetesJobGateway.requireJob(namespace, executionName);
        boolean deletePods = request != null && Boolean.TRUE.equals(request.deletePods());
        String templateName = resolveTemplateName(executionJob);

        // Best-effort stop signal before stopping execution.
        try {
            kubernetesJobGateway.patchSuspend(namespace, executionName, true);
        } catch (KubernetesClientException ex) {
            LOG.warn("Could not suspend execution {}/{} before stop: {}", namespace, executionName, ex.getMessage());
        }

        int remainingActivePods = kubernetesJobGateway.waitForActivePodsToStop(
            namespace,
            executionName,
            stopGracefulPollIntervalMillis,
            stopGracefulMaxAttempts
        );

        int deletedActivePods = 0;
        if (deletePods && remainingActivePods > 0) {
            deletedActivePods = kubernetesJobGateway.deleteActivePods(namespace, executionName);
            LOG.warn(
                "Graceful stop timeout for execution {}/{} ({} active pod(s) remaining). Forced deletion removed {} active pod(s)",
                namespace,
                executionName,
                remainingActivePods,
                deletedActivePods
            );
        }

        if (deletePods) {
            int deletedPods = kubernetesJobGateway.deletePods(namespace, executionName);
            kubernetesJobGateway.deleteJob(namespace, executionName);
            LOG.info(
                "Stopped execution {}/{} (graceful stop), deleted {} pod(s) in total ({} force-deleted active)",
                namespace,
                executionName,
                deletedPods,
                deletedActivePods
            );
            ExecutionActionResponseVO response = action(
                namespace,
                templateName,
                executionName,
                "stop",
                "STOPPED",
                "Execution stopped (graceful stop) and pods deleted"
            );
            reportExecutionAction(
                namespace,
                response.templateName(),
                executionName,
                response,
                Map.of(
                    "deletedPods", (double) deletedPods,
                    "deletedActivePods", (double) deletedActivePods
                )
            );
            return response;
        }

        kubernetesJobGateway.deleteJobPreservingPods(namespace, executionName);
        LOG.info(
            "Stopped execution {}/{} (graceful stop), preserved active and terminal pods",
            namespace,
            executionName
        );
        ExecutionActionResponseVO response = action(
            namespace,
            templateName,
            executionName,
            "stop",
            "STOPPED",
            "Execution stopped (graceful stop), active and terminal pods preserved"
        );
        reportExecutionAction(namespace, response.templateName(), executionName, response, Map.of("deletedActivePods", (double) deletedActivePods));
        return response;
    }

    private ExecutionActionResponseVO action(
        String namespace,
        String templateName,
        String executionName,
        String action,
        String state,
        String message
    ) {
        return new ExecutionActionResponseVO(
            namespace,
            templateName,
            executionName,
            null,
            action,
            state,
            message,
            Instant.now()
        );
    }

    private void reportExecutionAction(
        String namespace,
        String templateName,
        String executionName,
        ExecutionActionResponseVO response,
        Map<String, Double> metrics
    ) {
        jobMetricsReporter.report(
            namespace,
            "EXECUTION",
            executionName,
            response.state(),
            metrics,
            Map.of(
                "action", response.action(),
                "templateName", templateName
            )
        );
    }

    private String resolveTemplateName(Job executionJob) {
        var meta = executionJob.getMetadata();
        if (meta == null || meta.getLabels() == null) {
            LOG.warn("Execution {} is missing metadata or labels - cannot resolve template name",
                meta != null ? meta.getName() : "<unknown>");
            return "UNKNOWN";
        }
        String labelValue = meta.getLabels().get(KubernetesJobGateway.TEMPLATE_NAME_LABEL);
        if (labelValue == null || labelValue.isBlank()) {
            LOG.warn("Execution {} is missing label '{}' - cannot resolve template name",
                meta.getName(), KubernetesJobGateway.TEMPLATE_NAME_LABEL);
            return "UNKNOWN";
        }
        return labelValue;
    }

    private void validateTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("templateName must be provided");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
