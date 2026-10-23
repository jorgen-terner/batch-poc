package infrastruktur.batch.runner;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class JobHookClient {
    private final RunnerConfig config;
    private final HttpClient httpClient;

    JobHookClient(RunnerConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.httpTimeoutSeconds()))
                .build();
    }

    String invokeStartAndGetExecutionId() {
        HttpResponse<String> response = invokeRequiredUrl(
                "START",
                config.startUrl(),
                mergeHeaders(config.commonHeaders(), config.startHeaders())
        );
        String executionId = response.body() == null ? "" : response.body().trim();
        if (executionId.isBlank()) {
            throw new IllegalStateException("START hook returned empty executionId");
        }
        RunnerLog.info("START hook returned executionId=" + executionId);
        return executionId;
    }

    String fetchBatchStatus(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }

        String url = appendQueryParam(config.statusUrl(), config.execIdParamName(), executionId);
        HttpResponse<String> response = invokeRequiredUrl(
                "STATUS",
                url,
                mergeHeaders(config.commonHeaders(), config.statusHeaders())
        );
        String body = response.body() == null ? "" : response.body().trim();
        if (body.isBlank()) {
            throw new IllegalStateException("STATUS hook returned empty response");
        }
        return StatusParser.parseBatchStatus(body).orElseGet(() -> StatusParser.normalize(body));
    }

    void invokeStop(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (config.stopUrl() == null || config.stopUrl().isBlank()) {
            RunnerLog.warn("STOP url is not configured, skipping stop hook");
            return;
        }

        String url = appendQueryParam(config.stopUrl(), config.execIdParamName(), executionId);
        invokeRequiredUrl("STOP", url, mergeHeaders(config.commonHeaders(), config.stopHeaders()));
    }

    private HttpResponse<String> invokeRequiredUrl(String hookName, String url, Map<String, String> headers) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(config.httpTimeoutSeconds()));
        headers.forEach(requestBuilder::header);

        HttpRequest request = requestBuilder.build();
        RunnerLog.info("Calling " + hookName + " hook: " + request.uri());

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new IllegalStateException(hookName + " hook failed with HTTP " + statusCode + ": " + response.body());
            }
            RunnerLog.info(hookName + " hook completed with HTTP " + statusCode);
            return response;
        } catch (IOException ex) {
            throw new IllegalStateException(hookName + " hook call failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(hookName + " hook call interrupted", ex);
        }
    }

    private static Map<String, String> mergeHeaders(Map<String, String> commonHeaders, Map<String, String> specificHeaders) {
        if (commonHeaders.isEmpty() && specificHeaders.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(commonHeaders);
        merged.putAll(specificHeaders);
        return Map.copyOf(merged);
    }

    private static String appendQueryParam(String baseUrl, String paramName, String paramValue) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl
                + separator
                + URLEncoder.encode(paramName, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(paramValue, StandardCharsets.UTF_8);
    }
}
