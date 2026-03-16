package com.example.infbatchjob.service.monitoring;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsReporterFactory {

    @Inject
    JavaBatchMetricsReporter javaBatchMetricsReporter;

    @Inject
    NoopBatchMetricsReporter noopBatchMetricsReporter;

    public BatchMetricsReporter forBatchType(String batchType) {
        if ("JAVABATCH".equalsIgnoreCase(batchType)) {
            return javaBatchMetricsReporter;
        }
        return noopBatchMetricsReporter;
    }

    public boolean shouldMonitorAsync(String batchType) {
        return "JAVABATCH".equalsIgnoreCase(batchType);
    }
}
