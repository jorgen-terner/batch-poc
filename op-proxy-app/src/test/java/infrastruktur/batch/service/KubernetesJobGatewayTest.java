package infrastruktur.batch.service;

import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableKubernetesMockClient(crud = true)
class KubernetesJobGatewayTest {

    KubernetesMockServer mockServer;
    KubernetesClient client;

    private KubernetesJobGateway gateway;
    private static final String NS = "test-ns";

    @BeforeEach
    void setUp() {
        gateway = new KubernetesJobGateway(client);
    }

    // ─── requireJob ────────────────────────────────────────────────────────────

    @Test
    void requireJobShouldReturnExistingJob() {
        createJob("my-exec");

        Job job = gateway.requireJob(NS, "my-exec");

        assertNotNull(job);
        assertEquals("my-exec", job.getMetadata().getName());
    }

    @Test
    void requireJobShouldThrowWhenNotFound() {
        assertThrows(NoSuchElementException.class, () -> gateway.requireJob(NS, "nonexistent"));
    }

    // ─── patchSuspend ──────────────────────────────────────────────────────────

    @Test
    void patchSuspendShouldUpdateSuspendFlag() {
        createJob("my-exec");

        gateway.patchSuspend(NS, "my-exec", true);

        Job patched = client.batch().v1().jobs().inNamespace(NS).withName("my-exec").get();
        assertTrue(Boolean.TRUE.equals(patched.getSpec().getSuspend()));
    }

    // ─── deleteJob ─────────────────────────────────────────────────────────────

    @Test
    void deleteJobShouldRemoveJob() {
        createJob("to-delete");

        gateway.deleteJob(NS, "to-delete");

        Job result = client.batch().v1().jobs().inNamespace(NS).withName("to-delete").get();
        assertNull(result);  // job removed
    }

    // ─── createJob ─────────────────────────────────────────────────────────────

    @Test
    void createJobShouldPersistJob() {
        Job job = new JobBuilder()
            .withNewMetadata().withName("new-exec").withNamespace(NS).endMetadata()
            .withNewSpec()
                .withNewTemplate().withNewSpec()
                    .addNewContainer().withName("c").withImage("img").endContainer()
                .endSpec().endTemplate()
            .endSpec()
            .build();

        Job created = gateway.createJob(NS, job);

        assertNotNull(created);
        assertEquals("new-exec", created.getMetadata().getName());
    }

    // ─── countActivePods ───────────────────────────────────────────────────────

    @Test
    void countActivePodsShouldCountOnlyNonTerminalPods() {
        createPodWithPhase("pod-running", "my-exec", "Running");
        createPodWithPhase("pod-pending", "my-exec", "Pending");
        createPodWithPhase("pod-succeeded", "my-exec", "Succeeded");
        createPodWithPhase("pod-failed", "my-exec", "Failed");

        int count = gateway.countActivePods(NS, "my-exec");

        assertEquals(2, count); // Running + Pending
    }

    @Test
    void countActivePodsShouldReturnZeroWhenNoPods() {
        assertEquals(0, gateway.countActivePods(NS, "no-pods-job"));
    }

    @Test
    void readExecutionLogsShouldReturnEmptyWhenNoPods() {
        Map<String, String> logs = gateway.readExecutionLogs(NS, "no-pods-job", null);
        assertTrue(logs.isEmpty());
    }

    // ─── deleteActivePods ──────────────────────────────────────────────────────

    @Test
    void deleteActivePodsShouldDeleteOnlyNonTerminalPods() {
        createPodWithPhase("pod-running", "my-exec", "Running");
        createPodWithPhase("pod-succeeded", "my-exec", "Succeeded");

        int deleted = gateway.deleteActivePods(NS, "my-exec");

        assertEquals(1, deleted);
        assertNull(client.pods().inNamespace(NS).withName("pod-running").get());
        assertNotNull(client.pods().inNamespace(NS).withName("pod-succeeded").get());
    }

    @Test
    void deleteActivePodsWithNoPodsShouldReturnZero() {
        assertEquals(0, gateway.deleteActivePods(NS, "empty-job"));
    }

    // ─── hasIrrecoverablePodFailure ────────────────────────────────────────────

    @Test
    void hasIrrecoverablePodFailureShouldReturnFalseWhenNoPodsExist() {
        assertFalse(gateway.hasIrrecoverablePodFailure(NS, "no-pods-job"));
    }

    @Test
    void hasIrrecoverablePodFailureShouldReturnFalseForHealthyPod() {
        createPodWithPhase("pod-ok", "my-exec", "Running");
        assertFalse(gateway.hasIrrecoverablePodFailure(NS, "my-exec"));
    }

