package infrastruktur.batch.model;

import java.util.List;

public record StartExecutionRequestVO(
    String clientRequestId,
    Long timeoutSeconds,
    List<JobParameterVO> parameters
) {}
