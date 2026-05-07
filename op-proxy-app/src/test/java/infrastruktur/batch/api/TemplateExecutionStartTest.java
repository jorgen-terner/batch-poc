package infrastruktur.batch.api;

import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.service.TemplateExecutionService;
import infrastruktur.batch.service.TemplateProcessingException;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.NoSuchElementException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@QuarkusTest
class TemplateExecutionStartTest {

    @InjectMock
    TemplateExecutionService templateExecutionService;

    private static final String TEMPLATE_NAME = "my-job-template";
    private static final String NAMESPACE = "default";

    @Test
    void startWithBodyShouldReturnPendingResponse() {
        var response = pendingResponse(TEMPLATE_NAME, "my-exec-1", "req-abc");
        when(templateExecutionService.start(eq(NAMESPACE), eq(TEMPLATE_NAME), any()))
            .thenReturn(response);

        given()
            .contentType("application/json")
            .body("""
                {"clientRequestId":"req-abc","timeoutSeconds":300,"parameters":[{"name":"FILE","value":"/data/1.csv"}]}
                """)
            .when()
            .post("/api/templates/" + TEMPLATE_NAME + "/start")
            .then()
            .statusCode(200)
            .body("namespace", equalTo(NAMESPACE))
            .body("templateName", equalTo(TEMPLATE_NAME))
            .body("executionName", equalTo("my-exec-1"))
            .body("clientRequestId", equalTo("req-abc"))
            .body("action", equalTo("start"))
            .body("state", equalTo("PENDING"))
            .body("createdAt", notNullValue());
    }

    @Test
    void startWithEmptyBodyShouldSucceed() {
        var response = pendingResponse(TEMPLATE_NAME, "my-exec-2", null);
        when(templateExecutionService.start(eq(NAMESPACE), eq(TEMPLATE_NAME), isNull()))
            .thenReturn(response);

        given()
            .contentType("application/json")
            .when()
            .post("/api/templates/" + TEMPLATE_NAME + "/start")
            .then()
            .statusCode(200)
            .body("state", equalTo("PENDING"))
            .body("action", equalTo("start"));
    }

    @Test
    void startWithMinimalBodyShouldSucceed() {
        var response = pendingResponse(TEMPLATE_NAME, "my-exec-3", null);
        when(templateExecutionService.start(eq(NAMESPACE), eq(TEMPLATE_NAME), any()))
            .thenReturn(response);

        given()
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/templates/" + TEMPLATE_NAME + "/start")
            .then()
            .statusCode(200)
            .body("state", equalTo("PENDING"));
    }

    @Test
    void startWhenTemplateNotFoundShouldReturn422() {
        doThrow(new TemplateProcessingException("Template not found: " + NAMESPACE + "/" + TEMPLATE_NAME))
            .when(templateExecutionService).start(anyString(), anyString(), any());

        given()
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/templates/" + TEMPLATE_NAME + "/start")
            .then()
            .statusCode(422)
            .body("error", equalTo("Template processing failed"))
            .body("code", equalTo("TEMPLATE_PROCESSING_ERROR"))
            .body("message", containsString("Template not found"));
    }

    @Test
    void startWhenJobAlreadyExistsShouldReturn409() {
        KubernetesClientException conflict = new KubernetesClientException("AlreadyExists", 409, null);
        doThrow(conflict)
            .when(templateExecutionService).start(anyString(), anyString(), any());

        given()
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/templates/" + TEMPLATE_NAME + "/start")
            .then()
            .statusCode(409)
            .body("error", equalTo("Resource already exists"))
            .body("code", equalTo("ALREADY_EXISTS"));
    }

    @Test
    void startWhenKubernetesApiFailsShouldReturn502() {
        KubernetesClientException apiError = new KubernetesClientException("Connection refused", 503, null);
        doThrow(apiError)
            .when(templateExecutionService).start(anyString(), anyString(), any());

        given()
            .contentType("application/json")
            .body("{}")
            .when()
            .post("/api/templates/" + TEMPLATE_NAME + "/start")
            .then()
            .statusCode(502)
            .body("error", equalTo("Kubernetes API error"))
            .body("code", equalTo("KUBERNETES_API_ERROR"));
    }

    @Test
    void startWithInvalidParametersShouldReturn400() {
        doThrow(new IllegalArgumentException("Duplicate parameter name: FILE"))
            .when(templateExecutionService).start(anyString(), anyString(), any());

        given()
            .contentType("application/json")
            .body("""
                {"parameters":[{"name":"FILE","value":"a"},{"name":"FILE","value":"b"}]}
                """)
            .when()
            .post("/api/templates/" + TEMPLATE_NAME + "/start")
            .then()
            .statusCode(400)
            .body("error", equalTo("Invalid request"))
            .body("code", equalTo("BAD_REQUEST"))
            .body("message", containsString("Duplicate parameter name"));
    }

    private static ExecutionActionResponseVO pendingResponse(
        String templateName,
        String executionName,
        String clientRequestId
    ) {
        return new ExecutionActionResponseVO(
            NAMESPACE,
            templateName,
            executionName,
            clientRequestId,
            "start",
            "PENDING",
            "Execution started",
            Instant.now()
        );
    }
}
