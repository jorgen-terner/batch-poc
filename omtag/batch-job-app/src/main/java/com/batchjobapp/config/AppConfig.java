package com.batchjobapp.config;

import com.batchjobapp.store.JobReportStore;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
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
            kubernetesClient = new DefaultKubernetesClient(Config.autoConfigure(null));
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
