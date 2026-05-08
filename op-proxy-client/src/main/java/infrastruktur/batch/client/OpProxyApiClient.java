package infrastruktur.batch.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import infrastruktur.batch.client.model.ExecutionActionResponseVO;
import infrastruktur.batch.client.model.ExecutionLogsResponseVO;
import infrastruktur.batch.client.model.ExecutionStatusResponseVO;
import infrastruktur.batch.client.model.ExecutionStreamEventVO;
import infrastruktur.batch.client.model.StartExecutionRequestVO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * HTTP client for op-proxy-app. Uses java.net.http.HttpClient (no external deps).
 *
 * <p>SSE streaming: reads {@code text/event-stream} line-by-line and calls onEvent per event.
 * Blocks until the server closes the stream (i.e. a {@code done} event is received).
 */
public class OpProxyApiClient implements AutoCloseable {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public OpProxyApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ExecutionActionResponseVO start(String templateName, StartExecutionRequestVO request)
            throws IOException, InterruptedException {
        String body = mapper.writeValueAsString(request);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/templates/" + templateName + "/start"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return execute(req, ExecutionActionResponseVO.class);
    }

    public ExecutionStatusResponseVO status(String executionName)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/executions/" + executionName))
            .header("Accept", "application/json")
            .GET()
            .build();
        return execute(req, ExecutionStatusResponseVO.class);
    }

    public ExecutionActionResponseVO stop(String executionName)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/executions/" + executionName + "/stop"))
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        return execute(req, ExecutionActionResponseVO.class);
    }

    public ExecutionLogsResponseVO logs(String executionName, Integer tailLines)
            throws IOException, InterruptedException {
        String url = baseUrl + "/api/executions/" + executionName + "/logs";
        if (tailLines != null) {
            url += "?tailLines=" + tailLines;
        }
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .build();
        return execute(req, ExecutionLogsResponseVO.class);
    }

    /**
     * Opens an SSE stream to {@code /api/executions/{name}/stream} and calls
     * {@code onEvent} for each received event. Blocks until the server closes
     * the stream or the thread is interrupted.
     *
     * <p>SSE format (RFC): one or more {@code data: <json>} lines per event,
     * events separated by blank lines. Other fields (event:, id:, retry:) are ignored.
     */
    public void stream(String executionName, int intervalSeconds, Consumer<ExecutionStreamEventVO> onEvent)
            throws IOException, InterruptedException {
        String url = baseUrl + "/api/executions/" + executionName + "/stream?intervalSeconds=" + intervalSeconds;
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "text/event-stream")
            .GET()
            .build();

        HttpResponse<java.util.stream.Stream<String>> response =
            http.send(req, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(response.statusCode(),
                "SSE stream request failed for execution: " + executionName);
        }

        StringBuilder dataBuffer = new StringBuilder();
        try (var lines = response.body()) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (line.startsWith("data:")) {
                    String data = line.substring(5).strip();
                    if (!data.isEmpty()) {
                        dataBuffer.append(data);
                    }
                } else if (line.isBlank() && dataBuffer.length() > 0) {
                    // Blank line = end of one SSE event
                    ExecutionStreamEventVO event =
                        mapper.readValue(dataBuffer.toString(), ExecutionStreamEventVO.class);
                    dataBuffer.setLength(0);
                    onEvent.accept(event);
                }
                // Other SSE fields (event:, id:, retry:) are intentionally ignored
            }
        }
    }

    private <T> T execute(HttpRequest req, Class<T> responseType)
            throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(response.statusCode(), extractErrorMessage(response.body()));
        }
        return mapper.readValue(response.body(), responseType);
    }

    /**
     * Tries to extract {@code message} from a JSON error body.
     * Falls back to raw body text if parsing fails.
     */
    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "empty response body";
        }
        try {
            var node = mapper.readTree(body);
            String msg = node.path("message").asText(null);
            return msg != null ? msg : body;
        } catch (Exception ignored) {
            return body;
        }
    }

    @Override
    public void close() {
        http.close(); // HttpClient is AutoCloseable since Java 21
    }
}
