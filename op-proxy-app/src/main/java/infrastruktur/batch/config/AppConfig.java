package infrastruktur.batch.config;

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
        kubernetesClient = new KubernetesClientBuilder().build();
        return kubernetesClient;
    }

    @PreDestroy
    void onShutdown() {
        if (kubernetesClient != null) {
            kubernetesClient.close();
        }
    }
}
