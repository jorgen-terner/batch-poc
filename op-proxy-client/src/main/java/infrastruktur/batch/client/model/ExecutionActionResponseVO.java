package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionActionResponseVO {
    private final String namespace;
    private final String templateName;
    private final String executionName;
    private final String clientRequestId;
    private final String action;
    private final String state;
    private final String message;

    @JsonCreator
    public ExecutionActionResponseVO(
            @JsonProperty("namespace") String namespace,
            @JsonProperty("templateName") String templateName,
            @JsonProperty("executionName") String executionName,
            @JsonProperty("clientRequestId") String clientRequestId,
            @JsonProperty("action") String action,
            @JsonProperty("state") String state,
            @JsonProperty("message") String message) {
        this.namespace = namespace;
        this.templateName = templateName;
        this.executionName = executionName;
        this.clientRequestId = clientRequestId;
        this.action = action;
        this.state = state;
        this.message = message;
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

    public String clientRequestId() {
        return clientRequestId;
    }

    public String action() {
        return action;
    }

    public String state() {
        return state;
    }

    public String message() {
        return message;
    }
}
