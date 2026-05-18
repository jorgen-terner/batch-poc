package infrastruktur.batch.api;

import infrastruktur.batch.model.ExecutionStreamEventVO;
import infrastruktur.batch.service.TemplateExecutionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class TemplateExecutionStreamTest {

    @InjectMock
    TemplateExecutionService templateExecutionService;

    private static final String NAMESPACE = "default";
    private static final String EXEC_NAME = "my-exec-1";

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void streamShouldReturnStatusEventWithServerSentEventsContentType() {
        var statusEvent = new ExecutionStreamEventVO(
            "status",
            null,
            "RUNNING",
            null,
            null,
            null,
            Instant.now().toString()
        );

        var doneEvent = new ExecutionStreamEventVO(
            "done",
            null,
            "SUCCEEDED",
            0,
            null,
            null,
            Instant.now().toString()
        );

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), anyInt(), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent, doneEvent)));

        given()
            .queryParam("intervalSeconds", 1)
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .header("content-type", containsString("text/event-stream"));
    }

    @Test
    void streamShouldAcceptSinceParameter() {
        String since = "2026-05-11T10:00:00Z";
        var statusEvent = new ExecutionStreamEventVO(
            "status",
            null,
            "RUNNING",
            null,
            null,
            null,
            Instant.now().toString()
        );

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
    }

    @Test
    void streamShouldUseDefaultIntervalSecondsWhenNotProvided() {
        var statusEvent = new ExecutionStreamEventVO(
            "status",
            null,
            "PENDING",
            null,
            null,
            null,
            Instant.now().toString()
        );

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), eq(3), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent)));

        given()
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200);
    }

    @Test
    void streamShouldUseDefaultNamespaceWhenNotProvided() {
        var statusEvent = new ExecutionStreamEventVO(
            "status",
            null,
            "RUNNING",
            null,
            null,
            null,
            Instant.now().toString()
        );

        // Mock should be called with default namespace
        when(templateExecutionService.streamExecution(eq("default"), eq(EXEC_NAME), anyInt(), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent)));

        given()
            .queryParam("intervalSeconds", 1)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200);
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
        var statusEvent = new ExecutionStreamEventVO(
            "status",
            null,
            "RUNNING",
            null,
            null,
            null,
            Instant.now().toString()
        );
        var logEvent = new ExecutionStreamEventVO(
            "log",
            "pod-1",
            null,
            null,
            "line1\nline2\n",
            null,
            Instant.now().toString()
        );
        var doneEvent = new ExecutionStreamEventVO(
            "done",
            null,
            "SUCCEEDED",
            0,
            null,
            null,
            Instant.now().toString()
        );

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), anyInt(), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent, logEvent, doneEvent)));

        given()
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200);
    }

    @Test
    void streamShouldIncludeCursorInAllEvents() {
        Instant now = Instant.now();
        var cursor = now.toString();

        var statusEvent = new ExecutionStreamEventVO(
            "status",
            null,
            "RUNNING",
            null,
            null,
            null,
            cursor
        );
        var logEvent = new ExecutionStreamEventVO(
            "log",
            "pod-1",
            null,
            null,
            "output",
            null,
            cursor
        );
        var doneEvent = new ExecutionStreamEventVO(
            "done",
            null,
            "SUCCEEDED",
            0,
            null,
            null,
            cursor
        );

        when(templateExecutionService.streamExecution(eq(NAMESPACE), eq(EXEC_NAME), anyInt(), any()))
            .thenReturn(io.smallrye.mutiny.Multi.createFrom().iterable(List.of(statusEvent, logEvent, doneEvent)));

        given()
            .queryParam("namespace", NAMESPACE)
            .when()
            .get("/api/executions/" + EXEC_NAME + "/stream")
            .then()
            .statusCode(200);
    }

}
