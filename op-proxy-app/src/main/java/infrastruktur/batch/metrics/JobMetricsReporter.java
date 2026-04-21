package infrastruktur.batch.metrics;

import java.util.Map;

@FunctionalInterface
public interface JobMetricsReporter {
    void report(
        String namespace,
        String scope,
        String name,
        String status,
        Map<String, Double> metrics,
        Map<String, String> attributes
    );
}
