package infrastruktur.batch.api;

import infrastruktur.batch.service.TemplateExecutionService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
class TemplateExecutionMalformedRequestTest {

    @InjectMock
    TemplateExecutionService templateExecutionService;

    @Test
    void malformedJsonShouldReturnStructuredBadRequest() {
        String malformedPayload = "\"parameters\": [{ \"name\": \"FILE\", \"value\": \"/domains/1000.ac\" },{ \"name\": \"ID\", \"value\": \"1000\" }]}";

        given()
            .contentType("application/json")
            .body(malformedPayload)
            .when()
            .post("/api/templates/my-job-template/start")
            .then()
            .statusCode(400)
            .contentType(containsString("application/json"))
            .body("error", equalTo("Invalid request"))
            .body("code", equalTo("BAD_REQUEST"))
            .body("message", containsString("Request body could not be deserialized"));

        verifyNoInteractions(templateExecutionService);
    }
}
