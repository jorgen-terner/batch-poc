package infrastruktur.batch.service;

import infrastruktur.batch.metrics.JobMetricsReporter;
import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.model.ExecutionLogsResponseVO;
import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.model.JobParameterVO;
import infrastruktur.batch.model.StartExecutionRequestVO;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class TemplateExecutionServiceTest {

    @Mock
    KubernetesJobGateway kubernetesJobGateway;
    @Mock
    JobPhaseResolver jobPhaseResolver;
    @Mock
    JobMetricsReporter jobMetricsReporter;

    TemplateExecutionService service;

    private static final String NS = "default";
    private static final String TEMPLATE = "my-template";
    private static final String EXEC = "my-exec-001";

    @BeforeEach
    void setUp() {
        // 1ms poll, 2 attempts – fast tests
        service = new TemplateExecutionService(kubernetesJobGateway, jobPhaseResolver, jobMetricsReporter, 1L, 2);
    }

    // ─── start ─────────────────────────────────────────────────────────────────

    @Test
    void startShouldReturnPendingResponse() {
        when(kubernetesJobGateway.createExecutionFromTemplate(eq(NS), eq(TEMPLATE), any(), any()))
            .thenReturn(jobWithName(EXEC));

        ExecutionActionResponseVO response = service.start(NS, TEMPLATE,
            new StartExecutionRequestVO("req-1", 300L, null));

        assertEquals("start", response.action());
        assertEquals("PENDING", response.state());
        assertEquals(EXEC, response.executionName());
        assertEquals(TEMPLATE, response.templateName());
        assertEquals("req-1", response.clientRequestId());
        assertNotNull(response.createdAt());
        verify(jobMetricsReporter).report(eq(NS), eq("EXECUTION"), eq(EXEC), eq("PENDING"), any(), any());
    }

    @Test
    void startWithNullRequestShouldUseDefaults() {
        when(kubernetesJobGateway.createExecutionFromTemplate(eq(NS), eq(TEMPLATE), eq(null), eq(Map.of())))
            .thenReturn(jobWithName(EXEC));

        ExecutionActionResponseVO response = service.start(NS, TEMPLATE, null);

        assertEquals("PENDING", response.state());
        assertNull(response.clientRequestId());
    }

    @Test
    void startWithBlankTemplateNameShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.start(NS, "  ", null));
        assertThrows(IllegalArgumentException.class, () -> service.start(NS, null, null));
    }

    @Test
    void startWithInvalidTimeoutShouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
            service.start(NS, TEMPLATE, new StartExecutionRequestVO(null, 0L, null)));
    }

    @Test
    void startWhenCreatedJobHasNoNameShouldThrow() {
        when(kubernetesJobGateway.createExecutionFromTemplate(any(), any(), any(), any()))
            .thenReturn(new JobBuilder().withNewMetadata().endMetadata().build());

        assertThrows(IllegalStateException.class, () -> service.start(NS, TEMPLATE, null));
    }

    @Test
    void startWithParametersShouldPassNormalizedParameters() {
        when(kubernetesJobGateway.createExecutionFromTemplate(
            eq(NS), eq(TEMPLATE), eq(null),
            eq(Map.of("FILE", "/data/1.csv"))))
            .thenReturn(jobWithName(EXEC));

        service.start(NS, TEMPLATE, new StartExecutionRequestVO(null, null,
            List.of(new JobParameterVO("FILE", "/data/1.csv"))));

        verify(kubernetesJobGateway).createExecutionFromTemplate(
            eq(NS), eq(TEMPLATE), eq(null), eq(Map.of("FILE", "/data/1.csv")));
    }

    // ─── status ────────────────────────────────────────────────────────────────

    @Test
    void statusShouldReturnResolvedPhase() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(false);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn("RUNNING");

        ExecutionStatusResponseVO response = service.status(NS, EXEC);

        assertEquals("RUNNING", response.phase());
        assertEquals(TEMPLATE, response.templateName());
        assertEquals(EXEC, response.executionName());
        assertEquals(NS, response.namespace());
    }

    @Test
    void statusWhenIrrecoverablePodFailureDuringRunShouldBecomeFailed() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(true);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn("RUNNING");

        ExecutionStatusResponseVO response = service.status(NS, EXEC);

        assertEquals("FAILED", response.phase());
    }

    @Test
    void statusWhenIrrecoverablePodFailureDuringPendingShouldBecomeFailed() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(true);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn("PENDING");

        ExecutionStatusResponseVO response = service.status(NS, EXEC);

        assertEquals("FAILED", response.phase());
    }

    @Test
    void statusWhenIrrecoverablePodFailureButAlreadySucceededShouldStaySucceeded() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.hasIrrecoverablePodFailure(NS, EXEC)).thenReturn(true);
        when(jobPhaseResolver.resolvePhase(any(), anyInt(), anyInt(), anyInt(), anyBoolean()))
            .thenReturn("SUCCEEDED");

        ExecutionStatusResponseVO response = service.status(NS, EXEC);

        assertEquals("SUCCEEDED", response.phase());
    }

    @Test
    void statusWhenJobNotFoundShouldPropagate() {
        when(kubernetesJobGateway.requireJob(NS, "missing"))
            .thenThrow(new NoSuchElementException("Job not found: default/missing"));

        assertThrows(NoSuchElementException.class, () -> service.status(NS, "missing"));
    }

    // ─── logs ─────────────────────────────────────────────────────────────────

    @Test
    void logsShouldReturnPodLogs() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.readExecutionLogs(NS, EXEC, 50))
            .thenReturn(Map.of("pod-1", "line1\nline2\n"));

        ExecutionLogsResponseVO response = service.logs(NS, EXEC, 50);

        assertEquals(NS, response.namespace());
        assertEquals(TEMPLATE, response.templateName());
        assertEquals(EXEC, response.executionName());
        assertEquals(50, response.tailLines());
        assertEquals("line1\nline2\n", response.logsByPod().get("pod-1"));
    }

    @Test
    void logsWithInvalidTailLinesShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.logs(NS, EXEC, 0));
        verify(kubernetesJobGateway, never()).requireJob(anyString(), anyString());
    }

    @Test
    void logsWhenJobNotFoundShouldPropagate() {
        when(kubernetesJobGateway.requireJob(NS, "missing"))
            .thenThrow(new NoSuchElementException("Job not found: default/missing"));

        assertThrows(NoSuchElementException.class, () -> service.logs(NS, "missing", null));
    }

    // ─── stop ──────────────────────────────────────────────────────────────────

    @Test
    void stopShouldReturnStoppedWhenGraceful() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.waitForActivePodsToStop(eq(NS), eq(EXEC), anyLong(), anyInt()))
            .thenReturn(0);

        ExecutionActionResponseVO response = service.stop(NS, EXEC);

        assertEquals("stop", response.action());
        assertEquals("STOPPED", response.state());
        verify(kubernetesJobGateway).patchSuspend(NS, EXEC, true);
        verify(kubernetesJobGateway).deleteJob(NS, EXEC);
        verify(kubernetesJobGateway, never()).deleteActivePods(any(), any());
    }

    @Test
    void stopShouldForceDeletePodsWhenGracefulTimeoutExceeded() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        when(kubernetesJobGateway.waitForActivePodsToStop(eq(NS), eq(EXEC), anyLong(), anyInt()))
            .thenReturn(2); // 2 pods still active after graceful wait
        when(kubernetesJobGateway.deleteActivePods(NS, EXEC)).thenReturn(2);

        ExecutionActionResponseVO response = service.stop(NS, EXEC);

        assertEquals("STOPPED", response.state());
        verify(kubernetesJobGateway).deleteActivePods(NS, EXEC);
        verify(kubernetesJobGateway).deleteJob(NS, EXEC);
    }

    @Test
    void stopShouldContinueWhenPatchSuspendFails() {
        when(kubernetesJobGateway.requireJob(NS, EXEC)).thenReturn(jobWithTemplateLabel(EXEC, TEMPLATE));
        doThrow(new KubernetesClientException("forbidden", 403, null))
            .when(kubernetesJobGateway).patchSuspend(NS, EXEC, true);
        when(kubernetesJobGateway.waitForActivePodsToStop(eq(NS), eq(EXEC), anyLong(), anyInt()))
            .thenReturn(0);

        // Should NOT throw – the KubernetesClientException is caught as a warning
        ExecutionActionResponseVO response = service.stop(NS, EXEC);

        assertEquals("STOPPED", response.state());
        verify(kubernetesJobGateway).deleteJob(NS, EXEC);
    }

    @Test
    void stopWhenJobNotFoundShouldPropagate() {
        when(kubernetesJobGateway.requireJob(NS, "missing"))
            .thenThrow(new NoSuchElementException("Job not found: default/missing"));

        assertThrows(NoSuchElementException.class, () -> service.stop(NS, "missing"));
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private static Job jobWithName(String name) {
        return new JobBuilder()
            .withNewMetadata().withName(name).endMetadata()
            .build();
    }

    private static Job jobWithTemplateLabel(String name, String templateName) {
        return new JobBuilder()
            .withNewMetadata()
                .withName(name)
                .addToLabels(KubernetesJobGateway.TEMPLATE_NAME_LABEL, templateName)
            .endMetadata()
            .withNewStatus().endStatus()
            .build();
    }
}
