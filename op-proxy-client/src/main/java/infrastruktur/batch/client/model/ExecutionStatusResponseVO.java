package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionStatusResponseVO {
    private final String namespace;
    private final String templateName;
    private final String executionName;
    private final String phase;
    private final int activePods;
    private final int succeededPods;
    private final int failedPods;
    private final Long elapsedSeconds;

    @JsonCreator
    public ExecutionStatusResponseVO(
            @JsonProperty("namespace") String namespace,
            @JsonProperty("templateName") String templateName,
            @JsonProperty("executionName") String executionName,
            @JsonProperty("phase") String phase,
            @JsonProperty("activePods") int activePods,
            @JsonProperty("succeededPods") int succeededPods,
            @JsonProperty("failedPods") int failedPods,
            @JsonProperty("elapsedSeconds") Long elapsedSeconds) {
        this.namespace = namespace;
        this.templateName = templateName;
        this.executionName = executionName;
        this.phase = phase;
        this.activePods = activePods;
        this.succeededPods = succeededPods;
        this.failedPods = failedPods;
        this.elapsedSeconds = elapsedSeconds;
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

    public String phase() {
        return phase;
    }

    public int activePods() {
        return activePods;
    }

    public int succeededPods() {
        return succeededPods;
    }

    public int failedPods() {
        return failedPods;
    }

    public Long elapsedSeconds() {
        return elapsedSeconds;
    }
}
