package infrastruktur.batch.service;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobConditionBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobPhaseResolverTest {

    private JobPhaseResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new JobPhaseResolver();
    }

    @Test
    void suspendedBeforeStartShouldBeSuspended() {
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(true).endSpec()
            .withNewStatus().endStatus()
            .build();
        String phase = resolver.resolvePhase(job, 0, 0, 0, true);
        assertEquals("SUSPENDED", phase);
    }

    @Test
    void activePodsRunningMeansPending() {
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(false).endSpec()
            .withNewStatus().withStartTime("2025-01-01T10:00:00Z").endStatus()
            .build();
        String phase = resolver.resolvePhase(job, 1, 0, 0, false);
        assertEquals("RUNNING", phase);
    }

    @Test
    void completeConditionMeansSucceeded() {
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(false).endSpec()
            .withNewStatus()
                .withStartTime("2025-01-01T10:00:00Z")
                .addToConditions(new JobConditionBuilder()
                    .withType("Complete")
                    .withStatus("True")
                    .build())
            .endStatus()
            .build();
        String phase = resolver.resolvePhase(job, 0, 0, 0, false);
        assertEquals("SUCCEEDED", phase);
    }

    @Test
    void failedConditionMeansFailed() {
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(false).endSpec()
            .withNewStatus()
                .withStartTime("2025-01-01T10:00:00Z")
                .addToConditions(new JobConditionBuilder()
                    .withType("Failed")
                    .withStatus("True")
                    .build())
            .endStatus()
            .build();
        String phase = resolver.resolvePhase(job, 0, 0, 0, false);
        assertEquals("FAILED", phase);
    }

    @Test
    void succeededCountWithoutConditionMeansSucceeded() {
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(false).endSpec()
            .withNewStatus().withStartTime("2025-01-01T10:00:00Z").endStatus()
            .build();
        String phase = resolver.resolvePhase(job, 0, 1, 0, false);
        assertEquals("SUCCEEDED", phase);
    }

    @Test
    void failedCountWithoutConditionMeansFailed() {
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(false).endSpec()
            .withNewStatus().withStartTime("2025-01-01T10:00:00Z").endStatus()
            .build();
        String phase = resolver.resolvePhase(job, 0, 0, 1, false);
        assertEquals("FAILED", phase);
    }

    @Test
    void noActivityNoConditionsMeansPending() {
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(false).endSpec()
            .withNewStatus().endStatus()
            .build();
        String phase = resolver.resolvePhase(job, 0, 0, 0, false);
        assertEquals("PENDING", phase);
    }

    @Test
    void suspendedAfterStartMeansSuspended() {
        // Suspended but startTime was set means job was running before suspension
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(true).endSpec()
            .withNewStatus().withStartTime("2025-01-01T10:00:00Z").endStatus()
            .build();
        String phase = resolver.resolvePhase(job, 0, 0, 0, true);
        assertEquals("SUSPENDED", phase);
    }

    @Test
    void resolvePhaseFromJobObjectPicksUpSuspendFromSpec() {
        Job job = new JobBuilder()
            .withNewSpec().withSuspend(false).endSpec()
            .withNewStatus()
                .withActive(1)
            .endStatus()
            .build();
        String phase = resolver.resolvePhase(job);
        assertEquals("RUNNING", phase);
    }

    @Test
    void isTerminalPhaseReturnsTrueForSucceeded() {
        assertTrue(resolver.isTerminalPhase("SUCCEEDED"));
    }

    @Test
    void isTerminalPhaseReturnsTrueForFailed() {
        assertTrue(resolver.isTerminalPhase("FAILED"));
    }

    @Test
    void isTerminalPhaseReturnsFalseForRunning() {
        assertFalse(resolver.isTerminalPhase("RUNNING"));
    }

    @Test
    void isTerminalPhaseReturnsFalseForPending() {
        assertFalse(resolver.isTerminalPhase("PENDING"));
    }

    @Test
    void normalizeUppercasesAndTrimsValue() {
        assertEquals("SUCCEEDED", JobPhaseResolver.normalize("  succeeded  "));
    }

    @Test
    void normalizeNullReturnsUnknown() {
        assertEquals("UNKNOWN", JobPhaseResolver.normalize(null));
    }
}
