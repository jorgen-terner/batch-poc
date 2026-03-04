package com.example.triggerjobapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobStatusResponse {

    private String jobId;
    private String jobName;
    private String image;
    private String status;  // RUNNING, COMPLETED, FAILED, PENDING
    private Integer completions;
    private Integer parallelism;
    private LocalDateTime createdAt;
    private LocalDateTime startTime;
    private LocalDateTime completionTime;
    private String message;
}
