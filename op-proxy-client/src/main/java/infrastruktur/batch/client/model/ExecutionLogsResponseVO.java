package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionLogsResponseVO(
    String namespace,
    String templateName,
    String executionName,
    Integer tailLines,
    Map<String, String> logsByPod
) {}
