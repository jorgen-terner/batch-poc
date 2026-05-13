package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionLogsResponseVO {
    private final String namespace;
    private final String templateName;
    private final String executionName;
    private final Integer tailLines;
    private final Map<String, String> logsByPod;

    @JsonCreator
    public ExecutionLogsResponseVO(
            @JsonProperty("namespace") String namespace,
            @JsonProperty("templateName") String templateName,
            @JsonProperty("executionName") String executionName,
            @JsonProperty("tailLines") Integer tailLines,
            @JsonProperty("logsByPod") Map<String, String> logsByPod) {
        this.namespace = namespace;
        this.templateName = templateName;
        this.executionName = executionName;
        this.tailLines = tailLines;
        this.logsByPod = logsByPod;
    }

    public String namespace() {
        return namespace;
    }

    public String templateName() {
        return templateName;
    }

    public String executionName() {
        return executionName;
    }

    public Integer tailLines() {
        return tailLines;
    }

    public Map<String, String> logsByPod() {
        return logsByPod;
    }
}
