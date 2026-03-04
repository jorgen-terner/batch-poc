package com.example.triggerjobapi.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class KubernetesConfig {

    @Bean
    public ApiClient kubernetesApiClient() throws IOException {
        ApiClient client = Config.defaultClient();
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        return client;
    }

    @Bean
    public BatchV1Api batchV1Api(ApiClient apiClient) {
        return new BatchV1Api(apiClient);
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }
}
