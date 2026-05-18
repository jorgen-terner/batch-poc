package infrastruktur.batch.service;

import infrastruktur.batch.gateway.KubernetesJobGateway;
import infrastruktur.batch.metrics.JobMetricsReporter;
import infrastruktur.batch.model.ExecutionStreamEventVO;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Edge case tests for SSE streaming in TemplateExecutionService.
 *
 * Tests cover:
 * - No logs emitted (empty output)
 * - Kubernetes transient errors during streaming
 * - Since parameter edge cases (far past, far future, invalid format)
 * - Multiple reconnects with cursor update
 * - Deduplication with real log scenarios
 */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
class TemplateExecutionStreamEdgeCasesTest {

    private KubernetesJobGateway kubernetesJobGateway;
    private JobPhaseResolver jobPhaseResolver;
    private JobMetricsReporter jobMetricsReporter;
    private TemplateExecutionService service;

    private static final String NS = "default";
    private static final String EXEC = "exec-1";
    private static final String TEMPLATE = "my-template";

    @BeforeEach
    void setUp() {
        kubernetesJobGateway = mock(KubernetesJobGateway.class);
        jobPhaseResolver = mock(JobPhaseResolver.class);
        jobMetricsReporter = mock(JobMetricsReporter.class);
        service = new TemplateExecutionService(kubernetesJobGateway, jobPhaseResolver, jobMetricsReporter, 1L, 2);
    }

    private Job jobWithTemplateLabel(String executionName, String templateName) {
        return new JobBuilder()
            .withNewMetadata().withName(executionName).endMetadata()
            .editOrNewMetadata().addToLabels("template", templateName).endMetadata()
            .build();
    }

