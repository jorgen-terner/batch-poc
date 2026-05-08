package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record StartExecutionRequestVO(
    String clientRequestId,
    Long timeoutSeconds,
    List<JobParameterVO> parameters
) {}
