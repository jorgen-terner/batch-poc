package infrastruktur.batch.api;

import infrastruktur.batch.model.ExecutionLogsResponseVO;
import infrastruktur.batch.service.TemplateExecutionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@QuarkusTest
class TemplateExecutionLogsTest {

    @InjectMock
    TemplateExecutionService templateExecutionService;

    private static final String NAMESPACE = "default";

    @Test
    void logsShouldReturnPodLogs() {
        var response = new ExecutionLogsResponseVO(
            NAMESPACE,
            "my-template",
            "my-exec-1",
            100,
            Map.of("pod-1", "hello\nworld\n")
        );
        when(templateExecutionService.logs(eq(NAMESPACE), eq("my-exec-1"), eq(100)))
            .thenReturn(response);

        given()
            .queryParam("tailLines", 100)
            .when()
            .get("/api/executions/my-exec-1/logs")
            .then()
            .statusCode(200)
            .body("namespace", equalTo(NAMESPACE))
            .body("templateName", equalTo("my-template"))
            .body("executionName", equalTo("my-exec-1"))
            .body("tailLines", equalTo(100))
            .body("logsByPod.pod-1", equalTo("hello\nworld\n"));
    }

    @Test
    void logsShouldAllowMissingTailLines() {
        var response = new ExecutionLogsResponseVO(
            NAMESPACE,
            "my-template",
            "my-exec-2",
            null,
            Map.of("pod-a", "line\n")
        );
        when(templateExecutionService.logs(eq(NAMESPACE), eq("my-exec-2"), eq(null)))
            .thenReturn(response);

        given()
            .when()
            .get("/api/executions/my-exec-2/logs")
            .then()
            .statusCode(200)
            .body("tailLines", nullValue())
            .body("logsByPod.pod-a", equalTo("line\n"));
    }

    @Test
    void logsForMissingExecutionShouldReturn404() {
        doThrow(new NoSuchElementException("Job not found: default/missing-exec"))
            .when(templateExecutionService).logs(anyString(), eq("missing-exec"), eq(null));

        given()
            .when()
            .get("/api/executions/missing-exec/logs")
            .then()
            .statusCode(404)
            .body("error", equalTo("Resursen hittades inte"))
            .body("code", equalTo("NOT_FOUND"))
            .body("message", equalTo("Job not found: default/missing-exec"));
    }

    @Test
    void logsWithInvalidTailLinesShouldReturn400() {
        doThrow(new IllegalArgumentException("tailLines must be >= 1"))
            .when(templateExecutionService).logs(anyString(), eq("my-exec-1"), eq(0));

        given()
            .queryParam("tailLines", 0)
            .when()
            .get("/api/executions/my-exec-1/logs")
            .then()
            .statusCode(400)
            .body("error", equalTo("Ogiltig begäran"))
            .body("code", equalTo("BAD_REQUEST"))
            .body("message", equalTo("tailLines must be >= 1"));
    }
}