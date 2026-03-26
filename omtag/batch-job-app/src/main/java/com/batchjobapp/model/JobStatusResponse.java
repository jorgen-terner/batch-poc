package com.batchjobapp.model;

import java.time.Instant;

public record JobStatusResponse(
    String namespace,
    String jobName,
    String phase,
    boolean suspended,
    int activePods,
    int succeededPods,
    int failedPods,
    Instant startTime,
    Instant completionTime,
    Long elapsedSeconds,
    JobReportSnapshot latestReport
) {}
