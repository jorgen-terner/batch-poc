package com.example.infbatchjob.controller;

import com.example.infbatchjob.service.ConfigMapService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Admin endpoints för cache-management och diagnostik
 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminController {

    @Inject
    ConfigMapService configMapService;

    /**
     * Hämta cache-status
     */
    @GET
    @Path("/cache/status")
    public Response getCacheStatus() {
        int cacheSize = configMapService.getCacheSize();
        return Response.ok(Map.of(
            "cacheSize", cacheSize,
            "message", "ConfigMap cache innehåller " + cacheSize + " entries"
        )).build();
    }

    /**
     * Rensa hela cachen
     */
    @DELETE
    @Path("/cache")
    public Response clearCache() {
        configMapService.clearCache();
        return Response.ok(Map.of(
            "message", "Cache rensad"
        )).build();
    }

    /**
     * Rensa cache för specifik ConfigMap
     */
    @DELETE
    @Path("/cache/{configMapName}")
    public Response clearCacheForConfigMap(@PathParam("configMapName") String configMapName) {
        configMapService.clearCache(configMapName);
        return Response.ok(Map.of(
            "message", "Cache rensad för ConfigMap: " + configMapName
        )).build();
    }
}
