package infrastruktur.batch.model;

import java.util.List;

public record CreateRunRequestVO(
    String clientRequestId,
    Long timeoutSeconds,
    List<JobParameterVO> parameters
) {}
