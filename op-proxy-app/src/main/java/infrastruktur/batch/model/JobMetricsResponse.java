package infrastruktur.batch.model;

import java.time.Instant;

public record JobMetricsResponse(
    String namespace,
    String jobName,
    String phase,
    int podCount,
    int totalContainerRestarts,
    Integer lastExitCode,
    Instant startTime,
    Instant completionTime,
    Long elapsedSeconds
) {}
