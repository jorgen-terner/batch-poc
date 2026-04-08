package infrastruktur.batch.javabatch;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class JobHookClient {
    private static final Logger LOG = LoggerFactory.getLogger(JobHookClient.class);

    private final String startUrl;
    private final String statusUrl;
    private final String stopUrl;
    private final Duration httpTimeout;
    private final boolean failOpenOnStatusError;
    private final HttpClient httpClient;

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
        @ConfigProperty(name = "job.hook.http-timeout-seconds", defaultValue = "30") long httpTimeoutSeconds,
        @ConfigProperty(name = "job.hook.fail-open-on-status-error", defaultValue = "true") boolean failOpenOnStatusError
    ) {
        if (httpTimeoutSeconds < 1) {
            throw new IllegalArgumentException("job.hook.http-timeout-seconds must be >= 1");
        }

        this.startUrl = normalizeUrl(startUrl);
        this.statusUrl = normalizeUrl(statusUrl);
        this.stopUrl = normalizeUrl(stopUrl);
        this.httpTimeout = Duration.ofSeconds(httpTimeoutSeconds);
        this.failOpenOnStatusError = failOpenOnStatusError;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(this.httpTimeout)
            .build();
    }

    public void invokeStart() {
        if (startUrl == null) {
            throw new IllegalStateException("START environment variable must be configured");
        }
        invokeRequiredUrl("START", startUrl);
    }

    public void invokeStop() {
        if (stopUrl == null) {
            LOG.warn("STOP environment variable is not configured, skipping stop hook");
            return;
        }
        invokeRequiredUrl("STOP", stopUrl);
    }

    public boolean shouldInvokeStopHook() {
        if (stopUrl == null) {
            LOG.warn("STOP environment variable is not configured, stop hook will be skipped");
            return false;
        }

        if (statusUrl == null) {
            LOG.info("STATUS environment variable is not configured, invoking STOP by default");
            return true;
        }

        try {
            Optional<String> status = fetchBatchStatus();
            if (status.isEmpty()) {
                LOG.warn("Could not determine BatchStatus from STATUS response, failOpenOnStatusError={}", failOpenOnStatusError);
                return failOpenOnStatusError;
            }

            String batchStatus = status.get();
            boolean shouldInvoke = "STARTING".equals(batchStatus) || "STARTED".equals(batchStatus);
            LOG.info("STATUS resolved to {}. invoke STOP={}", batchStatus, shouldInvoke);
            return shouldInvoke;
        } catch (RuntimeException ex) {
            LOG.warn("STATUS hook call failed, failOpenOnStatusError={}", failOpenOnStatusError, ex);
            return failOpenOnStatusError;
        }
    }

    private void invokeRequiredUrl(String hookName, String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(httpTimeout)
            .build();

        LOG.info("Calling {} hook: {}", hookName, url);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new IllegalStateException(
                    hookName + " hook failed with HTTP " + statusCode + ": " + response.body()
                );
            }
            LOG.info("{} hook completed with HTTP {}", hookName, statusCode);
        } catch (IOException ex) {
            throw new IllegalStateException(hookName + " hook call failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(hookName + " hook call interrupted", ex);
        }
    }

    private Optional<String> fetchBatchStatus() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(statusUrl))
            .GET()
            .timeout(httpTimeout)
            .build();

        LOG.info("Calling STATUS hook: {}", statusUrl);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode > 299) {
                throw new IllegalStateException("STATUS hook failed with HTTP " + statusCode + ": " + response.body());
            }

            String body = response.body() == null ? "" : response.body().trim();
            return parseBatchStatus(body);
        } catch (IOException ex) {
            throw new IllegalStateException("STATUS hook call failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("STATUS hook call interrupted", ex);
        }
    }

    private Optional<String> parseBatchStatus(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Optional.empty();
        }

        String upper = rawResponse.toUpperCase(Locale.ROOT);
        for (String candidate : BATCH_STATUS_VALUES) {
            if (upper.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.trim();
    }
}