package com.example.triggerjobapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStartResponse {

    private String jobId;
    private String jobName;
    private String message;
    private boolean success;
}
