package infrastruktur.batch.api;

import infrastruktur.batch.service.TemplateExecutionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import java.util.NoSuchElementException;

@QuarkusTest
class TemplateExecutionStopAlreadyStoppedTest {

    @InjectMock
    TemplateExecutionService templateExecutionService;

    @Test
    void stopAlreadyStoppedJobShouldReturn404() {
        String executionName = "my-job-exec-123";
        
        // Mock the service to throw NoSuchElementException for already-stopped job
        doThrow(new NoSuchElementException("Job not found: default/" + executionName))
            .when(templateExecutionService)
            .stop(anyString(), anyString());

        given()
            .contentType("application/json")
            .when()
            .post("/api/executions/" + executionName + "/stop")
            .then()
            .statusCode(404)
            .body("error", equalTo("Resursen hittades inte"))
            .body("code", equalTo("NOT_FOUND"));
    }
}
