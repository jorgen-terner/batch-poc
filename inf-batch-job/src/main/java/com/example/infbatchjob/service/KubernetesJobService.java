package com.example.infbatchjob.service;

import com.example.infbatchjob.dto.JobStartRequest;
import com.example.infbatchjob.dto.JobStatusResponse;
import com.example.infbatchjob.exception.JobException;
import com.example.infbatchjob.service.monitoring.BatchMetricsContext;
import com.example.infbatchjob.service.monitoring.BatchMetricsReporter;
import com.example.infbatchjob.service.monitoring.MetricsReporterFactory;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@ApplicationScoped
public class KubernetesJobService {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesJobService.class);

    @ConfigProperty(name = "kubernetes.namespace", defaultValue = "default")
    String namespace;

    @ConfigProperty(name = "kubernetes.job-ttl-seconds", defaultValue = "86400")
    int defaultTtlSeconds;

    @Inject
    BatchV1Api batchV1Api;

    @Inject
    CoreV1Api coreV1Api;

    @Inject
    ConfigMapService configMapService;

    @Inject
    MetricsReporterFactory metricsReporterFactory;

    private final ExecutorService monitorExecutor = Executors.newCachedThreadPool();
    private final Set<String> terminalMetricsSent = ConcurrentHashMap.newKeySet();

    /**
     * Starta ett nytt Kubernetes Job
     */
    public String startJob(JobStartRequest request) {
        try {
            Map<String, String> configMapData = configMapService.getConfigMapData(request.getConfigMapName());
            String batchType = configMapData.getOrDefault("BATCH_TYP", "");
            BatchMetricsReporter reporter = metricsReporterFactory.forBatchType(batchType);

            V1Job job = buildJob(request, configMapData);
            V1Job createdJob = batchV1Api.createNamespacedJob(namespace, job, null, null, null, null);

            String createdJobName = createdJob.getMetadata().getName();
            BatchMetricsContext metricsContext = createMetricsContext(createdJobName, createdJobName, configMapData, Instant.now());
            reporter.onStart(metricsContext);

            if (metricsReporterFactory.shouldMonitorAsync(batchType)) {
                monitorJobAsync(createdJobName, reporter, metricsContext);
            }

            return createdJobName;
        } catch (ApiException e) {
            throw new JobException("Misslyckades att starta Job: " + e.getMessage(), e);
        }
    }

    /**
     * Konstruera ett Kubernetes Job-objekt från ConfigMap + request overrides
     */
    private V1Job buildJob(JobStartRequest request, Map<String, String> configMapData) {
        LOG.info("Building Job from ConfigMap: {}", request.getConfigMapName());

        // Extrahera Job-konfiguration från ConfigMap (med möjlighet till override från request)
        JobConfig jobConfig = extractJobConfig(configMapData, request);

        // Generera Job-namn
        String jobName = generateJobName(jobConfig.baseName);
        LOG.info("Generated Job name: {}", jobName);

        // Bygg Kubernetes Job
        V1Job job = new V1Job();
        job.setApiVersion("batch/v1");
        job.setKind("Job");

        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(jobName);
        metadata.setNamespace(namespace);
        metadata.putLabelsItem("configMap", request.getConfigMapName());

        String batchType = configMapData.getOrDefault("BATCH_TYP", "").trim();
        if (!batchType.isEmpty()) {
            metadata.putLabelsItem("batchTyp", normalizeLabel(batchType));
        }

        job.setMetadata(metadata);

        V1JobSpec spec = new V1JobSpec();
        spec.setTtlSecondsAfterFinished(jobConfig.ttlSecondsAfterFinished);
        spec.setBackoffLimit(jobConfig.backoffLimit);

        if (jobConfig.parallelism != null && jobConfig.parallelism > 0) {
            spec.setParallelism(jobConfig.parallelism);
        }
        if (jobConfig.completions != null && jobConfig.completions > 0) {
            spec.setCompletions(jobConfig.completions);
        }

        V1PodTemplateSpec podTemplate = new V1PodTemplateSpec();
        V1ObjectMeta podMetadata = new V1ObjectMeta();
        podMetadata.setName(jobName);
        podTemplate.setMetadata(podMetadata);

        V1PodSpec podSpec = new V1PodSpec();
        podSpec.setRestartPolicy("Never");

        V1Container container = new V1Container();
        container.setName(jobName);
        container.setImage(jobConfig.image);
        container.setImagePullPolicy(jobConfig.imagePullPolicy);

        if (jobConfig.command != null && !jobConfig.command.isEmpty()) {
            container.setCommand(jobConfig.command);
        }

        // Sätt envFrom för att ladda ALLA ConfigMap-värden som miljövariabler
        V1EnvFromSource envFrom = new V1EnvFromSource();
        V1ConfigMapEnvSource configMapRef = new V1ConfigMapEnvSource();
        configMapRef.setName(request.getConfigMapName());
        envFrom.setConfigMapRef(configMapRef);
        container.setEnvFrom(List.of(envFrom));

        // Lägg till extra miljövariabler från request (override ConfigMap-värden)
        if (jobConfig.extraEnv != null && !jobConfig.extraEnv.isEmpty()) {
            List<V1EnvVar> envVars = new ArrayList<>();
            jobConfig.extraEnv.forEach((key, value) -> {
                V1EnvVar envVar = new V1EnvVar();
                envVar.setName(key);
                envVar.setValue(value);
                envVars.add(envVar);
            });
            container.setEnv(envVars);
            LOG.info("Added {} environment variable overrides", envVars.size());
        }

        podSpec.setContainers(List.of(container));
        podTemplate.setSpec(podSpec);

        spec.setTemplate(podTemplate);
        job.setSpec(spec);

        return job;
    }

    /**
     * Extrahera Job-konfiguration från ConfigMap med request-overrides
     */
    private JobConfig extractJobConfig(Map<String, String> configMapData, JobStartRequest request) {
        JobConfig config = new JobConfig();

        // Image (required) - från ConfigMap eller request
        config.image = request.getImage() != null 
            ? request.getImage() 
            : configMapData.get("image");
        
        if (config.image == null || config.image.isEmpty()) {
            throw new JobException("'image' måste anges antingen i ConfigMap eller i request");
        }

        // Job name baseName
        config.baseName = request.getJobName() != null 
            ? request.getJobName() 
            : configMapData.getOrDefault("jobName", extractJobNameFromConfigMap(request.getConfigMapName()));

        // Image pull policy
        config.imagePullPolicy = request.getImagePullPolicy() != null 
            ? request.getImagePullPolicy() 
            : configMapData.getOrDefault("imagePullPolicy", "IfNotPresent");

        // TTL
        config.ttlSecondsAfterFinished = request.getTtlSecondsAfterFinished() > 0 
            ? request.getTtlSecondsAfterFinished() 
            : parseIntOrDefault(configMapData.get("ttlSecondsAfterFinished"), defaultTtlSeconds);

        // Backoff limit
        config.backoffLimit = parseIntOrDefault(configMapData.get("backoffLimit"), 3);

        // Parallelism
        config.parallelism = request.getParallelism() != null 
            ? request.getParallelism() 
            : parseIntOrNull(configMapData.get("parallelism"));

        // Completions
        config.completions = request.getCompletions() != null 
            ? request.getCompletions() 
            : parseIntOrNull(configMapData.get("completions"));

        // Command
        config.command = request.getCommand() != null 
            ? request.getCommand() 
            : parseCommandFromConfigMap(configMapData.get("command"));

        // Extra environment variables (från request)
        config.extraEnv = request.getEnv();

        LOG.info("Job configuration: image={}, parallelism={}, completions={}",
                config.image, config.parallelism, config.completions);

        return config;
    }

    /**
     * Extrahera jobName från ConfigMap-namnet
     * t.ex. "inf-batch-testapp1-config" -> "testapp1"
     */
    private String extractJobNameFromConfigMap(String configMapName) {
        // Ta bort "inf-batch-" prefix och "-config" suffix om de finns
        String name = configMapName
            .replaceFirst("^inf-batch-", "")
            .replaceFirst("-config$", "");
        return name.isEmpty() ? configMapName : name;
    }

    /**
     * Parse command från ConfigMap (kommaseparerad sträng)
     */
    private List<String> parseCommandFromConfigMap(String commandStr) {
        if (commandStr == null || commandStr.isEmpty()) {
            return null;
        }
        return Arrays.asList(commandStr.split(","));
    }

    /**
     * Parse int med default-värde
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid integer value: {}, using default: {}", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Parse int med null som default
     */
    private Integer parseIntOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid integer value: {}, returning null", value);
            return null;
        }
    }

    /**
     * Inner class för Job-konfiguration
     */
    private static class JobConfig {
        String baseName;
        String image;
        String imagePullPolicy;
        int ttlSecondsAfterFinished;
        int backoffLimit;
        Integer parallelism;
        Integer completions;
        List<String> command;
        Map<String, String> extraEnv;
    }

    private String normalizeLabel(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    /**
     * Generera ett unikt Job-namn
     */
    private String generateJobName(String baseName) {
        if (baseName == null || baseName.isEmpty()) {
            baseName = "batch-job";
        }
        long timestamp = System.currentTimeMillis();
        return baseName.toLowerCase().replaceAll("[^a-z0-9]", "-") 
            + "-" + (timestamp % 100000);
    }

    /**
     * Hämta status för ett specifikt Job
     */
    public JobStatusResponse getJobStatus(String jobId) {
        try {
            V1Job job = batchV1Api.readNamespacedJob(jobId, namespace, null);
            try {
                reportTerminalFromJob(job);
            } catch (Exception reportingError) {
                LOG.warn("Metrics reporting failed while reading job status {}: {}", jobId, reportingError.getMessage());
            }
            return mapJobToResponse(job);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new JobException("Job hittades inte: " + jobId);
            }
            throw new JobException("Misslyckades att hämta Job-status: " + e.getMessage(), e);
        }
    }

    /**
     * Lista alla aktiva Jobs
     */
    public List<JobStatusResponse> listJobs() {
        try {
            V1JobList jobList = batchV1Api.listNamespacedJob(
                namespace, 
                null,  // pretty
                null,  // allowWatchBookmarks
                null,  // _continue
                null,  // fieldSelector
                null,  // labelSelector
                null,  // limit
                null,  // resourceVersion
                null,  // resourceVersionMatch
                null,  // sendInitialEvents
                null,  // timeoutSeconds
                null   // watch
            );
            
            List<JobStatusResponse> response = new ArrayList<>();
            if (jobList.getItems() != null) {
                jobList.getItems().forEach(job -> response.add(mapJobToResponse(job)));
            }
            return response;
        } catch (ApiException e) {
            throw new JobException("Misslyckades att lista Jobs: " + e.getMessage(), e);
        }
    }

    /**
     * Ta bort ett Job
     */
    public void deleteJob(String jobId) {
        try {
            V1Job job = batchV1Api.readNamespacedJob(jobId, namespace, null);
            batchV1Api.deleteNamespacedJob(jobId, namespace, null, null, null, null, 
                null, null);

            try {
                MetricsContextEnvelope envelope = buildEnvelopeForJob(job);
                if (envelope != null) {
                    envelope.reporter().onStop(envelope.context());
                }
            } catch (Exception reportingError) {
                LOG.warn("Metrics reporting failed while deleting job {}: {}", jobId, reportingError.getMessage());
            }
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new JobException("Job hittades inte: " + jobId);
            }
            throw new JobException("Misslyckades att ta bort Job: " + e.getMessage(), e);
        }
    }

    /**
     * Konvertera V1Job till JobStatusResponse
     */
    private JobStatusResponse mapJobToResponse(V1Job job) {
        V1JobStatus status = job.getStatus();
        String jobStatus = determineStatus(status);

        String image = "N/A";
        if (job.getSpec() != null && job.getSpec().getTemplate() != null 
            && job.getSpec().getTemplate().getSpec() != null 
            && !job.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {
            image = job.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        }

        return JobStatusResponse.builder()
            .jobId(job.getMetadata().getName())
            .jobName(job.getMetadata().getName())
            .image(image)
            .status(jobStatus)
            .completions(status != null ? status.getSucceeded() : null)
            .parallelism(job.getSpec().getParallelism())
            .createdAt(convertToLocalDateTime(job.getMetadata().getCreationTimestamp()))
            .startTime(status != null ? convertToLocalDateTime(status.getStartTime()) : null)
            .completionTime(status != null ? convertToLocalDateTime(status.getCompletionTime()) : null)
            .build();
    }

    private LocalDateTime convertToLocalDateTime(java.time.OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.toLocalDateTime();
    }

    public String restartJob(String jobId) {
        try {
            V1Job existingJob = batchV1Api.readNamespacedJob(jobId, namespace, null);

            String status = determineStatus(existingJob.getStatus());
            if ("RUNNING".equals(status) || "PENDING".equals(status)) {
                deleteJob(jobId);
            }

            JobStartRequest restartRequest = toRestartRequest(existingJob);
            return startJob(restartRequest);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new JobException("Job hittades inte: " + jobId);
            }
            throw new JobException("Misslyckades att starta om Job: " + e.getMessage(), e);
        }
    }

    private JobStartRequest toRestartRequest(V1Job existingJob) {
        V1ObjectMeta metadata = existingJob.getMetadata();
        if (metadata == null || metadata.getLabels() == null) {
            throw new JobException("Job saknar labels med configMap-information");
        }

        String configMapName = metadata.getLabels().get("configMap");
        if (configMapName == null || configMapName.isBlank()) {
            throw new JobException("Job saknar configMap-label och kan inte restartas");
        }

        V1PodSpec podSpec = existingJob.getSpec() != null && existingJob.getSpec().getTemplate() != null
            ? existingJob.getSpec().getTemplate().getSpec()
            : null;

        V1Container container = podSpec != null && podSpec.getContainers() != null && !podSpec.getContainers().isEmpty()
            ? podSpec.getContainers().get(0)
            : null;

        JobStartRequest request = new JobStartRequest();
        request.setConfigMapName(configMapName);
        request.setJobName(extractBaseJobName(metadata.getName()));

        if (container != null) {
            request.setImage(container.getImage());
            request.setCommand(container.getCommand());
            request.setImagePullPolicy(container.getImagePullPolicy());

            Map<String, String> envOverrides = new HashMap<>();
            if (container.getEnv() != null) {
                for (V1EnvVar envVar : container.getEnv()) {
                    if (envVar.getName() != null && envVar.getValue() != null) {
                        envOverrides.put(envVar.getName(), envVar.getValue());
                    }
                }
            }
            if (!envOverrides.isEmpty()) {
                request.setEnv(envOverrides);
            }
        }

        V1JobSpec spec = existingJob.getSpec();
        if (spec != null) {
            if (spec.getParallelism() != null) {
                request.setParallelism(spec.getParallelism());
            }
            if (spec.getCompletions() != null) {
                request.setCompletions(spec.getCompletions());
            }
            if (spec.getTtlSecondsAfterFinished() != null) {
                request.setTtlSecondsAfterFinished(spec.getTtlSecondsAfterFinished());
            }
        }

        return request;
    }

    private String extractBaseJobName(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return "batch-job";
        }
        return jobName.replaceFirst("-[0-9]{1,10}$", "");
    }

    private String determineStatus(V1JobStatus status) {
        if (status != null) {
            if (status.getSucceeded() != null && status.getSucceeded() > 0) {
                return "COMPLETED";
            }
            if (status.getFailed() != null && status.getFailed() > 0) {
                return "FAILED";
            }
            if (status.getActive() != null && status.getActive() > 0) {
                return "RUNNING";
            }
        }
        return "PENDING";
    }

    private void monitorJobAsync(String jobId, BatchMetricsReporter reporter, BatchMetricsContext context) {
        monitorExecutor.submit(() -> {
            int faults = 0;
            while (faults < 5) {
                try {
                    Thread.sleep(5000);
                    V1Job job = batchV1Api.readNamespacedJob(jobId, namespace, null);
                    String status = determineStatus(job.getStatus());
                    if ("COMPLETED".equals(status)) {
                        sendTerminalMetricOnce(jobId, "COMPLETED", reporter, context, null);
                        return;
                    }
                    if ("FAILED".equals(status)) {
                        sendTerminalMetricOnce(jobId, "FAILED", reporter, context, "Job failed");
                        return;
                    }
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    sendTerminalMetricOnce(jobId, "FAILED", reporter, context, "Lifecycle monitor interrupted");
                    return;
                } catch (ApiException e) {
                    faults++;
                    LOG.warn("Failed reading job status for {} (attempt {}): {}", jobId, faults, e.getMessage());
                } catch (Exception e) {
                    faults++;
                    LOG.warn("Unexpected monitoring error for {} (attempt {}): {}", jobId, faults, e.getMessage());
                }
            }
            sendTerminalMetricOnce(jobId, "FAILED", reporter, context, "Too many status read failures");
        });
    }

    private void reportTerminalFromJob(V1Job job) {
        String status = determineStatus(job.getStatus());
        if (!"COMPLETED".equals(status) && !"FAILED".equals(status)) {
            return;
        }

        MetricsContextEnvelope envelope = buildEnvelopeForJob(job);
        if (envelope == null) {
            return;
        }

        if ("COMPLETED".equals(status)) {
            sendTerminalMetricOnce(job.getMetadata().getName(), status, envelope.reporter(), envelope.context(), null);
        } else {
            sendTerminalMetricOnce(job.getMetadata().getName(), status, envelope.reporter(), envelope.context(), "Job failed");
        }
    }

    private void sendTerminalMetricOnce(
        String jobId,
        String status,
        BatchMetricsReporter reporter,
        BatchMetricsContext context,
        String reason
    ) {
        String dedupeKey = jobId + ":" + status;
        if (!terminalMetricsSent.add(dedupeKey)) {
            return;
        }

        if ("COMPLETED".equals(status)) {
            reporter.onStop(context);
        } else {
            reporter.onError(context, reason);
        }
    }

    private MetricsContextEnvelope buildEnvelopeForJob(V1Job job) {
        if (job.getMetadata() == null || job.getMetadata().getLabels() == null) {
            return null;
        }

        String configMapName = job.getMetadata().getLabels().get("configMap");
        if (configMapName == null || configMapName.isBlank()) {
            return null;
        }

        Map<String, String> configMapData = configMapService.getConfigMapData(configMapName);
        String batchType = configMapData.getOrDefault("BATCH_TYP", "");
        BatchMetricsReporter reporter = metricsReporterFactory.forBatchType(batchType);

        Instant startTime = job.getStatus() != null && job.getStatus().getStartTime() != null
            ? job.getStatus().getStartTime().toInstant()
            : Instant.now();

        BatchMetricsContext context = createMetricsContext(
            job.getMetadata().getName(),
            job.getMetadata().getName(),
            configMapData,
            startTime
        );

        return new MetricsContextEnvelope(reporter, context);
    }

    private BatchMetricsContext createMetricsContext(
        String jobId,
        String jobName,
        Map<String, String> configMapData,
        Instant startTime
    ) {
        String object = configMapData.getOrDefault("TO_JOB_NAME", jobName);
        String chart = configMapData.getOrDefault("TO_ENV_NAME", "MANUELL");
        String environment = "jbatch";
        String user = Optional.ofNullable(System.getenv("LOGNAME"))
            .orElse(Optional.ofNullable(System.getenv("USERNAME")).orElse("unknown"));

        String server;
        try {
            server = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            server = "unknown";
        }
        if (server.contains(".")) {
            server = server.substring(0, server.indexOf('.'));
        }

        return new BatchMetricsContext(
            jobId,
            object,
            chart,
            environment,
            user,
            server,
            startTime,
            0
        );
    }

    @PreDestroy
    void shutdownMonitor() {
        monitorExecutor.shutdownNow();
    }

    private record MetricsContextEnvelope(BatchMetricsReporter reporter, BatchMetricsContext context) {
    }
}
