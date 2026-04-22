package infrastruktur.batch.service;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ManagedFieldsEntry;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

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

    public void deleteJob(String namespace, String jobName) {
        client.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
    }

    public void deleteJobPreservingPods(String namespace, String jobName) {
        client.batch().v1().jobs().inNamespace(namespace).withName(jobName)
            .withPropagationPolicy(DeletionPropagation.ORPHAN)
            .delete();
    }

    public void createJob(String namespace, Job job) {
        client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
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
        // Först, försöka läsa som OpenShift Template-resurs
        try {
            Job jobFromTemplate = processOpenShiftTemplate(namespace, templateName);
            if (jobFromTemplate != null) {
                LOG.info("Loaded Job from OpenShift Template: {}/{}", namespace, templateName);
                return jobFromTemplate;
            }
        } catch (InterruptedException ex) {
            LOG.debug("OpenShift Template processing was interrupted, falling back to direct Job lookup");
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            LOG.debug("Failed to process as OpenShift Template (I/O error), falling back to direct Job lookup: {}", ex.getMessage());
        } catch (IllegalStateException ex) {
            LOG.debug("Failed to process as OpenShift Template (invalid structure), falling back to direct Job lookup: {}", ex.getMessage());
        }

        // Fallback: Läs direkt som Job
        LOG.debug("Looking for Job resource: {}/{}", namespace, templateName);
        return requireJob(namespace, templateName);
    }

    private Job processOpenShiftTemplate(String namespace, String templateName) throws IOException, InterruptedException {
        // Kör: oc process <template-name> -n <namespace> -o yaml
        // Timeout satt till 30 sekunder för att förhindra eternal waiting
        ProcessBuilder pb = new ProcessBuilder(
            "oc", "process", templateName,
            "-n", namespace,
            "-o", "yaml"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        
        // Vänta på process med timeout
        boolean completed = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        if (!completed) {
            process.destroy();
            throw new IOException("oc process timed out after 30 seconds for template: " + namespace + "/" + templateName);
        }

        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.exitValue();

        if (exitCode != 0) {
            LOG.debug("oc process failed with exit code {}: {}", exitCode, output);
            return null;
        }

        // Parse YAML för att hitta Job-objektet - med säker error-hantering
        List<?> resources;
        try {
            resources = Serialization.unmarshal(output, List.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse YAML from oc process output: " + ex.getMessage(), ex);
        }

        if (resources == null) {
            LOG.warn("oc process returned empty YAML for template: {}/{}", namespace, templateName);
            return null;
        }

        for (Object resource : resources) {
            if (resource instanceof Map<?, ?> map) {
                Object kind = map.get("kind");
                if ("Job".equals(kind)) {
                    // Konvertera Map till Job via JSON - med validering
                    try {
                        String json = Serialization.asJson(map);
                        Job job = Serialization.unmarshal(json, Job.class);
                        
                        // Validera att Job-strukturen är välformad
                        validateJobStructure(job);
                        return job;
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to parse Job from Template YAML: " + ex.getMessage(), ex);
                    }
                }
            }
        }

        LOG.warn("No Job found in processed Template output: {}/{}", namespace, templateName);
        return null;
    }

    private void validateJobStructure(Job job) {
        if (job == null) {
            throw new IllegalStateException("Job object is null");
        }
        if (job.getMetadata() == null || job.getMetadata().getName() == null) {
            throw new IllegalStateException("Job must have metadata.name");
        }
        if (job.getSpec() == null) {
            throw new IllegalStateException("Job must have spec defined");
        }
        if (job.getSpec().getTemplate() == null || job.getSpec().getTemplate().getSpec() == null) {
            throw new IllegalStateException("Job must have spec.template.spec defined");
        }
        if (job.getSpec().getTemplate().getSpec().getContainers() == null 
            || job.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {
            throw new IllegalStateException("Job must define at least one container in spec.template.spec.containers");
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

    public void createRunFromTemplate(String namespace, String templateName, String runName, Long timeoutSeconds, Map<String, String> parameters) {
        Job templateJob = loadJobFromTemplate(namespace, templateName);
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
        createJob(namespace, builder.build());
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
            throw new IllegalStateException("Job must define at least one container in spec.template.spec.containers");
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
