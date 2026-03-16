package com.example.infbatchjob.service.monitoring;

import java.time.Instant;

public record BatchMetricsContext(
    String jobId,
    String object,
    String chart,
    String environment,
    String user,
    String server,
    Instant startTime,
    int pid
) {
}
