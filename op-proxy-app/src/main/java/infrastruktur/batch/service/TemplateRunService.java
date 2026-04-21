package infrastruktur.batch.service;

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
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class TemplateRunService {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateRunService.class);
    private static final int MAX_RUN_NAME_LENGTH = 63;

    private final KubernetesJobGateway kubernetesJobGateway;
    private final JobPhaseResolver jobPhaseResolver;

    @Inject
    public TemplateRunService(
        KubernetesJobGateway kubernetesJobGateway,
        JobPhaseResolver jobPhaseResolver
    ) {
        this.kubernetesJobGateway = kubernetesJobGateway;
        this.jobPhaseResolver = jobPhaseResolver;
    }

    public RunActionResponseVO createRun(String namespace, String templateName, CreateRunRequestVO request) {
        validateTemplateName(templateName);

        String clientRequestId = request == null ? null : trimToNull(request.clientRequestId());
        Long timeoutSeconds = request == null ? null : request.timeoutSeconds();
        Map<String, String> parameters = JobHelper.normalizeParameters(request == null ? null : request.parameters());

        JobHelper.validateTimeoutSeconds(timeoutSeconds);
        String runName = generateRunName(templateName);

        kubernetesJobGateway.createRunFromTemplate(namespace, templateName, runName, timeoutSeconds, parameters);
        LOG.info("Created run {}/{} from template {} (timeoutSeconds={}, parameters={})",
            namespace,
            runName,
            templateName,
            timeoutSeconds,
            parameters.keySet());

        return new RunActionResponseVO(
            namespace,
            templateName,
            runName,
            clientRequestId,
            "create",
            "PENDING",
            "Run created",
            Instant.now()
        );
    }

    public RunStatusResponseVO status(String namespace, String runName) {
        Job runJob = kubernetesJobGateway.requireJob(namespace, runName);
        JobStatus status = runJob.getStatus();

        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        boolean suspended = runJob.getSpec() != null && Boolean.TRUE.equals(runJob.getSpec().getSuspend());

        Instant startTime = JobHelper.parseInstant(status == null ? null : status.getStartTime());
        Instant completionTime = JobHelper.parseInstant(status == null ? null : status.getCompletionTime());
        Long elapsed = JobHelper.computeElapsedSeconds(startTime, completionTime);

        String phase = jobPhaseResolver.resolvePhase(runJob, active, succeeded, failed, suspended);
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
            return action(namespace, resolveTemplateName(runJob), runName, "cancel", "CANCELLED", "Run cancelled and pods deleted");
        }

        kubernetesJobGateway.deleteJobPreservingPods(namespace, runName);
        LOG.info("Cancelled run {}/{} and preserved pods", namespace, runName);
        return action(namespace, resolveTemplateName(runJob), runName, "cancel", "CANCELLED", "Run cancelled and pods preserved");
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

    private String generateRunName(String templateName) {
        String suffix = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now())
            + "-" + UUID.randomUUID().toString().substring(0, 6);
        return normalizeRunName(templateName + "-" + suffix);
    }

    private void validateTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("templateName must be provided");
        }
    }

    private String normalizeRunName(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("runName must be provided");
        }
        normalized = normalized.replaceAll("[^a-z0-9-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = trimHyphen(normalized);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("runName must contain at least one valid character");
        }
        if (normalized.length() > MAX_RUN_NAME_LENGTH) {
            normalized = trimHyphen(normalized.substring(0, MAX_RUN_NAME_LENGTH));
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("runName became invalid after normalization");
        }
        return normalized;
    }

    private String trimHyphen(String value) {
        String trimmed = value;
        while (trimmed.startsWith("-")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("-")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
