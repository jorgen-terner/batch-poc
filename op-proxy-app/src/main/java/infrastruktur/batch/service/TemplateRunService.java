package infrastruktur.batch.service;

import infrastruktur.batch.metrics.JobMetricsReporter;
import infrastruktur.batch.model.CancelRunRequestVO;
import infrastruktur.batch.model.CreateRunRequestVO;
import infrastruktur.batch.model.RunActionResponseVO;
import infrastruktur.batch.model.RunStatusResponseVO;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class TemplateRunService {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateRunService.class);

    private final KubernetesJobGateway kubernetesJobGateway;
    private final JobPhaseResolver jobPhaseResolver;
    private final JobMetricsReporter jobMetricsReporter;

    @Inject
    public TemplateRunService(
        KubernetesJobGateway kubernetesJobGateway,
        JobPhaseResolver jobPhaseResolver,
        JobMetricsReporter jobMetricsReporter
    ) {
        this.kubernetesJobGateway = kubernetesJobGateway;
        this.jobPhaseResolver = jobPhaseResolver;
        this.jobMetricsReporter = jobMetricsReporter;
    }

    public RunActionResponseVO createRun(String namespace, String templateName, CreateRunRequestVO request) {
        validateTemplateName(templateName);

        String clientRequestId = request == null ? null : trimToNull(request.clientRequestId());
        Long timeoutSeconds = request == null ? null : request.timeoutSeconds();
        Map<String, String> parameters = JobHelper.normalizeParameters(request == null ? null : request.parameters());

        JobHelper.validateTimeoutSeconds(timeoutSeconds);
        Job createdRun = kubernetesJobGateway.createRunFromTemplate(namespace, templateName, timeoutSeconds, parameters);
        String runName = createdRun.getMetadata() != null ? createdRun.getMetadata().getName() : null;
        if (runName == null || runName.isBlank()) {
            throw new IllegalStateException("Created run is missing metadata.name");
        }
        LOG.info("Created run {}/{} from template {} (timeoutSeconds={}, parameters={})",
            namespace,
            runName,
            templateName,
            timeoutSeconds,
            parameters.keySet());

        RunActionResponseVO response = new RunActionResponseVO(
            namespace,
            templateName,
            runName,
            clientRequestId,
            "create",
            "PENDING",
            "Run created",
            Instant.now()
        );
        reportRunAction(namespace, templateName, runName, response, Map.of());
        return response;
    }

    public RunStatusResponseVO status(String namespace, String runName) {
        Job runJob = kubernetesJobGateway.requireJob(namespace, runName);
        JobStatus status = runJob.getStatus();

        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        boolean suspended = runJob.getSpec() != null && Boolean.TRUE.equals(runJob.getSpec().getSuspend());
        boolean irrecoverablePodFailure = kubernetesJobGateway.hasIrrecoverablePodFailure(namespace, runName);

        Instant startTime = JobHelper.parseInstant(status == null ? null : status.getStartTime());
        Instant completionTime = JobHelper.parseInstant(status == null ? null : status.getCompletionTime());
        Long elapsed = JobHelper.computeElapsedSeconds(startTime, completionTime);

        String phase = jobPhaseResolver.resolvePhase(runJob, active, succeeded, failed, suspended);
        if (("RUNNING".equalsIgnoreCase(phase) || "PENDING".equalsIgnoreCase(phase)) && irrecoverablePodFailure) {
            phase = "FAILED";
        }
        return new RunStatusResponseVO(
            namespace,
            resolveTemplateName(runJob),
            runName,
            phase,
            active,
            succeeded,
            failed,
            startTime,
            completionTime,
            elapsed
        );
    }

    public RunActionResponseVO cancel(String namespace, String runName, CancelRunRequestVO request) {
        Job runJob = kubernetesJobGateway.requireJob(namespace, runName);
        boolean deletePods = request != null && Boolean.TRUE.equals(request.deletePods());

        if (deletePods) {
            int deletedPods = kubernetesJobGateway.deletePods(namespace, runName);
            kubernetesJobGateway.deleteJob(namespace, runName);
            LOG.info("Cancelled run {}/{} and deleted {} pod(s)", namespace, runName, deletedPods);
            RunActionResponseVO response = action(namespace, resolveTemplateName(runJob), runName, "cancel", "CANCELLED", "Run cancelled and pods deleted");
            reportRunAction(namespace, response.templateName(), runName, response, Map.of("deletedPods", (double) deletedPods));
            return response;
        }

        kubernetesJobGateway.deleteJobPreservingPods(namespace, runName);
        LOG.info("Cancelled run {}/{} and preserved pods", namespace, runName);
        RunActionResponseVO response = action(namespace, resolveTemplateName(runJob), runName, "cancel", "CANCELLED", "Run cancelled and pods preserved");
        reportRunAction(namespace, response.templateName(), runName, response, Map.of());
        return response;
    }

    private RunActionResponseVO action(
        String namespace,
        String templateName,
        String runName,
        String action,
        String state,
        String message
    ) {
        return new RunActionResponseVO(
            namespace,
            templateName,
            runName,
            null,
            action,
            state,
            message,
            Instant.now()
        );
    }

    private void reportRunAction(
        String namespace,
        String templateName,
        String runName,
        RunActionResponseVO response,
        Map<String, Double> metrics
    ) {
        jobMetricsReporter.report(
            namespace,
            "RUN",
            runName,
            response.state(),
            metrics,
            Map.of(
                "action", response.action(),
                "templateName", templateName
            )
        );
    }

    private String resolveTemplateName(Job runJob) {
        var meta = runJob.getMetadata();
        if (meta == null || meta.getLabels() == null) {
            LOG.warn("Run {} is missing metadata or labels — cannot resolve template name",
                meta != null ? meta.getName() : "<unknown>");
            return "UNKNOWN";
        }
        String labelValue = meta.getLabels().get(KubernetesJobGateway.TEMPLATE_NAME_LABEL);
        if (labelValue == null || labelValue.isBlank()) {
            LOG.warn("Run {} is missing label '{}' — cannot resolve template name",
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
