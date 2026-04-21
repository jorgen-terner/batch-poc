package infrastruktur.batch.model;

import java.time.Instant;

public record RunStatusResponseVO(
    String namespace,
    String templateName,
    String runName,
    String phase,
    int activePods,
    int succeededPods,
    int failedPods,
    Instant startTime,
    Instant completionTime,
    Long elapsedSeconds
) {}
