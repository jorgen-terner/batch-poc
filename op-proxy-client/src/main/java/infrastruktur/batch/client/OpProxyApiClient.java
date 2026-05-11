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
 * HTTP-klient för op-proxy-app. Använder java.net.http.HttpClient (inga externa beroenden).
 *
 * <p>SSE-strömning: läser {@code text/event-stream} rad för rad och anropar onEvent per händelse.
 * Blockerar tills servern stänger strömmen (dvs. när en {@code done}-händelse tas emot).
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
     * Öppnar en SSE-ström till {@code /api/executions/{name}/stream} och anropar
     * {@code onEvent} för varje mottagen händelse. Blockerar tills servern stänger
     * strömmen eller tråden avbryts.
     *
     * <p>SSE-format (RFC): en eller flera rader av typen {@code data: <json>} per händelse,
     * händelser separeras med tomrader. Övriga fält (event:, id:, retry:) ignoreras.
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
                "SSE-strömningsbegäran misslyckades för körning: " + executionName);
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
                    // Tomrad = slutet på en SSE-händelse
                    ExecutionStreamEventVO event =
                        mapper.readValue(dataBuffer.toString(), ExecutionStreamEventVO.class);
                    dataBuffer.setLength(0);
                    onEvent.accept(event);
                }
                // Övriga SSE-fält (event:, id:, retry:) ignoreras avsiktligt
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
     * Försöker extrahera {@code message} från en JSON-felkropp.
     * Faller tillbaka till råtext om parsning misslyckas.
     */
    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "tom svarskropp";
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
        http.close(); // HttpClient är AutoCloseable sedan Java 21
    }
}
