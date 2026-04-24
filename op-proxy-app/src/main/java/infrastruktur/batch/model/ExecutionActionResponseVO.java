package infrastruktur.batch.model;

import java.time.Instant;

public record ExecutionActionResponseVO(
    String namespace,
    String templateName,
    String executionName,
    String clientRequestId,
    String action,
    String state,
    String message,
    Instant createdAt
) {}
