package infrastruktur.batch.model;

import java.util.List;

public record RestartJobRequestVO(
    Long timeoutSeconds,
    Boolean keepFailedPods,
    List<JobParameterVO> parameters
) {}