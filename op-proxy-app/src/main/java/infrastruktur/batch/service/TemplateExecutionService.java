package infrastruktur.batch.service;

import infrastruktur.batch.metrics.JobMetricsReporter;
import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.model.ExecutionLogsResponseVO;
import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.model.ExecutionStreamEventVO;
import infrastruktur.batch.model.StartExecutionRequestVO;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class TemplateExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateExecutionService.class);
    private static final long DEFAULT_STOP_GRACEFUL_POLL_INTERVAL_MILLIS = 1000L;
    private static final int DEFAULT_STOP_GRACEFUL_MAX_ATTEMPTS = 20;
    private static final long STREAM_OVERLAP_MILLIS = 1000L;
    private static final int STREAM_DEDUP_TAIL_CHARS = 4096;
    private static final int STREAM_IRRECOVERABLE_CHECK_EVERY = 6;

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
            throw new IllegalArgumentException("batch.execution.stop.graceful.poll-interval-millis måste vara >= 1");
        }
        if (stopGracefulMaxAttempts < 1) {
            throw new IllegalArgumentException("batch.execution.stop.graceful.max-attempts måste vara >= 1");
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
            throw new IllegalStateException("Skapad körning saknar metadata.name");
        }
        LOG.info("Startade körning {}/{} från template {} (timeoutSeconds={}, parametrar={})",
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
            "Körning startad",
            Instant.now()
        );
        reportExecutionAction(namespace, templateName, executionName, response, Map.of());
        return response;
    }

    public ExecutionStatusResponseVO status(String namespace, String executionName) {
        return status(namespace, executionName, true);
    }

    private ExecutionStatusResponseVO status(String namespace, String executionName, boolean includeIrrecoverableCheck) {
        Job executionJob = kubernetesJobGateway.requireJob(namespace, executionName);
        JobStatus status = executionJob.getStatus();

        int active = status != null && status.getActive() != null ? status.getActive() : 0;
        int succeeded = status != null && status.getSucceeded() != null ? status.getSucceeded() : 0;
        int failed = status != null && status.getFailed() != null ? status.getFailed() : 0;
        boolean suspended = executionJob.getSpec() != null && Boolean.TRUE.equals(executionJob.getSpec().getSuspend());
        boolean irrecoverablePodFailure = includeIrrecoverableCheck
            && kubernetesJobGateway.hasIrrecoverablePodFailure(namespace, executionName);

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

    public ExecutionLogsResponseVO logs(String namespace, String executionName, Integer tailLines) {
        if (tailLines != null && tailLines < 1) {
            throw new IllegalArgumentException("tailLines måste vara >= 1");
        }

        Job executionJob = kubernetesJobGateway.requireJob(namespace, executionName);
        Map<String, String> logsByPod = kubernetesJobGateway.readExecutionLogs(namespace, executionName, tailLines);
        return new ExecutionLogsResponseVO(
            namespace,
            resolveTemplateName(executionJob),
            executionName,
            tailLines,
            logsByPod
        );
    }

    /**
     * Returnerar en SSE-ström med periodiska statusuppdateringar och inkrementella loggrader.
     * Om {@code since} anges används den som tidscursor vid reconnect.
     */
    public Multi<ExecutionStreamEventVO> streamExecution(String namespace, String executionName, int intervalSeconds) {
        return streamExecution(namespace, executionName, intervalSeconds, null);
    }

    /**
     * Returnerar en SSE-ström med periodiska statusuppdateringar och inkrementella loggrader.
     *
     * @param since ISO-8601 tidscursor för inkrementell återanslutning.
     */
    public Multi<ExecutionStreamEventVO> streamExecution(
        String namespace,
        String executionName,
        int intervalSeconds,
        String since
    ) {
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("intervalSeconds måste vara >= 1");
        }

        kubernetesJobGateway.requireJob(namespace, executionName);
        long pollMillis = (long) intervalSeconds * 1000L;
        Instant[] cursor = {parseSinceInstant(since)};
        Map<String, String> emittedTailByPod = new HashMap<>();
        AtomicInteger pollCounter = new AtomicInteger(0);

        return Multi.createFrom().<ExecutionStreamEventVO>emitter(emitter -> {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "execution-stream-" + executionName);
                t.setDaemon(true);
                return t;
            });
            AtomicBoolean closed = new AtomicBoolean(false);

            Runnable poll = () -> {
                if (closed.get() || emitter.isCancelled()) {
                    return;
                }

                Instant upperBound = Instant.now();
                ExecutionStatusResponseVO s;
                try {
                    boolean checkIrrecoverable = pollCounter.getAndIncrement() % STREAM_IRRECOVERABLE_CHECK_EVERY == 0;
                    s = status(namespace, executionName, checkIrrecoverable);
                } catch (KubernetesClientException ex) {
                    LOG.warn("Tillfälligt Kubernetes-fel under strömning av {}/{}, försöker igen: {}",
                        namespace, executionName, ex.getMessage());
                    return;
                } catch (Exception ex) {
                    if (closed.compareAndSet(false, true)) {
                        emitter.fail(ex);
                        scheduler.shutdownNow();
                    }
                    return;
                }

                String cursorValue = upperBound.toString();
                emitter.emit(ExecutionStreamEventVO.status(s, cursorValue));

                Instant overlapStart = Instant.EPOCH.plusMillis(STREAM_OVERLAP_MILLIS);
                Instant readFrom = cursor[0].isAfter(overlapStart)
                    ? cursor[0].minusMillis(STREAM_OVERLAP_MILLIS)
                    : Instant.EPOCH;
                Map<String, String> newLogsByPod;
                try {
                    newLogsByPod = kubernetesJobGateway.readExecutionLogsSinceTime(
                        namespace,
                        executionName,
                        readFrom
                    );
                } catch (KubernetesClientException ex) {
                    LOG.warn("Tillfälligt Kubernetes-fel vid loggströmning av {}/{}, fortsätter utan loggar i denna poll: {}",
                        namespace, executionName, ex.getMessage());
                    newLogsByPod = Map.of();
                }
                cursor[0] = upperBound;

                newLogsByPod.forEach((pod, output) -> {
                    if (output == null || output.isBlank()) {
                        return;
                    }

                    String previousTail = emittedTailByPod.getOrDefault(pod, "");
                    String deduped = removePrefixOverlap(previousTail, output);
                    if (deduped.isBlank()) {
                        return;
                    }

                    emitter.emit(ExecutionStreamEventVO.log(pod, deduped, cursorValue));
                    emittedTailByPod.put(pod, tail(previousTail + deduped, STREAM_DEDUP_TAIL_CHARS));
                });

                if (isTerminalPhase(s.phase()) && closed.compareAndSet(false, true)) {
                    emitter.emit(ExecutionStreamEventVO.done(s.phase(), phaseToExitCode(s.phase()), cursorValue));
                    emitter.complete();
                    scheduler.shutdownNow();
                }
            };

            ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(poll, 0L, pollMillis, TimeUnit.MILLISECONDS);
            emitter.onTermination(() -> {
                if (closed.compareAndSet(false, true)) {
                    future.cancel(true);
                    scheduler.shutdownNow();
                }
            });
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Instant parseSinceInstant(String since) {
        if (since == null || since.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(since);
        } catch (DateTimeParseException ex) {
            LOG.warn("Kunde inte parsa since='{}', startar från epok: {}", since, ex.getMessage());
            return Instant.EPOCH;
        }
    }

    private static String removePrefixOverlap(String previousTail, String currentChunk) {
        if (previousTail == null || previousTail.isEmpty() || currentChunk == null || currentChunk.isEmpty()) {
            return currentChunk == null ? "" : currentChunk;
        }

        int max = Math.min(previousTail.length(), currentChunk.length());
        for (int overlap = max; overlap > 0; overlap--) {
            String previousSuffix = previousTail.substring(previousTail.length() - overlap);
            String currentPrefix = currentChunk.substring(0, overlap);
            if (previousSuffix.equals(currentPrefix)) {
                return currentChunk.substring(overlap);
            }
        }
        return currentChunk;
    }

    private static String tail(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(value.length() - maxChars);
    }

    private static boolean isTerminalPhase(String phase) {
        return switch (phase.toUpperCase(Locale.ROOT)) {
            case "SUCCEEDED", "FAILED", "STOPPED", "CANCELLED" -> true;
            default -> false;
        };
    }

    private static int phaseToExitCode(String phase) {
        return switch (phase.toUpperCase(Locale.ROOT)) {
            case "SUCCEEDED", "STOPPED", "CANCELLED" -> 0;
            case "FAILED" -> 2;
            case "SUSPENDED" -> 3;
            default -> 4;
        };
    }

    public ExecutionActionResponseVO stop(String namespace, String executionName) {
        Job executionJob = kubernetesJobGateway.requireJob(namespace, executionName);
        String templateName = resolveTemplateName(executionJob);

        try {
            kubernetesJobGateway.patchSuspend(namespace, executionName, true);
        } catch (KubernetesClientException ex) {
            LOG.warn("Kunde inte pausa körning {}/{} före stopp: {}", namespace, executionName, ex.getMessage());
        }

        int remainingActivePods = kubernetesJobGateway.waitForActivePodsToStop(
            namespace,
            executionName,
            stopGracefulPollIntervalMillis,
            stopGracefulMaxAttempts
        );

        if (remainingActivePods > 0) {
            int deletedActivePods = kubernetesJobGateway.deleteActivePods(namespace, executionName);
            LOG.warn(
                "Tidsgräns för graceful stop nåddes för körning {}/{} ({} aktiv(a) podd(ar) kvar). Tvingad radering tog bort {} aktiv(a) podd(ar)",
                namespace,
                executionName,
                remainingActivePods,
                deletedActivePods
            );
        }

        kubernetesJobGateway.deleteJob(namespace, executionName);
        LOG.info(
            "Stoppade körning {}/{} (graceful stop), körningsjobb raderat",
            namespace,
            executionName
        );
        ExecutionActionResponseVO response = action(
            namespace,
            templateName,
            executionName,
            "stop",
            "STOPPED",
            "Körning stoppad (graceful stop), körningsjobb raderat"
        );
        reportExecutionAction(namespace, response.templateName(), executionName, response, Map.of());
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
            LOG.warn("Körning {} saknar metadata eller labels - kan inte avgöra template-namn",
                meta != null ? meta.getName() : "<okänd>");
            return "UNKNOWN";
        }
        String labelValue = meta.getLabels().get(KubernetesJobGateway.TEMPLATE_NAME_LABEL);
        if (labelValue == null || labelValue.isBlank()) {
            LOG.warn("Körning {} saknar label '{}' - kan inte avgöra template-namn",
                meta.getName(), KubernetesJobGateway.TEMPLATE_NAME_LABEL);
            return "UNKNOWN";
        }
        return labelValue;
    }

    private void validateTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("templateName måste anges");
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
