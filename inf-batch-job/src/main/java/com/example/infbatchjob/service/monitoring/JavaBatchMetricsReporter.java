package com.example.infbatchjob.service.monitoring;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@ApplicationScoped
public class JavaBatchMetricsReporter implements BatchMetricsReporter {

    private static final Logger LOG = LoggerFactory.getLogger(JavaBatchMetricsReporter.class);
    private static final Pattern INVALID_JOB_CHARS = Pattern.compile("[^a-zA-Z0-9,_-]");

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    @Override
    public void onStart(BatchMetricsContext context) {
        report("start", context, null);
    }

    @Override
    public void onStop(BatchMetricsContext context) {
        report("stop", context, null);
    }

    @Override
    public void onError(BatchMetricsContext context, String reason) {
        report("error", context, reason);
    }

    private void report(String state, BatchMetricsContext context, String reason) {
        MetricsTarget target = resolveTarget(context.server());
        String object = sanitizeObject(context.object());

        String objectStatus;
        int statusFlag;
        String influxDb;

        if ("start".equals(state)) {
            objectStatus = "Executing";
            statusFlag = 0;
            influxDb = target.execDb();
        } else if ("stop".equals(state)) {
            objectStatus = "Completed";
            statusFlag = 2;
            influxDb = target.historyDb();
        } else {
            objectStatus = "Failed";
            statusFlag = 1;
            influxDb = target.historyDb();
        }

        try {
            dropSeries(target.metricHost(), target.execDb(), object);
            writeStatus(target.metricHost(), influxDb, context, object, objectStatus, statusFlag, reason);
        } catch (Exception e) {
            LOG.warn("Metrics reporting failed for job {}: {}", context.jobId(), e.getMessage());
        }
    }

    private void dropSeries(String metricHost, String dbName, String object) throws Exception {
        String query = "DROP SERIES from exec_job where JOB='" + object + "'";
        String body = "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + metricHost + ".sfa.se:8086/query?db=" + dbName))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private void writeStatus(
        String metricHost,
        String dbName,
        BatchMetricsContext context,
        String object,
        String objectStatus,
        int statusFlag,
        String reason
    ) throws Exception {
        String elapsed = formatElapsed(context.startTime(), Instant.now());
        String startTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneOffset.UTC)
            .format(context.startTime());

        StringBuilder line = new StringBuilder();
        line.append("exec_job,JOB=").append(object).append(" ")
            .append("Object=\"").append(object).append("\",")
            .append("Start_time=\"").append(startTime).append("\",")
            .append("Status=\"").append(objectStatus).append("\",")
            .append("PID=").append(context.pid()).append(",")
            .append("User=\"").append(context.user()).append("\",")
            .append("Server=\"").append(context.server()).append("\",")
            .append("Chart=\"").append(context.chart()).append("\",")
            .append("Environment=\"").append(context.environment()).append("\",")
            .append("Elapsed=\"").append(elapsed).append("\",")
            .append("Status_flag=").append(statusFlag);

        if (reason != null && !reason.isBlank()) {
            String cleanedReason = reason.replace('"', '\'');
            line.append(",Reason=\"").append(cleanedReason).append("\"");
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + metricHost + ".sfa.se:8086/write?db=" + dbName))
            .header("Content-Type", "text/plain; charset=utf-8")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(line.toString()))
            .build();

        httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private String sanitizeObject(String object) {
        if (object == null || object.isBlank()) {
            return "unknown_job";
        }
        String cleaned = INVALID_JOB_CHARS.matcher(object).replaceAll("");
        return cleaned.isBlank() ? "unknown_job" : cleaned;
    }

    private String formatElapsed(Instant start, Instant end) {
        long seconds = Math.max(0, Duration.between(start, end).getSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    private MetricsTarget resolveTarget(String server) {
        if (server != null && server.contains("prod")) {
            return new MetricsTarget("fkmetrics", "surv_executing", "surv_history");
        }
        return new MetricsTarget("metricstest", "davve", "davve");
    }

    private record MetricsTarget(String metricHost, String execDb, String historyDb) {
    }
}
