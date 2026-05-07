package infrastruktur.batch.api;

import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.service.TemplateExecutionService;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@QuarkusTest
class TemplateExecutionStopTest {

    @InjectMock
    TemplateExecutionService templateExecutionService;

    private static final String NAMESPACE = "default";

    @Test
    void stopShouldReturnStoppedResponse() {
        var response = new ExecutionActionResponseVO(
            NAMESPACE, "my-template", "my-exec-1", null,
            "stop", "STOPPED", "Execution stopped (graceful stop), execution job deleted",
            Instant.now()
        );
        when(templateExecutionService.stop(eq(NAMESPACE), eq("my-exec-1")))
            .thenReturn(response);

        given()
            .contentType("application/json")
            .when()
            .post("/api/executions/my-exec-1/stop")
            .then()
            .statusCode(200)
            .body("namespace", equalTo(NAMESPACE))
            .body("templateName", equalTo("my-template"))
            .body("executionName", equalTo("my-exec-1"))
            .body("action", equalTo("stop"))
            .body("state", equalTo("STOPPED"))
            .body("createdAt", notNullValue());
    }

    @Test
    void stopWhenKubernetesApiFailsShouldReturn502() {
        KubernetesClientException apiError = new KubernetesClientException("Kubernetes API unavailable", 500, null);
        doThrow(apiError)
            .when(templateExecutionService).stop(anyString(), eq("my-exec-fail"));

        given()
            .contentType("application/json")
            .when()
            .post("/api/executions/my-exec-fail/stop")
            .then()
            .statusCode(502)
            .body("error", equalTo("Kubernetes API error"))
            .body("code", equalTo("KUBERNETES_API_ERROR"));
    }
}
