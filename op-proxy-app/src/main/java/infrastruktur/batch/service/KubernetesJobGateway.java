package infrastruktur.batch.service;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@ApplicationScoped
public class KubernetesJobGateway {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesJobGateway.class);
    
    public static final String TEMPLATE_NAME_LABEL = "batch.template-name";
    public static final String RUN_NAME_LABEL = "batch.run-name";

    private static final List<String> RECREATED_TEMPLATE_LABEL_KEYS_TO_REMOVE = List.of(
        "controller-uid",
        "batch.kubernetes.io/controller-uid",
        "job-name",
        "batch.kubernetes.io/job-name"
    );

    // Conservatively map only clearly non-recoverable startup reasons to FAILED early.
    private static final Set<String> IRRECOVERABLE_WAITING_REASONS = Set.of(
        "ImagePullBackOff",
        "ErrImagePull",
        "InvalidImageName",
        "CreateContainerConfigError",
        "CreateContainerError"
    );

    private final KubernetesClient client;

    @Inject
    public KubernetesJobGateway(KubernetesClient client) {
        this.client = client;
    }

    public Job requireJob(String namespace, String jobName) {
        Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
        if (job == null) {
            throw new NoSuchElementException("Job not found: " + namespace + "/" + jobName);
        }
        return job;
    }

    public void patchSuspend(String namespace, String jobName, boolean suspend) {
        client.batch().v1().jobs().inNamespace(namespace).withName(jobName).edit(j -> new JobBuilder(j)
            .editOrNewSpec()
            .withSuspend(suspend)
            .endSpec()
            .build());
    }

    public void patchStartConfiguration(String namespace, String jobName, Long timeoutSeconds) {
        client.batch().v1().jobs().inNamespace(namespace).withName(jobName).edit(j -> {
            JobBuilder builder = new JobBuilder(j)
                .editOrNewSpec()
                .withSuspend(false)
                .endSpec();

            if (timeoutSeconds != null) {
                builder.editOrNewSpec()
                    .withActiveDeadlineSeconds(timeoutSeconds)
                    .endSpec();
            }
            return builder.build();
        });
    }

    public int deleteActivePods(String namespace, String jobName) {
        var podList = client.pods().inNamespace(namespace).withLabel("job-name", jobName).list();
        List<String> activePodNames = new ArrayList<>();
        for (Pod pod : podList.getItems()) {
            String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
            boolean terminal = "Succeeded".equalsIgnoreCase(phase) || "Failed".equalsIgnoreCase(phase);
            if (!terminal && pod.getMetadata() != null && pod.getMetadata().getName() != null) {
                activePodNames.add(pod.getMetadata().getName());
            }
        }

        for (String podName : activePodNames) {
            client.pods().inNamespace(namespace).withName(podName).delete();
        }
        return activePodNames.size();
    }

    public int deletePods(String namespace, String jobName) {
        var podList = client.pods().inNamespace(namespace).withLabel("job-name", jobName).list();
        List<String> podNames = new ArrayList<>();
        for (Pod pod : podList.getItems()) {
            if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
                podNames.add(pod.getMetadata().getName());
            }
        }

        for (String podName : podNames) {
            client.pods().inNamespace(namespace).withName(podName).delete();
        }
        return podNames.size();
    }

    public boolean hasIrrecoverablePodFailure(String namespace, String jobName) {
        var podList = client.pods().inNamespace(namespace).withLabel("job-name", jobName).list();
        for (Pod pod : podList.getItems()) {
            PodStatus status = pod.getStatus();
            if (status == null) {
                continue;
            }

            if (hasIrrecoverableWaitingReason(status.getContainerStatuses())) {
                return true;
            }

            if (hasIrrecoverableWaitingReason(status.getInitContainerStatuses())) {
                return true;
            }

            if (isIrrecoverableReason(status.getReason()) || containsIrrecoverableReason(status.getMessage())) {
                return true;
            }

            List<PodCondition> conditions = status.getConditions();
            if (conditions != null) {
                for (PodCondition condition : conditions) {
                    String reason = condition == null ? null : condition.getReason();
                    String message = condition == null ? null : condition.getMessage();
                    if (isIrrecoverableReason(reason) || containsIrrecoverableReason(message)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasIrrecoverableWaitingReason(List<ContainerStatus> containerStatuses) {
        if (containerStatuses == null) {
            return false;
        }
        for (ContainerStatus containerStatus : containerStatuses) {
            if (containerStatus == null) {
                continue;
            }

            if (containerStatus.getState() != null && containerStatus.getState().getWaiting() != null) {
                String reason = containerStatus.getState().getWaiting().getReason();
                String message = containerStatus.getState().getWaiting().getMessage();
                if (isIrrecoverableReason(reason) || containsIrrecoverableReason(message)) {
                    return true;
                }
            }

            if (containerStatus.getLastState() != null && containerStatus.getLastState().getWaiting() != null) {
                String reason = containerStatus.getLastState().getWaiting().getReason();
                String message = containerStatus.getLastState().getWaiting().getMessage();
                if (isIrrecoverableReason(reason) || containsIrrecoverableReason(message)) {
                    return true;
                }
            }

        }
        return false;
    }

    private boolean isIrrecoverableReason(String reason) {
        return reason != null && IRRECOVERABLE_WAITING_REASONS.contains(reason);
    }

    private boolean containsIrrecoverableReason(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return IRRECOVERABLE_WAITING_REASONS.stream()
            .map(reason -> reason.toLowerCase(Locale.ROOT))
            .anyMatch(normalized::contains);
    }

    public void deleteJob(String namespace, String jobName) {
        client.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
    }

    public void deleteJobPreservingPods(String namespace, String jobName) {
        client.batch().v1().jobs().inNamespace(namespace).withName(jobName)
            .withPropagationPolicy(DeletionPropagation.ORPHAN)
            .delete();
    }

    public Job createJob(String namespace, Job job) {
        return client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
    }

    public void waitForDeletion(String namespace, String jobName, long pollIntervalMillis, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            Job job = client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
            if (job == null) {
                return;
            }
            sleep(pollIntervalMillis);
        }
        throw new IllegalStateException("Timed out waiting for Job deletion: " + namespace + "/" + jobName);
    }

    private Job loadJobFromTemplate(String namespace, String templateName) {
        Job jobFromTemplate = processOpenShiftTemplate(namespace, templateName);
        if (jobFromTemplate != null) {
            LOG.info("Loaded Job from OpenShift Template: {}/{}", namespace, templateName);
            return jobFromTemplate;
        }

        // Fallback används endast när Template-resursen saknas.
        LOG.debug("Template not found, looking for Job resource instead: {}/{}", namespace, templateName);
        return requireJob(namespace, templateName);
    }

    private Job processOpenShiftTemplate(String namespace, String templateName) {
        OpenShiftClient openShiftClient = adaptToOpenShiftClient();
        var templateResource = openShiftClient.templates().inNamespace(namespace).withName(templateName);
        var template = templateResource.get();
        if (template == null) {
            return null;
        }

        Map<String, String> templateParameters = Map.of("NAMESPACE", namespace);
        KubernetesList processed;
        try {
            processed = templateResource.process(templateParameters);
        } catch (KubernetesClientException ex) {
            if (!shouldFallbackToLocalProcessing(ex)) {
                throw ex;
            }
            LOG.debug("Template server-side processing failed for {}/{} ({}), trying local processing",
                namespace, templateName, ex.getMessage());
            processed = templateResource.processLocally(templateParameters);
        }

        Job job = extractSingleJobFromProcessedList(processed);
        validateJobStructure(job);
        return job;
    }

    private OpenShiftClient adaptToOpenShiftClient() {
        try {
            return client.adapt(OpenShiftClient.class);
        } catch (KubernetesClientException ex) {
            throw new TemplateProcessingException(
                "OpenShift client API is not available from current Kubernetes client: " + ex.getMessage(),
                ex
            );
        }
    }

    private boolean shouldFallbackToLocalProcessing(KubernetesClientException exception) {
        int code = exception.getCode();
        if (code == 404 || code == 405 || code == 501) {
            return true;
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("processedtemplates")
            && (normalized.contains("not found")
                || normalized.contains("method not allowed")
                || normalized.contains("not implemented"));
    }

    private Job extractSingleJobFromProcessedList(KubernetesList processed) {
        if (processed == null || processed.getItems() == null) {
            throw new TemplateProcessingException("Processed template did not return any resources");
        }

        Job foundJob = null;
        for (HasMetadata item : processed.getItems()) {
            if (item instanceof Job job) {
                if (foundJob != null) {
                    throw new TemplateProcessingException("Processed template must contain exactly one Job");
                }
                foundJob = job;
            }
        }

        if (foundJob == null) {
            throw new TemplateProcessingException("Processed template does not contain a Job");
        }

        return foundJob;
    }

    private void validateJobStructure(Job job) {
        if (job == null) {
            throw new TemplateProcessingException("Job object is null");
        }
        if (job.getMetadata() == null || job.getMetadata().getName() == null) {
            throw new TemplateProcessingException("Job must have metadata.name");
        }
        if (job.getSpec() == null) {
            throw new TemplateProcessingException("Job must have spec defined");
        }
        if (job.getSpec().getTemplate() == null || job.getSpec().getTemplate().getSpec() == null) {
            throw new TemplateProcessingException("Job must have spec.template.spec defined");
        }
        if (job.getSpec().getTemplate().getSpec().getContainers() == null 
            || job.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {
            throw new TemplateProcessingException("Job must define at least one container in spec.template.spec.containers");
        }
    }

    public Job createRestartedJob(Job source, Long timeoutSeconds, Map<String, String> parameters) {
        String name = source.getMetadata().getName();
        String namespace = source.getMetadata().getNamespace();
        List<EnvVar> existingEnv = getFirstContainerEnvOrThrow(source);
        Map<String, String> sanitizedTemplateLabels = sanitizeTemplateLabels(source);

        JobBuilder builder = new JobBuilder(source)
            .editOrNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withResourceVersion(null)
            .withUid(null)
            .withCreationTimestamp(null)
            .withGeneration(null)
            .withManagedFields((List<ManagedFieldsEntry>) null)
            .endMetadata()
            .withStatus(null)
            .editOrNewSpec()
            .withSelector(null)
            .withManualSelector(null)
            .withSuspend(false)
            .editOrNewTemplate()
            .editOrNewMetadata()
            .withLabels(sanitizedTemplateLabels)
            .endMetadata()
            .endTemplate()
            .endSpec();

        if (timeoutSeconds != null) {
            builder.editOrNewSpec()
                .withActiveDeadlineSeconds(timeoutSeconds)
                .endSpec();
        }

        applyParametersToFirstContainer(builder, existingEnv, parameters);

        return builder.build();
    }

    public Job createRunFromTemplate(String namespace, String templateName, Long timeoutSeconds, Map<String, String> parameters) {
        Job templateJob = loadJobFromTemplate(namespace, templateName);
        String runName = extractRequiredJobName(templateJob);
        List<EnvVar> existingEnv = getFirstContainerEnvOrThrow(templateJob);
        Map<String, String> runLabels = sanitizeTemplateLabels(templateJob);
        runLabels.put(TEMPLATE_NAME_LABEL, templateName);
        runLabels.put(RUN_NAME_LABEL, runName);

        JobBuilder builder = new JobBuilder(templateJob)
            .editOrNewMetadata()
            .withName(runName)
            .withNamespace(namespace)
            .withResourceVersion(null)
            .withUid(null)
            .withCreationTimestamp(null)
            .withGeneration(null)
            .withManagedFields((List<ManagedFieldsEntry>) null)
            .addToLabels(TEMPLATE_NAME_LABEL, templateName)
            .addToLabels(RUN_NAME_LABEL, runName)
            .endMetadata()
            .withStatus(null)
            .editOrNewSpec()
            .withSelector(null)
            .withManualSelector(null)
            .withSuspend(false)
            .editOrNewTemplate()
            .editOrNewMetadata()
            .withLabels(runLabels)
            .endMetadata()
            .endTemplate()
            .endSpec();

        if (timeoutSeconds != null) {
            builder.editOrNewSpec()
                .withActiveDeadlineSeconds(timeoutSeconds)
                .endSpec();
        }

        applyParametersToFirstContainer(builder, existingEnv, parameters);
        return createJob(namespace, builder.build());
    }

    private String extractRequiredJobName(Job job) {
        String name = job == null || job.getMetadata() == null ? null : job.getMetadata().getName();
        if (name == null || name.isBlank()) {
            throw new TemplateProcessingException("Template Job must define metadata.name");
        }
        return name;
    }

    private Map<String, String> sanitizeTemplateLabels(Job source) {
        Map<String, String> labels = source.getSpec() != null
            && source.getSpec().getTemplate() != null
            && source.getSpec().getTemplate().getMetadata() != null
            && source.getSpec().getTemplate().getMetadata().getLabels() != null
            ? new HashMap<>(source.getSpec().getTemplate().getMetadata().getLabels())
            : new HashMap<>();

        for (String key : RECREATED_TEMPLATE_LABEL_KEYS_TO_REMOVE) {
            labels.remove(key);
        }
        return labels;
    }

    private void applyParametersToFirstContainer(JobBuilder builder, List<EnvVar> existingEnv, Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }

        builder.editOrNewSpec()
            .editOrNewTemplate()
            .editOrNewSpec()
            .editFirstContainer()
            .withEnv(mergeEnvVars(existingEnv, parameters))
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec();
    }

    private List<EnvVar> getFirstContainerEnvOrThrow(Job job) {
        if (job.getSpec() == null
            || job.getSpec().getTemplate() == null
            || job.getSpec().getTemplate().getSpec() == null
            || job.getSpec().getTemplate().getSpec().getContainers() == null
            || job.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {
            throw new TemplateProcessingException("Job must define at least one container in spec.template.spec.containers");
        }
        List<EnvVar> env = job.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        return env != null ? env : new ArrayList<>();
    }

    private List<EnvVar> mergeEnvVars(List<EnvVar> existingEnv, Map<String, String> parameters) {
        Map<String, EnvVar> merged = new LinkedHashMap<>();
        if (existingEnv != null) {
            for (EnvVar envVar : existingEnv) {
                if (envVar != null && envVar.getName() != null) {
                    merged.put(envVar.getName(), envVar);
                }
            }
        }

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            merged.put(entry.getKey(), new EnvVarBuilder().withName(entry.getKey()).withValue(entry.getValue()).build());
        }

        return new ArrayList<>(merged.values());
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
