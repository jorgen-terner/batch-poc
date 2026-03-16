package com.example.infbatchjob.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;

public class JobStartRequest {

    private String jobName;

    @NotBlank(message = "configMapName is required")
    private String configMapName;

    // Optional: Om angiven överskrids ConfigMap-värden
    private String image;

    private List<String> command;

    private Map<String, String> env;

    private String imagePullPolicy;

    private int ttlSecondsAfterFinished;

    private Integer parallelism;

    private Integer completions;

    public JobStartRequest() {
    }

    public JobStartRequest(String jobName, String image, List<String> command, 
                          Map<String, String> env, String configMapName,
                          String imagePullPolicy, int ttlSecondsAfterFinished, 
                          Integer parallelism, Integer completions) {
        this.jobName = jobName;
        this.image = image;
        this.command = command;
        this.env = env;
        this.configMapName = configMapName;
        this.imagePullPolicy = imagePullPolicy;
        this.ttlSecondsAfterFinished = ttlSecondsAfterFinished;
        this.parallelism = parallelism;
        this.completions = completions;
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

    public List<String> getCommand() {
        return command;
    }

    public void setCommand(List<String> command) {
        this.command = command;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public String getConfigMapName() {
        return configMapName;
    }

    public void setConfigMapName(String configMapName) {
        this.configMapName = configMapName;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public void setImagePullPolicy(String imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

    public int getTtlSecondsAfterFinished() {
        return ttlSecondsAfterFinished;
    }

    public void setTtlSecondsAfterFinished(int ttlSecondsAfterFinished) {
        this.ttlSecondsAfterFinished = ttlSecondsAfterFinished;
    }

    public Integer getParallelism() {
        return parallelism;
    }

    public void setParallelism(Integer parallelism) {
        this.parallelism = parallelism;
    }

    public Integer getCompletions() {
        return completions;
    }

    public void setCompletions(Integer completions) {
        this.completions = completions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String jobName;
        private String image;
        private List<String> command;
        private Map<String, String> env;
        private String configMapName;
        private String imagePullPolicy;
        private int ttlSecondsAfterFinished;
        private Integer parallelism;
        private Integer completions;

        public Builder jobName(String jobName) {
            this.jobName = jobName;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public Builder command(List<String> command) {
            this.command = command;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder configMapName(String configMapName) {
            this.configMapName = configMapName;
            return this;
        }

        public Builder imagePullPolicy(String imagePullPolicy) {
            this.imagePullPolicy = imagePullPolicy;
            return this;
        }

        public Builder ttlSecondsAfterFinished(int ttlSecondsAfterFinished) {
            this.ttlSecondsAfterFinished = ttlSecondsAfterFinished;
            return this;
        }

        public Builder parallelism(Integer parallelism) {
            this.parallelism = parallelism;
            return this;
        }

        public Builder completions(Integer completions) {
            this.completions = completions;
            return this;
        }

        public JobStartRequest build() {
            return new JobStartRequest(jobName, image, command, env, 
                configMapName, imagePullPolicy, ttlSecondsAfterFinished, 
                parallelism, completions);
        }
    }
}
