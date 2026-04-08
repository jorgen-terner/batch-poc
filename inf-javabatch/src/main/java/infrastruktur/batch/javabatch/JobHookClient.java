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

@ApplicationScoped
public class JobHookClient {
    private static final Logger LOG = LoggerFactory.getLogger(JobHookClient.class);

    private final String startUrl;
    private final String stopUrl;
    private final Duration httpTimeout;
    private final HttpClient httpClient;

    @Inject
    public JobHookClient(
        @ConfigProperty(name = "job.hook.start-url") String startUrl,
        @ConfigProperty(name = "job.hook.stop-url") String stopUrl,
        @ConfigProperty(name = "job.hook.http-timeout-seconds", defaultValue = "30") long httpTimeoutSeconds
    ) {
        if (httpTimeoutSeconds < 1) {
            throw new IllegalArgumentException("job.hook.http-timeout-seconds must be >= 1");
        }

        this.startUrl = normalizeUrl(startUrl);
        this.stopUrl = normalizeUrl(stopUrl);
        this.httpTimeout = Duration.ofSeconds(httpTimeoutSeconds);
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

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.trim();
    }
}