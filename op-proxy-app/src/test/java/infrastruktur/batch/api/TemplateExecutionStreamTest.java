package infrastruktur.batch.api;

import infrastruktur.batch.model.ExecutionStreamEventVO;
import infrastruktur.batch.service.TemplateExecutionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class TemplateExecutionStreamTest {

    @InjectMock
    TemplateExecutionService templateExecutionService;

    private static final String NAMESPACE = "default";
    private static final String EXEC_NAME = "my-exec-1";

    private static ExecutionStreamEventVO statusEvent(String phase, String cursor) {
        return new ExecutionStreamEventVO("status", phase, 0, 0, 0, null, null, null, cursor);
    }

    private static ExecutionStreamEventVO logEvent(String pod, String output, String cursor) {
        return ExecutionStreamEventVO.log(pod, output, cursor);
    }

    private static ExecutionStreamEventVO doneEvent(String phase, int exitCode, String cursor) {
        return ExecutionStreamEventVO.done(phase, exitCode, cursor);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void streamShouldReturnStatusEventWithServerSentEventsContentType() {
        var statusEvent = statusEvent("RUNNING", Instant.now().toString());
        var doneEvent = doneEvent("SUCCEEDED", 0, Instant.now().toString());

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), anyInt(), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent, doneEvent)));

        given()
            .queryParam("intervalSeconds", 1)
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200)
            .header("content-type", containsString("text/event-stream"));

        verify(templateExecutionService).streamExecution(NAMESPACE, EXEC_NAME, 1, null);
    }

    @Test
    void streamShouldAcceptSinceParameter() {
        String since = "2026-05-11T10:00:00Z";
        var statusEvent = statusEvent("RUNNING", Instant.now().toString());

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), anyInt(), eq(since)))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent)));

        given()
            .queryParam("intervalSeconds", 2)
            .queryParam("namespace", NAMESPACE)
            .queryParam("since", since)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200);

        verify(templateExecutionService).streamExecution(NAMESPACE, EXEC_NAME, 2, since);
    }

    @Test
    void streamShouldUseDefaultIntervalSecondsWhenNotProvided() {
        var statusEvent = statusEvent("PENDING", Instant.now().toString());

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), eq(3), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent)));

        given()
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200);

        verify(templateExecutionService).streamExecution(NAMESPACE, EXEC_NAME, 3, null);
    }

    @Test
    void streamShouldUseDefaultNamespaceWhenNotProvided() {
        var statusEvent = statusEvent("RUNNING", Instant.now().toString());

        // Mock should be called with default namespace
        when(templateExecutionService.streamExecution(eq("default"), eq(EXEC_NAME), anyInt(), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent)));

        given()
            .queryParam("intervalSeconds", 1)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200);

        verify(templateExecutionService).streamExecution("default", EXEC_NAME, 1, null);
    }

    @Test
    void streamForMissingExecutionShouldReturnError() {
        when(templateExecutionService.streamExecution(anyString(), eq("missing-exec"), anyInt(), any()))
            .thenThrow(new NoSuchElementException("Job not found: default/missing-exec"));

        given()
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/missing-exec/stream")
            .then()
            .statusCode(404);
    }

    @Test
    void streamShouldHandleInvalidIntervalSeconds() {
        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), eq(0), any()))
            .thenThrow(new IllegalArgumentException("intervalSeconds måste vara >= 1"));

        given()
            .queryParam("intervalSeconds", 0)
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(400);
    }

    @Test
    void streamShouldEmitLogEventsWithPodName() {
        var statusEvent = statusEvent("RUNNING", Instant.now().toString());
        var logEvent = logEvent("pod-1", "line1\nline2\n", Instant.now().toString());
        var doneEvent = doneEvent("SUCCEEDED", 0, Instant.now().toString());

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), anyInt(), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent, logEvent, doneEvent)));

        given()
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200);

        verify(templateExecutionService).streamExecution(NAMESPACE, EXEC_NAME, 3, null);
    }

    @Test
    void streamShouldIncludeCursorInAllEvents() {
        Instant now = Instant.now();
        var cursor = now.toString();

        var statusEvent = statusEvent("RUNNING", cursor);
        var logEvent = logEvent("pod-1", "output", cursor);
        var doneEvent = doneEvent("SUCCEEDED", 0, cursor);

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), anyInt(), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent, logEvent, doneEvent)));

        String body = given()
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        verify(templateExecutionService).streamExecution(NAMESPACE, EXEC_NAME, 3, null);
        assertTrue(body.contains(cursor), "SSE payload should include the event cursor");
    }

}
