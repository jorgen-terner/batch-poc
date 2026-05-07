package infrastruktur.batch.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HealthEndpointTest {

    @Test
    void healthShouldReturnStatusUp() {
        given()
            .when()
            .get("/health")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("status", equalTo("UP"));
    }
}
