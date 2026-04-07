package infrastruktur.batch.metrics;

import infrastruktur.batch.model.JobReportSnapshot;

@FunctionalInterface
public interface JobMetricsReporter {
    void report(String namespace, String jobName, JobReportSnapshot snapshot);
}