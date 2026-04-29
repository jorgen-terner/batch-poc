package infrastruktur.batch.javabatch;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class JobHookClient {
    private static final Logger LOG = LoggerFactory.getLogger(JobHookClient.class);
    private static final Pattern JSON_STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final String startUrl;
    private final String statusUrl;
    private final String stopUrl;
    private final String execIdParamName;
    private final Duration httpTimeout;
    private final HttpClient httpClient;
    private final Map<String, String> commonHeaders;
    private final Map<String, String> startHeaders;
    private final Map<String, String> statusHeaders;
    private final Map<String, String> stopHeaders;

    private static final Set<String> BATCH_STATUS_VALUES = Set.of(
        "STARTING",
        "STARTED",
        "STOPPING",
        "STOPPED",
        "COMPLETED",
        "FAILED",
        "ABANDONED",
        "UNKNOWN"
    );

    @Inject
    public JobHookClient(
        @ConfigProperty(name = "job.hook.start-url") String startUrl,
        @ConfigProperty(name = "job.hook.status-url") String statusUrl,
        @ConfigProperty(name = "job.hook.stop-url") String stopUrl,
        @ConfigProperty(name = "job.hook.exec-id-param-name", defaultValue = "execId") String execIdParamName,
        @ConfigProperty(name = "job.hook.http-timeout-seconds", defaultValue = "30") long httpTimeoutSeconds,
        @ConfigProperty(name = "job.hook.common-headers") Optional<String> commonHeaders,
        @ConfigProperty(name = "job.hook.start-headers") Optional<String> startHeaders,
        @ConfigProperty(name = "job.hook.status-headers") Optional<String> statusHeaders,
        @ConfigProperty(name = "job.hook.stop-headers") Optional<String> stopHeaders
    ) {
        if (httpTimeoutSeconds < 1) {
            throw new IllegalArgumentException("job.hook.http-timeout-seconds must be >= 1");
        }
        if (execIdParamName == null || execIdParamName.isBlank()) {
            throw new IllegalArgumentException("job.hook.exec-id-param-name must not be blank");
        }

        this.startUrl = normalizeUrl(startUrl);
        this.statusUrl = normalizeUrl(statusUrl);
        this.stopUrl = normalizeUrl(stopUrl);
        this.execIdParamName = execIdParamName.trim();
        this.httpTimeout = Duration.ofSeconds(httpTimeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(this.httpTimeout)
            .build();
        this.commonHeaders = parseHeaders(commonHeaders.orElse(""), "job.hook.common-headers");
        this.startHeaders = parseHeaders(startHeaders.orElse(""), "job.hook.start-headers");
        this.statusHeaders = parseHeaders(statusHeaders.orElse(""), "job.hook.status-headers");
        this.stopHeaders = parseHeaders(stopHeaders.orElse(""), "job.hook.stop-headers");
    }

    public String invokeStartAndGetExecutionId() {
        if (startUrl == null) {
            throw new IllegalStateException("Missing required config: job.hook.start-url");
        }
        HttpResponse<String> response = invokeRequiredUrl("START", startUrl, mergeHeaders(startHeaders));
        String executionId = response.body() == null ? "" : response.body().trim();
        if (executionId.isBlank()) {
            throw new IllegalStateException("START hook returned empty executionId");
        }
        LOG.info("START hook returned executionId={}", executionId);
        return executionId;
    }

    public String fetchBatchStatus(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (statusUrl == null) {
            throw new IllegalStateException("Missing required config: job.hook.status-url");
        }

        String urlWithExecId = appendQueryParam(statusUrl, execIdParamName, executionId);
        HttpResponse<String> response = invokeRequiredUrl("STATUS", urlWithExecId, mergeHeaders(statusHeaders));
        String body = response.body() == null ? "" : response.body().trim();
        if (body.isBlank()) {
            throw new IllegalStateException("STATUS hook returned empty response");
        }

        return parseBatchStatus(body)
            .orElseGet(() -> normalizeStatus(body));
    }

    public void invokeStop(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId must not be blank");
        }
        if (stopUrl == null) {
            LOG.warn("Config 'job.hook.stop-url' is not configured, skipping stop hook");
            return;
        }

        String urlWithExecId = appendQueryParam(stopUrl, execIdParamName, executionId);
        invokeRequiredUrl("STOP", urlWithExecId, mergeHeaders(stopHeaders));
    }

    private HttpResponse<String> invokeRequiredUrl(String hookName, String url, Map<String, String> headers) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(httpTimeout);
        headers.forEach(requestBuilder::header);

        HttpRequest request = requestBuilder.build();

        String requestUrl = request.uri().toString();

        LOG.info("Calling {} hook: {}", hookName, requestUrl);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new IllegalStateException(
                    hookName + " hook failed with HTTP " + statusCode + ": " + response.body()
                );
            }
            LOG.info("{} hook completed with HTTP {}", hookName, statusCode);
            return response;
        } catch (IOException ex) {
            throw new IllegalStateException(hookName + " hook call failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(hookName + " hook call interrupted", ex);
        }
    }

    private Optional<String> parseBatchStatus(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Optional.empty();
        }

        String upper = normalizeStatus(rawResponse);

        if (BATCH_STATUS_VALUES.contains(upper)) {
            return Optional.of(upper);
        }

        Matcher jsonStatusMatcher = JSON_STATUS_PATTERN.matcher(rawResponse);
        if (jsonStatusMatcher.find()) {
            String parsedJsonStatus = normalizeStatus(jsonStatusMatcher.group(1));
            if (BATCH_STATUS_VALUES.contains(parsedJsonStatus)) {
                return Optional.of(parsedJsonStatus);
            }
        }

        // Fallback: allow exact token matches from a structured plain-text payload.
        String[] tokens = upper.split("[^A-Z_]+");
        for (String token : tokens) {
            if (BATCH_STATUS_VALUES.contains(token)) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    private String normalizeStatus(String rawStatus) {
        String normalized = rawStatus == null ? "" : rawStatus.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String appendQueryParam(String baseUrl, String paramName, String paramValue) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl
            + separator
            + URLEncoder.encode(paramName, StandardCharsets.UTF_8)
            + "="
            + URLEncoder.encode(paramValue, StandardCharsets.UTF_8);
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.trim();
    }

    private Map<String, String> mergeHeaders(Map<String, String> specificHeaders) {
        if (commonHeaders.isEmpty() && specificHeaders.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(commonHeaders);
        merged.putAll(specificHeaders);
        return Map.copyOf(merged);
    }

    private Map<String, String> parseHeaders(String rawHeaders, String propertyName) {
        if (rawHeaders == null || rawHeaders.isBlank()) {
            return Map.of();
        }

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        String[] entries = rawHeaders.split("[;\\r\\n]+");
        for (String entry : entries) {
            String trimmedEntry = entry.trim();
            if (trimmedEntry.isEmpty()) {
                continue;
            }

            int separatorIndex = trimmedEntry.indexOf('=');
            if (separatorIndex < 0) {
                separatorIndex = trimmedEntry.indexOf(':');
            }
            if (separatorIndex <= 0 || separatorIndex == trimmedEntry.length() - 1) {
                throw new IllegalArgumentException(
                    propertyName + " contains invalid header entry: '" + trimmedEntry + "'. Expected format 'Header=Value'"
                );
            }

            String name = trimmedEntry.substring(0, separatorIndex).trim();
            String value = trimmedEntry.substring(separatorIndex + 1).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(propertyName + " contains a blank header name");
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException(propertyName + " contains a blank value for header '" + name + "'");
            }
            headers.put(name, value);
        }

        return headers.isEmpty() ? Map.of() : Map.copyOf(headers);
    }
}