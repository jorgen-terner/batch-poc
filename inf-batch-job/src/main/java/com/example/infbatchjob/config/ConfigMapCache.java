package com.example.infbatchjob.config;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cache-entry för ConfigMap
 */
public class ConfigMapCache {
    
    private final String configMapName;
    private final String resourceVersion;
    private final Map<String, String> data;
    private final LocalDateTime cachedAt;

    public ConfigMapCache(String configMapName, String resourceVersion, 
                         Map<String, String> data) {
        this.configMapName = configMapName;
        this.resourceVersion = resourceVersion;
        this.data = data;
        this.cachedAt = LocalDateTime.now();
    }

    public String getConfigMapName() {
        return configMapName;
    }

    public String getResourceVersion() {
        return resourceVersion;
    }

    public Map<String, String> getData() {
        return data;
    }

    public LocalDateTime getCachedAt() {
        return cachedAt;
    }

    /**
     * Kontrollera om denna cache är giltig för given resourceVersion
     */
    public boolean isValid(String currentResourceVersion) {
        return this.resourceVersion != null && 
               this.resourceVersion.equals(currentResourceVersion);
    }
}
