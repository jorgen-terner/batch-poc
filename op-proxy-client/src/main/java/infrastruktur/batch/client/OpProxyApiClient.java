package infrastruktur.batch.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import infrastruktur.batch.client.model.ExecutionActionResponseVO;
import infrastruktur.batch.client.model.ExecutionLogsResponseVO;
import infrastruktur.batch.client.model.ExecutionStatusResponseVO;
import infrastruktur.batch.client.model.ExecutionStreamEventVO;
import infrastruktur.batch.client.model.StartExecutionRequestVO;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * HTTP-klient för op-proxy-app.
 *
 * <p>SSE-strömning: läser {@code text/event-stream} rad för rad och anropar onEvent per händelse.
 * Blockerar tills servern stänger strömmen (dvs. när en {@code done}-händelse tas emot).
 */
public class OpProxyApiClient implements AutoCloseable {

    private final String baseUrl;
    private final ObjectMapper mapper;

    public OpProxyApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ExecutionActionResponseVO start(String templateName, StartExecutionRequestVO request, String namespace)
            throws IOException, InterruptedException {
        String body = mapper.writeValueAsString(request);
        String url = baseUrl + "/api/templates/" + templateName + "/start";
        if (namespace != null && !namespace.trim().isEmpty()) {
            url += "?namespace=" + namespace;
        }
        return execute(url, "POST", body, "application/json", "application/json", ExecutionActionResponseVO.class);
    }

    public ExecutionStatusResponseVO status(String executionName, String namespace)
            throws IOException, InterruptedException {
        String url = baseUrl + "/api/executions/" + executionName;
        if (namespace != null && !namespace.trim().isEmpty()) {
            url += "?namespace=" + namespace;
        }
        return execute(url, "GET", null, null, "application/json", ExecutionStatusResponseVO.class);
    }

    public ExecutionActionResponseVO stop(String executionName, String namespace)
            throws IOException, InterruptedException {
        String url = baseUrl + "/api/executions/" + executionName + "/stop";
        if (namespace != null && !namespace.trim().isEmpty()) {
            url += "?namespace=" + namespace;
        }
        return execute(url, "POST", "", "application/json", "application/json", ExecutionActionResponseVO.class);
    }

    public ExecutionLogsResponseVO logs(String executionName, Integer tailLines, String namespace)
            throws IOException, InterruptedException {
        String url = baseUrl + "/api/executions/" + executionName + "/logs";
        boolean hasParam = false;
        if (namespace != null && !namespace.trim().isEmpty()) {
            url += "?namespace=" + namespace;
            hasParam = true;
        }
        if (tailLines != null) {
            url += (hasParam ? "&" : "?") + "tailLines=" + tailLines;
        }
        return execute(url, "GET", null, null, "application/json", ExecutionLogsResponseVO.class);
    }

    /**
     * Öppnar en SSE-ström till {@code /api/executions/{name}/stream} och anropar
     * {@code onEvent} för varje mottagen händelse. Blockerar tills servern stänger
     * strömmen eller tråden avbryts.
     *
     * <p>SSE-format (RFC): en eller flera rader av typen {@code data: <json>} per händelse,
     * händelser separeras med tomrader. Övriga fält (event:, id:, retry:) ignoreras.
     */
    public void stream(String executionName, int intervalSeconds, String namespace, Consumer<ExecutionStreamEventVO> onEvent)
            throws IOException, InterruptedException {
        stream(executionName, intervalSeconds, namespace, null, onEvent);
    }

    /**
     * Öppnar en SSE-ström och skickar med tidscursor som since-parameter vid reconnect.
     */
    public void stream(
            String executionName,
            int intervalSeconds,
            String namespace,
            String since,
            Consumer<ExecutionStreamEventVO> onEvent)
            throws IOException, InterruptedException {
        String url = baseUrl + "/api/executions/" + executionName + "/stream?intervalSeconds=" + intervalSeconds;
        if (namespace != null && !namespace.trim().isEmpty()) {
            url += "&namespace=" + namespace;
        }
        if (since != null && !since.trim().isEmpty()) {
            url += "&since=" + URLEncoder.encode(since, "UTF-8");
        }
        HttpURLConnection connection = openConnection(url, "GET", null, "text/event-stream");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new ApiException(status,
                "SSE-strömningsbegäran misslyckades för körning: " + executionName);
        }

        StringBuilder dataBuffer = new StringBuilder();
        try (InputStream bodyStream = connection.getInputStream();
             BufferedReader lines = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (!data.isEmpty()) {
                        dataBuffer.append(data);
                    }
                } else if (line.trim().isEmpty() && dataBuffer.length() > 0) {
                    // Tomrad = slutet på en SSE-händelse
                    ExecutionStreamEventVO event = mapper.readValue(dataBuffer.toString(), ExecutionStreamEventVO.class);
                    dataBuffer.setLength(0);
                    onEvent.accept(event);
                }
                // Övriga SSE-fält (event:, id:, retry:) ignoreras avsiktligt
            }
        } finally {
            connection.disconnect();
        }
    }

    private <T> T execute(String url, String method, String body, String contentType, String accept, Class<T> responseType)
            throws IOException, InterruptedException {
        HttpURLConnection connection = openConnection(url, method, contentType, accept);
        try {
            if (body != null) {
                connection.setDoOutput(true);
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
            }

            int status = connection.getResponseCode();
            String responseBody = readResponseBody(connection, status);

            if (status < 200 || status >= 300) {
                throw new ApiException(status, extractErrorMessage(responseBody));
            }
            return mapper.readValue(responseBody, responseType);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String url, String method, String contentType, String accept)
            throws IOException {
        URL parsedUrl = URI.create(url).toURL();
        HttpURLConnection connection = (HttpURLConnection) parsedUrl.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        if (accept != null) {
            connection.setRequestProperty("Accept", accept);
        }
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        return connection;
    }

    private String readResponseBody(HttpURLConnection connection, int status) throws IOException {
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Försöker extrahera {@code message} från en JSON-felkropp.
     * Faller tillbaka till råtext om parsning misslyckas.
     */
    private String extractErrorMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "tom svarskropp";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(body);
            String msg = node.path("message").asText(null);
            return msg != null ? msg : body;
        } catch (Exception ignored) {
            return body;
        }
    }

    @Override
    public void close() {
        // Ingen bestående resurs att stänga för HttpURLConnection-varianten.
    }
}
