package infrastruktur.batch.api;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GenericExceptionMapperTest {

    private final GenericExceptionMapper mapper = new GenericExceptionMapper();

    @Test
    void anyExceptionShouldReturn500WithSafeMessage() {
        Response response = mapper.toResponse(new RuntimeException("internal secret detail"));

        assertEquals(500, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("Internal server error", body.get("error"));
        assertEquals("INTERNAL_ERROR", body.get("code"));
        // Safe generic message – must NOT leak the exception message
        assertEquals("An unexpected error occurred", body.get("message"));
    }

    @Test
    void nullMessageExceptionShouldReturn500() {
        Response response = mapper.toResponse(new NullPointerException());

        assertEquals(500, response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    void checkedExceptionShouldAlsoReturn500() {
        Response response = mapper.toResponse(new Exception("checked exception"));

        assertEquals(500, response.getStatus());
    }
}
