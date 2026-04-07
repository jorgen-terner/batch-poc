package infrastruktur.batch.metrics;

import infrastruktur.batch.model.JobReportSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NoOpJobMetricsReporter implements JobMetricsReporter {
    @Override
    public void report(String namespace, String jobName, JobReportSnapshot snapshot) {
        // Intentionally empty until an external metrics backend is wired in.
    }
}