package infrastruktur.batch.javabatch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobHookClientTest {

    @Test
    void constructorShouldRejectInvalidTimeout() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new JobHookClient(
                "http://localhost/start",
                "http://localhost/status",
                "http://localhost/stop",
                "execId",
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            ));

        assertTrue(ex.getMessage().contains("http-timeout-seconds"));
    }

    @Test
    void constructorShouldRejectBlankExecIdParamName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new JobHookClient(
                "http://localhost/start",
                "http://localhost/status",
                "http://localhost/stop",
                "   ",
                1,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            ));

        assertTrue(ex.getMessage().contains("exec-id-param-name"));
    }

    @Test
    void constructorShouldRejectInvalidHeaderEntry() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new JobHookClient(
                "http://localhost/start",
                "http://localhost/status",
                "http://localhost/stop",
                "execId",
                1,
                Optional.of("X-Api-Key"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            ));

        assertTrue(ex.getMessage().contains("invalid header entry"));
    }

    @Test
    void invokeStartShouldReturnTrimmedExecutionId() throws Exception {
        try (TestServer server = new TestServer()) {
            server.register("/start", 200, "  exec-123  ");

            JobHookClient client = newClient(server);

            String executionId = client.invokeStartAndGetExecutionId();

            assertEquals("exec-123", executionId);
        }
    }

    @Test
    void invokeStartShouldFailOnEmptyBody() throws Exception {
        try (TestServer server = new TestServer()) {
            server.register("/start", 200, "   ");

            JobHookClient client = newClient(server);

            IllegalStateException ex = assertThrows(IllegalStateException.class, client::invokeStartAndGetExecutionId);
            assertTrue(ex.getMessage().contains("empty executionId"));
        }
    }

    @Test
    void fetchBatchStatusShouldParseJsonStatus() throws Exception {
        try (TestServer server = new TestServer()) {
            server.register("/status", 200, "{\"status\":\"completed\"}");

            JobHookClient client = newClient(server);

            String status = client.fetchBatchStatus("exec-1");

            assertEquals("COMPLETED", status);
        }
    }

    @Test
    void fetchBatchStatusShouldParseTokenFromStructuredText() throws Exception {
        try (TestServer server = new TestServer()) {
            server.register("/status", 200, "state=started;progress=42");

            JobHookClient client = newClient(server);

            String status = client.fetchBatchStatus("exec-1");

            assertEquals("STARTED", status);
        }
    }

    @Test
    void fetchBatchStatusShouldNormalizeUnknownRawStatus() throws Exception {
        try (TestServer server = new TestServer()) {
            server.register("/status", 200, "  custom_status  ");

            JobHookClient client = newClient(server);

            String status = client.fetchBatchStatus("exec-1");

            assertEquals("CUSTOM_STATUS", status);
        }
    }

    @Test
    void fetchBatchStatusShouldFailOnHttpError() throws Exception {
        try (TestServer server = new TestServer()) {
            server.register("/status", 500, "boom");

            JobHookClient client = newClient(server);

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.fetchBatchStatus("exec-1"));
            assertTrue(ex.getMessage().contains("HTTP 500"));
        }
    }

    @Test
    void fetchBatchStatusShouldAppendEncodedQueryParam() throws Exception {
        try (TestServer server = new TestServer()) {
            AtomicReference<String> queryRef = new AtomicReference<>();
            server.register("/status", exchange -> {
                queryRef.set(exchange.getRequestURI().getRawQuery());
                respond(exchange, 200, "STARTED");
            });

            JobHookClient client = newClient(server, "exec id");

            client.fetchBatchStatus("id with space");

            assertEquals("exec+id=id+with+space", queryRef.get());
        }
    }

    @Test
    void invokeStopShouldSkipWhenStopUrlMissing() {
        JobHookClient client = new JobHookClient(
            "http://localhost/start",
            "http://localhost/status",
            "   ",
            "execId",
            1,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        assertDoesNotThrow(() -> client.invokeStop("exec-1"));
    }

    @Test
    void invokeStopShouldCallStopEndpoint() throws Exception {
        try (TestServer server = new TestServer()) {
            AtomicInteger calls = new AtomicInteger();
            server.register("/stop", exchange -> {
                calls.incrementAndGet();
                respond(exchange, 200, "ok");
            });

            JobHookClient client = newClient(server);

            client.invokeStop("exec-1");

            assertEquals(1, calls.get());
        }
    }

    private static JobHookClient newClient(TestServer server) {
        return newClient(server, "execId");
    }

    private static JobHookClient newClient(TestServer server, String execIdParamName) {
        return new JobHookClient(
            server.url("/start"),
            server.url("/status"),
            server.url("/stop"),
            execIdParamName,
            1,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.start();
        }

        private void register(String path, int statusCode, String body) {
            register(path, exchange -> respond(exchange, statusCode, body));
        }

        private void register(String path, HttpHandler handler) {
            server.createContext(path, handler);
        }

        private String url(String path) {
            return "http://localhost:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
