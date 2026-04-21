package infrastruktur.batch.model;

import java.time.Instant;

public record RunActionResponseVO(
    String namespace,
    String templateName,
    String runName,
    String clientRequestId,
    String action,
    String state,
    String message,
    Instant createdAt
) {}
