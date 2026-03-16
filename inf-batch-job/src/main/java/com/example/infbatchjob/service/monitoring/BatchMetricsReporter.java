package com.example.infbatchjob.service.monitoring;

public interface BatchMetricsReporter {

    void onStart(BatchMetricsContext context);

    void onStop(BatchMetricsContext context);

    void onError(BatchMetricsContext context, String reason);
}
