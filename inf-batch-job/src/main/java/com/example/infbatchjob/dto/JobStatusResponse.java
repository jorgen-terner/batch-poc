package com.example.infbatchjob.dto;

import java.time.LocalDateTime;

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

    public JobStatusResponse() {
    }

    public JobStatusResponse(String jobId, String jobName, String image, String status,
                           Integer completions, Integer parallelism, LocalDateTime createdAt,
                           LocalDateTime startTime, LocalDateTime completionTime, String message) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.image = image;
        this.status = status;
        this.completions = completions;
        this.parallelism = parallelism;
        this.createdAt = createdAt;
        this.startTime = startTime;
        this.completionTime = completionTime;
        this.message = message;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCompletions() {
        return completions;
    }

    public void setCompletions(Integer completions) {
        this.completions = completions;
    }

    public Integer getParallelism() {
        return parallelism;
    }

    public void setParallelism(Integer parallelism) {
        this.parallelism = parallelism;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(LocalDateTime completionTime) {
        this.completionTime = completionTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String jobId;
        private String jobName;
        private String image;
        private String status;
        private Integer completions;
        private Integer parallelism;
        private LocalDateTime createdAt;
        private LocalDateTime startTime;
        private LocalDateTime completionTime;
        private String message;

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder jobName(String jobName) {
            this.jobName = jobName;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder completions(Integer completions) {
            this.completions = completions;
            return this;
        }

        public Builder parallelism(Integer parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder completionTime(LocalDateTime completionTime) {
            this.completionTime = completionTime;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public JobStatusResponse build() {
            return new JobStatusResponse(jobId, jobName, image, status,
                completions, parallelism, createdAt, startTime, completionTime, message);
        }
    }
}
