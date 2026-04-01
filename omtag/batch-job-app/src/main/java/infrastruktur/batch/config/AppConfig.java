package infrastruktur.batch.config;

import infrastruktur.batch.store.JobReportStore;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class AppConfig {
    private KubernetesClient kubernetesClient;

    @Produces
    @ApplicationScoped
    public KubernetesClient kubernetesClient() {
        if (kubernetesClient == null) {
            kubernetesClient = new KubernetesClientBuilder().build();
        }
        return kubernetesClient;
    }

    @Produces
    @ApplicationScoped
    public JobReportStore jobReportStore() {
        return new JobReportStore();
    }

    @PreDestroy
    void onShutdown() {
        if (kubernetesClient != null) {
            kubernetesClient.close();
        }
    }
}
