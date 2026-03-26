package com.batchjobapp.model;

import java.time.Instant;
import java.util.Map;

public record JobMetricsResponse(
    String namespace,
    String jobName,
    int podCount,
    int totalContainerRestarts,
    Integer lastExitCode,
    Instant startTime,
    Instant completionTime,
    Long elapsedSeconds,
    String reportedStatus,
    Map<String, Double> reportedMetrics,
    Map<String, String> reportedAttributes
) {}
