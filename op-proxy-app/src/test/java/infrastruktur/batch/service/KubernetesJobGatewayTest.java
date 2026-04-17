package infrastruktur.batch.service;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubernetesJobGatewayTest {

    @Test
    void createRestartedJobRemovesGeneratedSelectorAndTemplateLabels() {
        KubernetesJobGateway gateway = new KubernetesJobGateway(null);
        Job source = sourceJob();

        Job recreated = gateway.createRestartedJob(source, 1200L, Map.of("executionId", "abc-123"));

        assertNull(recreated.getSpec().getSelector());
        assertNull(recreated.getSpec().getManualSelector());
        assertTrue(Boolean.FALSE.equals(recreated.getSpec().getSuspend()));
        assertEquals(1200L, recreated.getSpec().getActiveDeadlineSeconds());

        Map<String, String> labels = recreated.getSpec().getTemplate().getMetadata().getLabels();
        assertEquals("inv-javabatch", labels.get("app"));
        assertFalse(labels.containsKey("controller-uid"));
        assertFalse(labels.containsKey("batch.kubernetes.io/controller-uid"));
        assertFalse(labels.containsKey("job-name"));
        assertFalse(labels.containsKey("batch.kubernetes.io/job-name"));

        List<EnvVar> env = recreated.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        assertEquals(2, env.size());
        assertEquals("abc-123", env.stream()
            .filter(item -> "executionId".equals(item.getName()))
            .findFirst()
            .orElseThrow()
            .getValue());
    }

    private Job sourceJob() {
        return new JobBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withName("inv-javabatch-suspended")
                .withNamespace("dev252")
                .withResourceVersion("123")
                .withUid("old-uid")
                .build())
            .withSpec(new JobSpecBuilder()
                .withSuspend(true)
                .withSelector(new io.fabric8.kubernetes.api.model.LabelSelector(null, Map.of(
                    "batch.kubernetes.io/controller-uid", "old-controller"
                )))
                .withTemplate(new PodTemplateSpecBuilder()
                    .withMetadata(new ObjectMetaBuilder()
                        .withLabels(Map.of(
                            "app", "inv-javabatch",
                            "controller-uid", "old-controller",
                            "batch.kubernetes.io/controller-uid", "old-controller",
                            "job-name", "inv-javabatch-suspended",
                            "batch.kubernetes.io/job-name", "inv-javabatch-suspended"
                        ))
                        .build())
                    .withSpec(new PodSpecBuilder()
                        .addNewContainer()
                        .withName("inv-javabatch")
                        .withImage("example")
                        .withEnv(new EnvVarBuilder().withName("runType").withValue("FULL").build())
                        .endContainer()
                        .build())
                    .build())
                .build())
            .build();
    }
}