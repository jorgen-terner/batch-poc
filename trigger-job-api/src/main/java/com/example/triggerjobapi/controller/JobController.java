package com.example.triggerjobapi.controller;

import com.example.triggerjobapi.dto.JobStartRequest;
import com.example.triggerjobapi.dto.JobStartResponse;
import com.example.triggerjobapi.dto.JobStatusResponse;
import com.example.triggerjobapi.exception.JobException;
import com.example.triggerjobapi.service.KubernetesJobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final KubernetesJobService jobService;

    public JobController(KubernetesJobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Starta ett nytt Job
     */
    @PostMapping
    public ResponseEntity<JobStartResponse> startJob(@Valid @RequestBody JobStartRequest request) {
        try {
            String jobId = jobService.startJob(request);
            JobStartResponse response = JobStartResponse.builder()
                .jobId(jobId)
                .jobName(request.getJobName())
                .message("Job startad framgångsrikt")
                .success(true)
                .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (JobException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(JobStartResponse.builder()
                    .message(e.getMessage())
                    .success(false)
                    .build());
        }
    }

    /**
     * Hämta status för ett Job
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        try {
            JobStatusResponse response = jobService.getJobStatus(jobId);
            return ResponseEntity.ok(response);
        } catch (JobException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lista alla Jobs
     */
    @GetMapping
    public ResponseEntity<List<JobStatusResponse>> listJobs() {
        List<JobStatusResponse> jobs = jobService.listJobs();
        return ResponseEntity.ok(jobs);
    }

    /**
     * Ta bort/stoppa ett Job
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<?> deleteJob(@PathVariable String jobId) {
        try {
            jobService.deleteJob(jobId);
            return ResponseEntity.ok(Map.of("message", "Job raderat framgångsrikt"));
        } catch (JobException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
