package com.example.infbatchjob.service;

import com.example.infbatchjob.config.ConfigMapCache;
import com.example.infbatchjob.exception.JobException;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service för att läsa och cacha ConfigMaps
 */
@ApplicationScoped
public class ConfigMapService {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigMapService.class);

    @ConfigProperty(name = "kubernetes.namespace", defaultValue = "default")
    String namespace;

    @Inject
    CoreV1Api coreV1Api;

    // Cache: configMapName -> ConfigMapCache
    private final Map<String, ConfigMapCache> cache = new ConcurrentHashMap<>();

    /**
     * Läs ConfigMap med caching.
     * Om ConfigMap redan är cachad och resourceVersion är samma, returnera från cache.
     * Annars läs från Kubernetes API och uppdatera cache.
     */
    public Map<String, String> getConfigMapData(String configMapName) {
        try {
            // Läs ConfigMap från Kubernetes
            V1ConfigMap configMap = coreV1Api.readNamespacedConfigMap(
                configMapName, namespace, null);

            if (configMap == null || configMap.getData() == null) {
                throw new JobException("ConfigMap '" + configMapName + "' finns inte eller har ingen data");
            }

            String resourceVersion = configMap.getMetadata() != null 
                ? configMap.getMetadata().getResourceVersion() 
                : null;

            // Kontrollera om vi har giltig cache
            ConfigMapCache cached = cache.get(configMapName);
            if (cached != null && cached.isValid(resourceVersion)) {
                LOG.debug("Using cached ConfigMap: {} (resourceVersion: {})", 
                         configMapName, resourceVersion);
                return cached.getData();
            }

            // Cache miss eller utdaterad - uppdatera cache
            LOG.info("Loading ConfigMap: {} (resourceVersion: {})", 
                    configMapName, resourceVersion);
            ConfigMapCache newCache = new ConfigMapCache(
                configMapName, resourceVersion, configMap.getData());
            cache.put(configMapName, newCache);

            return configMap.getData();

        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new JobException("ConfigMap '" + configMapName + "' hittades inte i namespace '" + namespace + "'");
            }
            throw new JobException("Misslyckades att läsa ConfigMap '" + configMapName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Hämta specifikt värde från ConfigMap
     */
    public String getConfigMapValue(String configMapName, String key) {
        Map<String, String> data = getConfigMapData(configMapName);
        return data.get(key);
    }

    /**
     * Hämta specifikt värde med default
     */
    public String getConfigMapValue(String configMapName, String key, String defaultValue) {
        String value = getConfigMapValue(configMapName, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Rensa cache (användbart för testing/debugging)
     */
    public void clearCache() {
        cache.clear();
        LOG.info("ConfigMap cache cleared");
    }

    /**
     * Rensa cache för specifik ConfigMap
     */
    public void clearCache(String configMapName) {
        cache.remove(configMapName);
        LOG.info("Cleared cache for ConfigMap: {}", configMapName);
    }

    /**
     * Hämta cache-status
     */
    public int getCacheSize() {
        return cache.size();
    }
}
