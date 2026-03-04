package com.example.triggerjobapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStartRequest {

    @NotBlank(message = "jobName is required")
    private String jobName;

    @NotBlank(message = "image is required")
    private String image;

    private List<String> command;

    private Map<String, String> env;

    private String imagePullPolicy;

    private int ttlSecondsAfterFinished;

    private Integer parallelism;

    private Integer completions;
}
