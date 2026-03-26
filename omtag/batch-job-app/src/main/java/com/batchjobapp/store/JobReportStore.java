package com.batchjobapp.store;

import com.batchjobapp.model.JobReportRequest;
import com.batchjobapp.model.JobReportSnapshot;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JobReportStore {
    private final ConcurrentMap<String, JobReportSnapshot> latestReports = new ConcurrentHashMap<>();

    public JobReportSnapshot put(String namespace, String jobName, JobReportRequest request) {
        Map<String, Double> metrics = request.metrics() == null ? Map.of() : Map.copyOf(request.metrics());
        Map<String, String> attributes = request.attributes() == null ? Map.of() : Map.copyOf(request.attributes());
        String status = request.status() == null || request.status().isBlank() ? "UNKNOWN" : request.status().trim().toUpperCase();

        JobReportSnapshot snapshot = new JobReportSnapshot(
            Instant.now(),
            status,
            metrics,
            attributes
        );

        latestReports.put(key(namespace, jobName), snapshot);
        return snapshot;
    }

    public Optional<JobReportSnapshot> get(String namespace, String jobName) {
        return Optional.ofNullable(latestReports.get(key(namespace, jobName)));
    }

    private String key(String namespace, String jobName) {
        return namespace + "/" + jobName;
    }
}
