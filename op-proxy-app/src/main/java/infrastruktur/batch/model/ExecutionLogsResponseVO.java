package infrastruktur.batch.model;

import java.util.Map;

public record ExecutionLogsResponseVO(
    String namespace,
    String templateName,
    String executionName,
    Integer tailLines,
    Map<String, String> logsByPod
) {}