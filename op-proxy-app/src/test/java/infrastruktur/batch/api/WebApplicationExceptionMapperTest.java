package infrastruktur.batch.api;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebApplicationExceptionMapperTest {

    private final WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();

    @Test
    void fourXxExceptionShouldReturnSameStatusCode() {
        Response response = mapper.toResponse(new WebApplicationException(404));

        assertEquals(404, response.getStatus());
        assertStructuredBody(response, "NOT_FOUND");
    }

    @Test
    void fourOhThreeExceptionShouldReturnForbidden() {
        Response response = mapper.toResponse(new WebApplicationException(403));

        assertEquals(403, response.getStatus());
        assertStructuredBody(response, "FORBIDDEN");
    }

    @Test
    void fiveHundredExceptionShouldReturn500() {
        Response response = mapper.toResponse(new WebApplicationException(500));

        assertEquals(500, response.getStatus());
        assertStructuredBody(response, "INTERNAL_SERVER_ERROR");
    }

    @Test
    void exceptionWithCustomMessageShouldAppearInResponse() {
        Response response = mapper.toResponse(
            new WebApplicationException("custom error detail", 422));

        assertEquals(422, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertNotNull(body.get("message"));
        assertEquals("custom error detail", body.get("message"));
    }

    @Test
    void exceptionWithUnknownStatusCodeShouldStillReturnResponse() {
        // Use a status code that doesn't map to a well-known Response.Status enum value
        Response response = mapper.toResponse(new WebApplicationException(418));

        assertEquals(418, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("HTTP_418", body.get("code"));
    }

    @SuppressWarnings("unchecked")
    private static void assertStructuredBody(Response response, String expectedCode) {
        assertNotNull(response.getEntity());
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertNotNull(body.get("error"));
        assertEquals(expectedCode, body.get("code"));
        assertNotNull(body.get("message"));
    }
}
