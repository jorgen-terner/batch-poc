package com.example.infbatchjob.service.monitoring;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NoopBatchMetricsReporter implements BatchMetricsReporter {

    @Override
    public void onStart(BatchMetricsContext context) {
        // no-op by design
    }

    @Override
    public void onStop(BatchMetricsContext context) {
        // no-op by design
    }

    @Override
    public void onError(BatchMetricsContext context, String reason) {
        // no-op by design
    }
}
