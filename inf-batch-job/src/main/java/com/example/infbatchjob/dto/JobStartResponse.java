package com.example.infbatchjob.dto;

public class JobStartResponse {

    private String jobId;
    private String jobName;
    private String message;
    private boolean success;

    public JobStartResponse() {
    }

    public JobStartResponse(String jobId, String jobName, String message, boolean success) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.message = message;
        this.success = success;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String jobId;
        private String jobName;
        private String message;
        private boolean success;

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder jobName(String jobName) {
            this.jobName = jobName;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public JobStartResponse build() {
            return new JobStartResponse(jobId, jobName, message, success);
        }
    }
}
