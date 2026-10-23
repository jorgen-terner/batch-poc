package infrastruktur.batch.runner;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class JobHookRunner {
    private final RunnerConfig config;
    private final JobHookClient client;
    private final AtomicReference<String> currentExecutionId = new AtomicReference<>();
    private final AtomicBoolean terminalStateReached = new AtomicBoolean(false);

    JobHookRunner(RunnerConfig config, JobHookClient client) {
        this.config = config;
        this.client = client;
    }

    int runToCompletion() throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown, "job-hook-stop"));

        String executionId = client.invokeStartAndGetExecutionId();
        currentExecutionId.set(executionId);

        int consecutiveStatusErrors = 0;
        int consecutiveUnknownStatuses = 0;
        Instant pollStart = Instant.now();
        RunnerLog.info("Started external batch with executionId=" + executionId);

        while (true) {
            if (config.maxPollSeconds() > 0) {
                long elapsedSeconds = Duration.between(pollStart, Instant.now()).toSeconds();
                if (elapsedSeconds >= config.maxPollSeconds()) {
                    RunnerLog.error("Polling exceeded max duration of " + config.maxPollSeconds() + " second(s). Marking job as failed");
                    return 1;
                }
            }

            String status;
            try {
                status = client.fetchBatchStatus(executionId);
                consecutiveStatusErrors = 0;
            } catch (RuntimeException ex) {
                consecutiveUnknownStatuses = 0;
                if (config.failOpenOnStatusError()) {
                    consecutiveStatusErrors++;
                    if (consecutiveStatusErrors >= config.maxStatusErrorRetries()) {
                        RunnerLog.error(
                                "STATUS call failed " + consecutiveStatusErrors + " time(s) in a row (limit "
                                        + config.maxStatusErrorRetries() + "). Marking job as failed",
                                ex
                        );
                        return 1;
                    }
                    RunnerLog.warn(
                            "STATUS call failed (" + consecutiveStatusErrors + " of " + config.maxStatusErrorRetries()
                                    + " before fail). Retrying in " + config.statusPollIntervalSeconds() + " second(s)",
                            ex
                    );
                    Thread.sleep(config.statusPollIntervalSeconds() * 1000L);
                    continue;
                }
                RunnerLog.error("STATUS call failed and fail-open is disabled. Marking job as failed", ex);
                return 1;
            }

            RunnerLog.info("External status for executionId " + executionId + " is " + status);

            if (config.successStatuses().contains(status)) {
                terminalStateReached.set(true);
                RunnerLog.info("External batch completed successfully with status " + status);
                return 0;
            }

            if (config.failureStatuses().contains(status)) {
                terminalStateReached.set(true);
                RunnerLog.error("External batch ended in failure status " + status);
                return 1;
            }

            if (!config.activeStatuses().contains(status)) {
                consecutiveUnknownStatuses++;
                if (consecutiveUnknownStatuses >= config.maxUnknownStatusRetries()) {
                    RunnerLog.error(
                            "Received " + consecutiveUnknownStatuses + " unclassified STATUS response(s) in a row (limit "
                                    + config.maxUnknownStatusRetries() + "). Marking job as failed. Last status=" + status
                    );
                    return 1;
                }
                RunnerLog.warn(
                        "Received unclassified status " + status + " (" + consecutiveUnknownStatuses + " of "
                                + config.maxUnknownStatusRetries() + " before fail). Treating as active and continuing polling"
                );
            } else {
                consecutiveUnknownStatuses = 0;
            }

            Thread.sleep(config.statusPollIntervalSeconds() * 1000L);
        }
    }

    private void onShutdown() {
        if (terminalStateReached.get()) {
            RunnerLog.info("Shutdown after terminal status. STOP hook is not required");
            return;
        }

        String executionId = currentExecutionId.get();
        if (executionId == null || executionId.isBlank()) {
            RunnerLog.info("No executionId available during shutdown. STOP hook is skipped");
            return;
        }

        try {
            if (!shouldInvokeStopHook(executionId)) {
                RunnerLog.info("Skipping STOP hook because external status is no longer active");
                return;
            }

            client.invokeStop(executionId);
            if (config.stopWaitSeconds() > 0) {
                RunnerLog.info("Waiting " + config.stopWaitSeconds() + " second(s) before shutdown completes");
                Thread.sleep(config.stopWaitSeconds() * 1000L);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            RunnerLog.warn("Interrupted while waiting before shutdown", ex);
        } catch (RuntimeException ex) {
            RunnerLog.warn("Stop hook failed", ex);
        }
    }

    private boolean shouldInvokeStopHook(String executionId) {
        try {
            String status = client.fetchBatchStatus(executionId);
            boolean shouldStop = config.activeStatuses().contains(status);
            RunnerLog.info("Shutdown status check for executionId " + executionId + " returned " + status + ". invoke STOP=" + shouldStop);
            return shouldStop;
        } catch (RuntimeException ex) {
            RunnerLog.warn("Failed to verify status before STOP. Invoking STOP as a safe fallback", ex);
            return true;
        }
    }
}