    @Test
    void hasIrrecoverablePodFailureShouldReturnTrueForImagePullBackOff() {
        createPodWithWaitingReason("pod-bad", "my-exec", "ImagePullBackOff");
        assertTrue(gateway.hasIrrecoverablePodFailure(NS, "my-exec"));
    }

    @Test
    void hasIrrecoverablePodFailureShouldReturnTrueForErrImagePull() {
        createPodWithWaitingReason("pod-bad", "my-exec", "ErrImagePull");
        assertTrue(gateway.hasIrrecoverablePodFailure(NS, "my-exec"));
    }

    @Test
    void hasIrrecoverablePodFailureShouldReturnTrueForInvalidImageName() {
        createPodWithWaitingReason("pod-bad", "my-exec", "InvalidImageName");
        assertTrue(gateway.hasIrrecoverablePodFailure(NS, "my-exec"));
    }

    @Test
    void hasIrrecoverablePodFailureShouldReturnTrueForCreateContainerConfigError() {
        createPodWithWaitingReason("pod-bad", "my-exec", "CreateContainerConfigError");
        assertTrue(gateway.hasIrrecoverablePodFailure(NS, "my-exec"));
    }

    @Test
    void hasIrrecoverablePodFailureShouldReturnFalseForRecoverableReason() {
        createPodWithWaitingReason("pod-transient", "my-exec", "ContainerCreating");
        assertFalse(gateway.hasIrrecoverablePodFailure(NS, "my-exec"));
    }

    @Test
    void hasIrrecoverablePodFailureShouldReturnTrueWhenReasonIsInPodStatusMessage() {
        Pod pod = new PodBuilder()
            .withNewMetadata()
                .withName("pod-msg").withNamespace(NS)
                .addToLabels("job-name", "msg-exec")
            .endMetadata()
            .withNewStatus()
                .withPhase("Pending")
                .withMessage("ImagePullBackOff: failed to pull image")
            .endStatus()
            .build();
        client.pods().inNamespace(NS).resource(pod).create();

        assertTrue(gateway.hasIrrecoverablePodFailure(NS, "msg-exec"));
    }

    // ─── waitForActivePodsToStop ───────────────────────────────────────────────

    @Test
    void waitForActivePodsShouldReturnZeroWhenNoPodsExist() {
        int remaining = gateway.waitForActivePodsToStop(NS, "no-pods", 1L, 3);
        assertEquals(0, remaining);
    }

    @Test
    void waitForActivePodsShouldReturnRemainingCountAfterMaxAttempts() {
        createPodWithPhase("pod-stuck", "stuck-exec", "Running");

        // 1ms poll, 2 attempts – pod never disappears → should return 1
        int remaining = gateway.waitForActivePodsToStop(NS, "stuck-exec", 1L, 2);
        assertEquals(1, remaining);
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private void createJob(String name) {
        Job job = new JobBuilder()
            .withNewMetadata().withName(name).withNamespace(NS).endMetadata()
            .withNewSpec()
                .withNewTemplate().withNewSpec()
                    .addNewContainer().withName("c").withImage("img").endContainer()
                .endSpec().endTemplate()
            .endSpec()
            .build();
        client.batch().v1().jobs().inNamespace(NS).resource(job).create();
    }

    private void createPodWithPhase(String podName, String jobName, String phase) {
        Pod pod = new PodBuilder()
            .withNewMetadata()
                .withName(podName).withNamespace(NS)
                .addToLabels("job-name", jobName)
            .endMetadata()
            .withNewStatus().withPhase(phase).endStatus()
            .build();
        client.pods().inNamespace(NS).resource(pod).create();
    }

    private void createPodWithWaitingReason(String podName, String jobName, String reason) {
        var containerStatus = new ContainerStatusBuilder()
            .withName("main")
            .withNewState()
                .withNewWaiting().withReason(reason).endWaiting()
            .endState()
            .build();
        Pod pod = new PodBuilder()
            .withNewMetadata()
                .withName(podName).withNamespace(NS)
                .addToLabels("job-name", jobName)
            .endMetadata()
            .withNewStatus()
                .withPhase("Pending")
                .addToContainerStatuses(containerStatus)
            .endStatus()
            .build();
        client.pods().inNamespace(NS).resource(pod).create();
    }

    private static void assertNull(Object obj) {
        org.junit.jupiter.api.Assertions.assertNull(obj);
    }

    private static void assertNotNull(Object obj) {
        org.junit.jupiter.api.Assertions.assertNotNull(obj);
    }
}
