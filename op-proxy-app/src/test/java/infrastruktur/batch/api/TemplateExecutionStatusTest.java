package infrastruktur.batch.api;

import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.service.TemplateExecutionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.NoSuchElementException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@QuarkusTest
class TemplateExecutionStatusTest {

    @InjectMock
    TemplateExecutionService templateExecutionService;

    private static final String NAMESPACE = "default";

    @Test
    void statusShouldReturnRunningExecution() {
        Instant startTime = Instant.parse("2025-01-01T10:00:00Z");
        var statusResponse = new ExecutionStatusResponseVO(
            NAMESPACE, "my-template", "my-exec-1", "RUNNING",
            1, 0, 0, startTime, null, 60L
        );
        when(templateExecutionService.status(eq(NAMESPACE), eq("my-exec-1")))
            .thenReturn(statusResponse);

        given()
            .when()
            .get("/api/executions/my-exec-1")
            .then()
            .statusCode(200)
            .body("namespace", equalTo(NAMESPACE))
            .body("templateName", equalTo("my-template"))
            .body("executionName", equalTo("my-exec-1"))
            .body("phase", equalTo("RUNNING"))
            .body("activePods", equalTo(1))
            .body("succeededPods", equalTo(0))
            .body("failedPods", equalTo(0))
            .body("startTime", notNullValue())
            .body("completionTime", nullValue())
            .body("elapsedSeconds", equalTo(60));
    }

    @Test
    void statusShouldReturnSucceededExecution() {
        Instant startTime = Instant.parse("2025-01-01T10:00:00Z");
        Instant completionTime = Instant.parse("2025-01-01T10:05:00Z");
        var statusResponse = new ExecutionStatusResponseVO(
            NAMESPACE, "my-template", "my-exec-2", "SUCCEEDED",
            0, 1, 0, startTime, completionTime, 300L
        );
        when(templateExecutionService.status(eq(NAMESPACE), eq("my-exec-2")))
            .thenReturn(statusResponse);

        given()
            .when()
            .get("/api/executions/my-exec-2")
            .then()
            .statusCode(200)
            .body("phase", equalTo("SUCCEEDED"))
            .body("succeededPods", equalTo(1))
            .body("completionTime", notNullValue())
            .body("elapsedSeconds", equalTo(300));
    }

    @Test
    void statusShouldReturnFailedExecution() {
        Instant startTime = Instant.parse("2025-01-01T10:00:00Z");
        var statusResponse = new ExecutionStatusResponseVO(
            NAMESPACE, "my-template", "my-exec-3", "FAILED",
            0, 0, 1, startTime, null, 120L
        );
        when(templateExecutionService.status(eq(NAMESPACE), eq("my-exec-3")))
            .thenReturn(statusResponse);

        given()
            .when()
            .get("/api/executions/my-exec-3")
            .then()
            .statusCode(200)
            .body("phase", equalTo("FAILED"))
            .body("failedPods", equalTo(1));
    }

    @Test
    void statusForNonExistentExecutionShouldReturn404() {
        doThrow(new NoSuchElementException("Job not found: default/missing-exec"))
            .when(templateExecutionService).status(anyString(), eq("missing-exec"));

        given()
            .when()
            .get("/api/executions/missing-exec")
            .then()
            .statusCode(404)
            .body("error", equalTo("Resursen hittades inte"))
            .body("code", equalTo("NOT_FOUND"))
            .body("message", equalTo("Job not found: default/missing-exec"));
    }
}
