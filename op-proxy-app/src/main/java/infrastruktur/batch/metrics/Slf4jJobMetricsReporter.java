package infrastruktur.batch.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ApplicationScoped
public class Slf4jJobMetricsReporter implements JobMetricsReporter {
    private static final Logger LOG = LoggerFactory.getLogger(Slf4jJobMetricsReporter.class);

    @Override
    public void report(
        String namespace,
        String scope,
        String name,
        String status,
        Map<String, Double> metrics,
        Map<String, String> attributes
    ) {
        LOG.info(
            "Forwarding generic metrics event: namespace={}, scope={}, name={}, status={}, metrics={}, attributes={}",
            namespace,
            scope,
            name,
            status,
            metrics,
            attributes
        );
    }
}
