package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionActionResponseVO(
    String namespace,
    String templateName,
    String executionName,
    String clientRequestId,
    String action,
    String state,
    String message
) {}
