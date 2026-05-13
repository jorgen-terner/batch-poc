package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StartExecutionRequestVO {
    private final String clientRequestId;
    private final Long timeoutSeconds;
    private final List<JobParameterVO> parameters;

    @JsonCreator
    public StartExecutionRequestVO(
            @JsonProperty("clientRequestId") String clientRequestId,
            @JsonProperty("timeoutSeconds") Long timeoutSeconds,
            @JsonProperty("parameters") List<JobParameterVO> parameters) {
        this.clientRequestId = clientRequestId;
        this.timeoutSeconds = timeoutSeconds;
        this.parameters = parameters;
    }

    public String clientRequestId() {
        return clientRequestId;
    }

    public Long timeoutSeconds() {
        return timeoutSeconds;
    }

    public List<JobParameterVO> parameters() {
        return parameters;
    }
}
