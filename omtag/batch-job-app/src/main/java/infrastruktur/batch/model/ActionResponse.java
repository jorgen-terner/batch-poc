package infrastruktur.batch.model;

import java.time.Instant;

public record ActionResponse(
    String namespace,
    String jobName,
    String action,
    String state,
    String message,
    Instant timestamp
) {}
