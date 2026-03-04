package com.example.triggerjobapi.service;

import com.example.triggerjobapi.dto.JobStartRequest;
import com.example.triggerjobapi.dto.JobStatusResponse;
import com.example.triggerjobapi.exception.JobException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class KubernetesJobService {

    @Value("${kubernetes.namespace:default}")
    private String namespace;

    @Value("${kubernetes.job-ttl-seconds:86400}")
    private int defaultTtlSeconds;

    private final BatchV1Api batchV1Api;
    private final CoreV1Api coreV1Api;

    public KubernetesJobService(BatchV1Api batchV1Api, CoreV1Api coreV1Api) {
        this.batchV1Api = batchV1Api;
        this.coreV1Api = coreV1Api;
    }

    /**
     * Starta ett nytt Kubernetes Job
     */
    public String startJob(JobStartRequest request) {
        try {
            V1Job job = buildJob(request);
            V1Job createdJob = batchV1Api.createNamespacedJob(namespace, job, null, null, null, null);
            return createdJob.getMetadata().getName();
        } catch (ApiException e) {
            throw new JobException("Misslyckades att starta Job: " + e.getMessage(), e);
        }
    }

    /**
     * Konstruera ett Kubernetes Job-objekt
     */
    private V1Job buildJob(JobStartRequest request) {
        String jobName = generateJobName(request.getJobName());

        V1Job job = new V1Job();
        job.setApiVersion("batch/v1");
        job.setKind("Job");

        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(jobName);
        metadata.setNamespace(namespace);
        job.setMetadata(metadata);

        V1JobSpec spec = new V1JobSpec();
        spec.setTtlSecondsAfterFinished(request.getTtlSecondsAfterFinished() > 0 
            ? request.getTtlSecondsAfterFinished() 
            : defaultTtlSeconds);
        spec.setBackoffLimit(3);

        V1PodTemplateSpec podTemplate = new V1PodTemplateSpec();
        V1ObjectMeta podMetadata = new V1ObjectMeta();
        podMetadata.setName(jobName);
        podTemplate.setMetadata(podMetadata);

        V1PodSpec podSpec = new V1PodSpec();
        podSpec.setRestartPolicy("Never");

        V1Container container = new V1Container();
        container.setName(jobName);
        container.setImage(request.getImage());
        container.setImagePullPolicy(request.getImagePullPolicy() != null 
            ? request.getImagePullPolicy() 
            : "IfNotPresent");

        if (request.getCommand() != null && !request.getCommand().isEmpty()) {
            container.setCommand(request.getCommand());
        }

        if (request.getEnv() != null && !request.getEnv().isEmpty()) {
            List<V1EnvVar> envVars = new ArrayList<>();
            request.getEnv().forEach((key, value) -> {
                V1EnvVar envVar = new V1EnvVar();
                envVar.setName(key);
                envVar.setValue(value);
                envVars.add(envVar);
            });
            container.setEnv(envVars);
        }

        podSpec.setContainers(List.of(container));
        podTemplate.setSpec(podSpec);

        spec.setTemplate(podTemplate);
        job.setSpec(spec);

        return job;
    }

    /**
     * Generera ett unikt Job-namn
     */
    private String generateJobName(String baseName) {
        long timestamp = System.currentTimeMillis();
        return baseName.toLowerCase().replaceAll("[^a-z0-9]", "-") 
            + "-" + (timestamp % 10000);
    }

    /**
     * Hämta status för ett specifikt Job
     */
    public JobStatusResponse getJobStatus(String jobId) {
        try {
            V1Job job = batchV1Api.readNamespacedJob(jobId, namespace, null, null, null);
            return mapJobToResponse(job);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new JobException("Job hittades inte: " + jobId);
            }
            throw new JobException("Misslyckades att hämta Job-status: " + e.getMessage(), e);
        }
    }

    /**
     * Lista alla aktiva Jobs
     */
    public List<JobStatusResponse> listJobs() {
        try {
            V1JobList jobList = batchV1Api.listNamespacedJob(namespace, null, null, null, null, 
                null, null, null, null, null, null);
            
            List<JobStatusResponse> response = new ArrayList<>();
            if (jobList.getItems() != null) {
                jobList.getItems().forEach(job -> response.add(mapJobToResponse(job)));
            }
            return response;
        } catch (ApiException e) {
            throw new JobException("Misslyckades att lista Jobs: " + e.getMessage(), e);
        }
    }

    /**
     * Ta bort ett Job
     */
    public void deleteJob(String jobId) {
        try {
            batchV1Api.deleteNamespacedJob(jobId, namespace, null, null, null, null, 
                "Foreground", null);
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                throw new JobException("Job hittades inte: " + jobId);
            }
            throw new JobException("Misslyckades att ta bort Job: " + e.getMessage(), e);
        }
    }

    /**
     * Konvertera V1Job till JobStatusResponse
     */
    private JobStatusResponse mapJobToResponse(V1Job job) {
        V1JobStatus status = job.getStatus();
        String jobStatus = "PENDING";

        if (status != null) {
            if (status.getSucceeded() != null && status.getSucceeded() > 0) {
                jobStatus = "COMPLETED";
            } else if (status.getFailed() != null && status.getFailed() > 0) {
                jobStatus = "FAILED";
            } else if (status.getActive() != null && status.getActive() > 0) {
                jobStatus = "RUNNING";
            }
        }

        String image = "N/A";
        if (job.getSpec() != null && job.getSpec().getTemplate() != null 
            && job.getSpec().getTemplate().getSpec() != null 
            && !job.getSpec().getTemplate().getSpec().getContainers().isEmpty()) {
            image = job.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        }

        return JobStatusResponse.builder()
            .jobId(job.getMetadata().getName())
            .jobName(job.getMetadata().getName())
            .image(image)
            .status(jobStatus)
            .completions(status != null ? status.getSucceeded() : null)
            .parallelism(job.getSpec().getParallelism())
            .createdAt(convertToLocalDateTime(job.getMetadata().getCreationTimestamp()))
            .startTime(status != null ? convertToLocalDateTime(status.getStartTime()) : null)
            .completionTime(status != null ? convertToLocalDateTime(status.getCompletionTime()) : null)
            .build();
    }

    private LocalDateTime convertToLocalDateTime(java.time.OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.toLocalDateTime();
    }
}