    @Test
    void streamShouldHandleEmptyPodOutput() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(false)))
            .thenReturn("RUNNING");
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(true)))
            .thenReturn("SUCCEEDED");

        // Return empty output – should skip log emission
        when(kubernetesJobGateway.readExecutionLogsSinceTime(eq(NS), eq(EXEC), any()))
            .thenReturn(Map.of("pod-1", ""));

        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, null)
            .select().first(3)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertEquals(3, events.size());
        assertEquals("status", events.get(0).type());
        assertEquals("done", events.get(1).type());
        // No log event between status and done because output was empty
    }

    @Test
    void streamShouldHandleNullPodOutput() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(false)))
            .thenReturn("RUNNING");
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(true)))
            .thenReturn("SUCCEEDED");

        // Return null output – should skip log emission
        when(kubernetesJobGateway.readExecutionLogsSinceTime(eq(NS), eq(EXEC), any()))
            .thenReturn(Map.of("pod-1", null));

        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, null)
            .select().first(2)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertEquals(2, events.size());
        assertEquals("status", events.get(0).type());
        assertEquals("done", events.get(1).type());
    }

    @Test
    void streamShouldContinueOnTransientKubernetesError() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn("SUCCEEDED");

        // First call throws, second call succeeds
        when(kubernetesJobGateway.readExecutionLogsSinceTime(eq(NS), eq(EXEC), any()))
            .thenThrow(new KubernetesClientException("Temporary connection error", 500, null))
            .thenReturn(Map.of("pod-1", "log line\n"));

        // Collect 3 status events + 1 log + 1 done = 5 events, allowing retry
        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, null)
            .select().first(5)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(10));

        // Should have status, status (after retry), log, status (before done check), done
        assertEquals(5, events.size());
        assertEquals("status", events.get(0).type());
        // At least one log event should appear after transient error is recovered
        var hasLogEvent = events.stream().anyMatch(e -> "log".equals(e.type()));
        assert hasLogEvent : "Should have log event after transient error recovery";
    }

    @Test
    void streamWithSinceParameterInFarPastShouldIncludeHistoricalLogs() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(true)))
            .thenReturn("SUCCEEDED");

        Instant farPast = Instant.parse("1970-01-01T00:00:00Z");
        when(kubernetesJobGateway.readExecutionLogsSinceTime(
            eq(NS), eq(EXEC),
            any())) // Should be around Instant.EPOCH
            .thenReturn(Map.of("pod-1", "historical log\n"));

        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, farPast.toString())
            .select().first(3)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertEquals(3, events.size());
        assertEquals("status", events.get(0).type());
        assertEquals("log", events.get(1).type());
        assertEquals("historical log\n", events.get(1).output());
    }

    @Test
    void streamWithSinceParameterInFutureShouldNotEmitHistoricalLogs() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(true)))
            .thenReturn("SUCCEEDED");

        Instant farFuture = Instant.now().plus(1, ChronoUnit.HOURS);
        when(kubernetesJobGateway.readExecutionLogsSinceTime(eq(NS), eq(EXEC), any()))
            .thenReturn(Map.of()); // No logs since we're reading from future

        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, farFuture.toString())
            .select().first(2)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertEquals(2, events.size());
        assertEquals("status", events.get(0).type());
        assertEquals("done", events.get(1).type());
        // No log events
    }

    @Test
    void streamWithInvalidSinceParameterShouldParseAsEpoch() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(true)))
            .thenReturn("SUCCEEDED");

        // Invalid ISO-8601 should be parsed as EPOCH
        when(kubernetesJobGateway.readExecutionLogsSinceTime(eq(NS), eq(EXEC), any()))
            .thenReturn(Map.of("pod-1", "log\n"));

        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, "invalid-timestamp")
            .select().first(3)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(5));

        assertEquals(3, events.size());
        // Should still emit log (parsed as EPOCH, getting all historical logs)
        assertEquals("log", events.get(1).type());
    }

    @Test
    void streamShouldUpdateCursorOnEachPoll() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(false)))
            .thenReturn("RUNNING");
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(true)))
            .thenReturn("SUCCEEDED");
        when(kubernetesJobGateway.readExecutionLogsSinceTime(eq(NS), eq(EXEC), any()))
            .thenReturn(Map.of("pod-1", "line1\n"))
            .thenReturn(Map.of("pod-1", "line2\n"));

        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, null)
            .select().first(5)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(10));

        // Should have: status1, log1, status2, log2, status3?, done
        assertEquals(5, events.size());

        // Cursors should be present on all events
        for (var event : events) {
            assertNotNull(event.cursor(), "Event type " + event.type() + " should have cursor");
        }

        // Cursors should be in chronological order (or at least non-decreasing)
        for (int i = 1; i < events.size(); i++) {
            var prev = Instant.parse(events.get(i - 1).cursor());
            var curr = Instant.parse(events.get(i).cursor());
            assert !curr.isBefore(prev) : "Cursors should be non-decreasing: " + prev + " -> " + curr;
        }
    }

    @Test
    void streamShouldHandleMultiplePods() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(true)))
            .thenReturn("SUCCEEDED");
        when(kubernetesJobGateway.readExecutionLogsSinceTime(eq(NS), eq(EXEC), any()))
            .thenReturn(Map.of(
                "pod-1", "pod1-log\n",
                "pod-2", "pod2-log\n",
                "pod-3", "pod3-log\n"
            ));

        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, null)
            .select().first(5)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(5));

        // status, log-pod1, log-pod2, log-pod3, done
        assertEquals(5, events.size());
        assertEquals("status", events.get(0).type());
        assertEquals("log", events.get(1).type());
        assertEquals("log", events.get(2).type());
        assertEquals("log", events.get(3).type());
        assertEquals("done", events.get(4).type());

        var pods = events.stream()
            .filter(e -> "log".equals(e.type()))
            .map(ExecutionStreamEventVO::pod)
            .toList();
        assertEquals(3, pods.size());
        assert pods.contains("pod-1");
        assert pods.contains("pod-2");
        assert pods.contains("pod-3");
    }

    @Test
    void streamWithBlankPodOutputShouldSkip() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), eq(true)))
            .thenReturn("SUCCEEDED");

        when(kubernetesJobGateway.readExecutionLogsSinceTime(eq(NS), eq(EXEC), any()))
            .thenReturn(Map.of(
                "pod-1", "real log\n",
                "pod-2", "   \n", // whitespace only
                "pod-3", "" // empty
            ));

        List<ExecutionStreamEventVO> events = service.streamExecution(NS, EXEC, 1, null)
            .select().first(3)
            .collect().asList()
            .await().atMost(java.time.Duration.ofSeconds(5));

        // Should be: status, log (only pod-1), done
        assertEquals(3, events.size());
        assertEquals("log", events.get(1).type());
        assertEquals("pod-1", events.get(1).pod());
    }

}
