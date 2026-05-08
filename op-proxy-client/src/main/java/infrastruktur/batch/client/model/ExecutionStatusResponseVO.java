package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionStatusResponseVO(
    String namespace,
    String templateName,
    String executionName,
    String phase,
    int activePods,
    int succeededPods,
    int failedPods,
    Long elapsedSeconds
) {}
