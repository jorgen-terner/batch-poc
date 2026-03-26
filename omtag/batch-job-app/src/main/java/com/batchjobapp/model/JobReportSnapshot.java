package com.batchjobapp.model;

import java.time.Instant;
import java.util.Map;

public record JobReportSnapshot(
    Instant timestamp,
    String status,
    Map<String, Double> metrics,
    Map<String, String> attributes
) {}
