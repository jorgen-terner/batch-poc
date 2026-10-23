package infrastruktur.batch.javabatch;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobHookLifecycleTest {

    @Test
    void constructorShouldRejectInvalidPollInterval() {
        FakeJobHookClient client = new FakeJobHookClient();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new JobHookLifecycle(
                client,
                0,
                0,
                0,
                1,
                1,
                true,
                "STARTED",
                "COMPLETED",
                "FAILED"
            ));

        assertTrue(ex.getMessage().contains("status-poll-interval-seconds"));
    }

    @Test
    void constructorShouldRejectOverlappingStatusSets() {
        FakeJobHookClient client = new FakeJobHookClient();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new JobHookLifecycle(
                client,
                1,
                0,
                0,
                1,
                1,
                true,
                "STARTED,COMPLETED",
                "COMPLETED",
                "FAILED"
            ));

        assertTrue(ex.getMessage().contains("Status sets overlap"));
    }

    @Test
    void runToCompletionShouldReturnZeroForSuccessStatus() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueStatus("COMPLETED");

        JobHookLifecycle lifecycle = lifecycle(client, true, 2, 2);

        int exitCode = lifecycle.runToCompletion();

        assertEquals(0, exitCode);
    }

    @Test
    void runToCompletionShouldReturnOneForFailureStatus() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueStatus("FAILED");

        JobHookLifecycle lifecycle = lifecycle(client, true, 2, 2);

        int exitCode = lifecycle.runToCompletion();

        assertEquals(1, exitCode);
    }

    @Test
    void runToCompletionShouldFailWhenStatusErrorsReachRetryLimit() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueError(new RuntimeException("status failed"));

        JobHookLifecycle lifecycle = lifecycle(client, true, 1, 2);

        int exitCode = lifecycle.runToCompletion();

        assertEquals(1, exitCode);
    }

    @Test
    void runToCompletionShouldFailWhenFailOpenIsDisabledAndStatusThrows() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueError(new RuntimeException("status failed"));

        JobHookLifecycle lifecycle = lifecycle(client, false, 3, 2);

        int exitCode = lifecycle.runToCompletion();

        assertEquals(1, exitCode);
    }

    @Test
    void runToCompletionShouldFailWhenUnknownStatusRetryLimitReached() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueStatus("mystery-status");

        JobHookLifecycle lifecycle = lifecycle(client, true, 3, 1);

        int exitCode = lifecycle.runToCompletion();

        assertEquals(1, exitCode);
    }

    @Test
    void runToCompletionShouldKeepPollingForActiveStatusThenSucceed() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueStatus("STARTED");
        client.enqueueStatus("COMPLETED");

        JobHookLifecycle lifecycle = lifecycle(client, true, 2, 2);

        int exitCode = lifecycle.runToCompletion();

        assertEquals(0, exitCode);
        assertEquals(2, client.statusCallCount);
    }

    @Test
    void onStopShouldSkipWhenTerminalStateWasReached() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        JobHookLifecycle lifecycle = lifecycle(client, true, 2, 2);
        client.enqueueStatus("COMPLETED");

        int exitCode = lifecycle.runToCompletion();

        lifecycle.onStop(null);

        assertEquals(0, exitCode);
        assertEquals(0, client.stopInvocations);
    }

    @Test
    void onStopShouldInvokeStopWhenExecutionIsActive() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueStatus("STARTED");
        JobHookLifecycle lifecycle = lifecycle(client, true, 2, 2);

        setCurrentExecutionId(lifecycle, "exec-2");

        lifecycle.onStop(null);

        assertEquals(1, client.stopInvocations);
        assertEquals("exec-2", client.lastStoppedExecutionId);
    }

    @Test
    void onStopShouldSkipWhenExecutionIdIsBlank() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        JobHookLifecycle lifecycle = lifecycle(client, true, 2, 2);

        setCurrentExecutionId(lifecycle, "   ");

        lifecycle.onStop(null);

        assertEquals(0, client.stopInvocations);
    }

    @Test
    void onStopShouldSkipStopWhenExecutionIsNotActive() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueStatus("COMPLETED");
        JobHookLifecycle lifecycle = lifecycle(client, true, 2, 2);

        setCurrentExecutionId(lifecycle, "exec-3");

        lifecycle.onStop(null);

        assertEquals(0, client.stopInvocations);
    }

    @Test
    void onStopShouldInvokeStopWhenStatusCheckFails() throws Exception {
        FakeJobHookClient client = new FakeJobHookClient();
        client.enqueueError(new RuntimeException("status endpoint down"));
        JobHookLifecycle lifecycle = lifecycle(client, true, 2, 2);

        setCurrentExecutionId(lifecycle, "exec-4");

        lifecycle.onStop(null);

        assertEquals(1, client.stopInvocations);
        assertEquals("exec-4", client.lastStoppedExecutionId);
    }

    private static JobHookLifecycle lifecycle(
        FakeJobHookClient client,
        boolean failOpenOnStatusError,
        int maxStatusErrorRetries,
        int maxUnknownStatusRetries
    ) {
        return new JobHookLifecycle(
            client,
            1,
            0,
            0,
            maxStatusErrorRetries,
            maxUnknownStatusRetries,
            failOpenOnStatusError,
            "STARTING,STARTED,STOPPING",
            "COMPLETED",
            "FAILED,ABANDONED,UNKNOWN,STOPPED"
        );
    }

    @SuppressWarnings("unchecked")
    private static void setCurrentExecutionId(JobHookLifecycle lifecycle, String executionId) throws Exception {
        Field field = JobHookLifecycle.class.getDeclaredField("currentExecutionId");
        field.setAccessible(true);
        AtomicReference<String> ref = (AtomicReference<String>) field.get(lifecycle);
        ref.set(executionId);
    }

    private static final class FakeJobHookClient extends JobHookClient {

        private final Queue<Object> statusResponses = new ArrayDeque<>();

        private int stopInvocations;
        private int statusCallCount;
        private String lastStoppedExecutionId;

        private FakeJobHookClient() {
            super(
                "http://localhost/start",
                "http://localhost/status",
                "http://localhost/stop",
                "execId",
                1,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            );
        }

        private void enqueueStatus(String status) {
            statusResponses.add(status);
        }

        private void enqueueError(RuntimeException ex) {
            statusResponses.add(ex);
        }

        @Override
        public String invokeStartAndGetExecutionId() {
            return "exec-1";
        }

        @Override
        public String fetchBatchStatus(String executionId) {
            statusCallCount++;
            Object next = statusResponses.poll();
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (next instanceof String status) {
                return status;
            }
            return "STARTED";
        }

        @Override
        public void invokeStop(String executionId) {
            stopInvocations++;
            lastStoppedExecutionId = executionId;
        }
    }
}
