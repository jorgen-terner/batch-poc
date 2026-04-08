package infrastruktur.batch.model;

import java.util.List;

public record StartJobRequestVO(
    Long timeoutSeconds,
    List<JobParameterVO> parameters
) {}