package infrastruktur.batch.javabatch;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class JobHookLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(JobHookLifecycle.class);

    private final JobHookClient jobHookClient;
    private final long statusPollIntervalSeconds;
    private final long maxPollSeconds;
    private final long stopWaitSeconds;
    private final boolean failOpenOnStatusError;
    private final int maxStatusErrorRetries;
    private final int maxUnknownStatusRetries;
    private final Set<String> activeStatuses;
    private final Set<String> successStatuses;
    private final Set<String> failureStatuses;
    private final AtomicReference<String> currentExecutionId = new AtomicReference<>();
    private final AtomicBoolean terminalStateReached = new AtomicBoolean(false);

    @Inject
    public JobHookLifecycle(
        JobHookClient jobHookClient,
        @ConfigProperty(name = "job.hook.status-poll-interval-seconds", defaultValue = "5") long statusPollIntervalSeconds,
        @ConfigProperty(name = "job.hook.max-poll-seconds", defaultValue = "3600") long maxPollSeconds,
        @ConfigProperty(name = "job.hook.stop-wait-seconds", defaultValue = "15") long stopWaitSeconds,
        @ConfigProperty(name = "job.hook.max-status-error-retries", defaultValue = "12") int maxStatusErrorRetries,
        @ConfigProperty(name = "job.hook.max-unknown-status-retries", defaultValue = "12") int maxUnknownStatusRetries,
        @ConfigProperty(name = "job.hook.fail-open-on-status-error", defaultValue = "true") boolean failOpenOnStatusError,
        @ConfigProperty(name = "job.hook.active-status-values", defaultValue = "STARTING,STARTED,STOPPING") String activeStatuses,
        @ConfigProperty(name = "job.hook.success-status-values", defaultValue = "COMPLETED") String successStatuses,
        @ConfigProperty(name = "job.hook.failure-status-values", defaultValue = "FAILED,ABANDONED,UNKNOWN,STOPPED") String failureStatuses
    ) {
        if (statusPollIntervalSeconds < 1) {
            throw new IllegalArgumentException("job.hook.status-poll-interval-seconds must be >= 1");
        }
        if (maxPollSeconds < 0) {
            throw new IllegalArgumentException("job.hook.max-poll-seconds must be >= 0");
        }
        if (stopWaitSeconds < 0) {
            throw new IllegalArgumentException("job.hook.stop-wait-seconds must be >= 0");
        }
        if (maxStatusErrorRetries < 0) {
            throw new IllegalArgumentException("job.hook.max-status-error-retries must be >= 0");
        }
        if (maxUnknownStatusRetries < 0) {
            throw new IllegalArgumentException("job.hook.max-unknown-status-retries must be >= 0");
        }
        this.jobHookClient = jobHookClient;
        this.statusPollIntervalSeconds = statusPollIntervalSeconds;
        this.maxPollSeconds = maxPollSeconds;
        this.stopWaitSeconds = stopWaitSeconds;
        this.maxStatusErrorRetries = maxStatusErrorRetries;
        this.maxUnknownStatusRetries = maxUnknownStatusRetries;
        this.failOpenOnStatusError = failOpenOnStatusError;
        this.activeStatuses = parseStatuses(activeStatuses, "job.hook.active-status-values");
        this.successStatuses = parseStatuses(successStatuses, "job.hook.success-status-values");
        this.failureStatuses = parseStatuses(failureStatuses, "job.hook.failure-status-values");
        validateStatusSets();
    }

    public int runToCompletion() throws InterruptedException {
        String executionId = jobHookClient.invokeStartAndGetExecutionId();
        currentExecutionId.set(executionId);
        int consecutiveStatusErrors = 0;
        int consecutiveUnknownStatuses = 0;
        Instant pollStart = Instant.now();

        LOG.info("Started external batch with executionId={}", executionId);
        while (true) {
            if (maxPollSeconds > 0) {
                long elapsedSeconds = Duration.between(pollStart, Instant.now()).toSeconds();
                if (elapsedSeconds >= maxPollSeconds) {
                    LOG.error("Polling exceeded max duration of {} second(s). Marking job as failed", maxPollSeconds);
                    return 1;
                }
            }

            String status;
            try {
                status = jobHookClient.fetchBatchStatus(executionId);
                consecutiveStatusErrors = 0;
            } catch (RuntimeException ex) {
                // A transport/error response is not an unknown status value.
                consecutiveUnknownStatuses = 0;
                if (failOpenOnStatusError) {
                    consecutiveStatusErrors++;
                    if (consecutiveStatusErrors >= maxStatusErrorRetries) {
                        LOG.error(
                            "STATUS call failed {} time(s) in a row (limit {}, fail condition: count >= limit). Marking job as failed",
                            consecutiveStatusErrors,
                            maxStatusErrorRetries,
                            ex
                        );
                        return 1;
                    }
                    LOG.warn(
                        "STATUS call failed ({} of {} before fail). Retrying in {} second(s)",
                        consecutiveStatusErrors,
                        maxStatusErrorRetries,
                        statusPollIntervalSeconds,
                        ex
                    );
                    Thread.sleep(statusPollIntervalSeconds * 1000L);
                    continue;
                }
                LOG.error("STATUS call failed and fail-open is disabled. Marking job as failed", ex);
                return 1;
            }

            String normalizedStatus = normalize(status);
            LOG.info("External status for executionId {} is {}", executionId, normalizedStatus);

            if (successStatuses.contains(normalizedStatus)) {
                terminalStateReached.set(true);
                LOG.info("External batch completed successfully with status {}", normalizedStatus);
                return 0;
            }

            if (failureStatuses.contains(normalizedStatus)) {
                terminalStateReached.set(true);
                LOG.error("External batch ended in failure status {}", normalizedStatus);
                return 1;
            }

            if (!activeStatuses.contains(normalizedStatus)) {
                consecutiveUnknownStatuses++;
                if (consecutiveUnknownStatuses >= maxUnknownStatusRetries) {
                    LOG.error(
                        "Received {} unclassified STATUS response(s) in a row (limit {}, fail condition: count >= limit). Marking job as failed. Last status={}",
                        consecutiveUnknownStatuses,
                        maxUnknownStatusRetries,
                        normalizedStatus
                    );
                    return 1;
                }
                LOG.warn(
                    "Received unclassified status {} ({} of {} before fail). Treating as active and continuing polling",
                    normalizedStatus,
                    consecutiveUnknownStatuses,
                    maxUnknownStatusRetries
                );
            } else {
                consecutiveUnknownStatuses = 0;
            }
            Thread.sleep(statusPollIntervalSeconds * 1000L);
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (terminalStateReached.get()) {
            LOG.info("Shutdown after terminal status. STOP hook is not required");
            return;
        }

        String executionId = currentExecutionId.get();
        if (executionId == null || executionId.isBlank()) {
            LOG.info("No executionId available during shutdown. STOP hook is skipped");
            return;
        }

        try {
            if (!shouldInvokeStopHook(executionId)) {
                LOG.info("Skipping STOP hook because external status is no longer active");
                return;
            }

            jobHookClient.invokeStop(executionId);
            if (stopWaitSeconds > 0) {
                LOG.info("Waiting {} second(s) before shutdown completes", stopWaitSeconds);
                Thread.sleep(stopWaitSeconds * 1000);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting before shutdown", ex);
        } catch (RuntimeException ex) {
            LOG.warn("Stop hook failed", ex);
        }
    }

    private Set<String> parseStatuses(String raw, String propertyName) {
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        if (raw != null) {
            Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::normalize)
                .forEach(statuses::add);
        }
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException(propertyName + " must define at least one status value");
        }
        return Set.copyOf(statuses);
    }

    private void validateStatusSets() {
        assertNoOverlap(activeStatuses, successStatuses, "active", "success");
        assertNoOverlap(activeStatuses, failureStatuses, "active", "failure");
        assertNoOverlap(successStatuses, failureStatuses, "success", "failure");
    }

    private void assertNoOverlap(Set<String> first, Set<String> second, String firstName, String secondName) {
        Set<String> overlap = new HashSet<>(first);
        overlap.retainAll(second);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                "Status sets overlap between " + firstName + " and " + secondName + ": " + overlap
            );
        }
    }

    private boolean shouldInvokeStopHook(String executionId) {
        try {
            String status = normalize(jobHookClient.fetchBatchStatus(executionId));
            boolean shouldStop = activeStatuses.contains(status);
            LOG.info("Shutdown status check for executionId {} returned {}. invoke STOP={}", executionId, status, shouldStop);
            return shouldStop;
        } catch (RuntimeException ex) {
            LOG.warn("Failed to verify status before STOP. Invoking STOP as a safe fallback", ex);
            return true;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

}