package infrastruktur.batch.model;

import java.util.Map;

public record JobReportRequest(
    String status,
    Map<String, Double> metrics,
    Map<String, String> attributes
) {}
