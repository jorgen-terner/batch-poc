package infrastruktur.batch.model;

import java.time.Instant;

public record ExecutionStatusResponseVO(
    String namespace,
    String templateName,
    String executionName,
    String phase,
    int activePods,
    int succeededPods,
    int failedPods,
    Instant startTime,
    Instant completionTime,
    Long elapsedSeconds
) {}
