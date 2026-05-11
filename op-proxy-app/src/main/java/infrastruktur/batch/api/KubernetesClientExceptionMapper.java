package infrastruktur.batch.api;

import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Maps KubernetesClientException to appropriate HTTP response.
 * Distinguishes between conflict (409 when resource exists) and gateway errors (502).
 */
@Provider
public class KubernetesClientExceptionMapper implements ExceptionMapper<KubernetesClientException> {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesClientExceptionMapper.class);

    @Override
    public Response toResponse(KubernetesClientException exception) {
        int code = exception.getCode();
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        boolean alreadyExists = code == 409 || message.contains("AlreadyExists");

        if (alreadyExists) {
            LOG.info("Kubernetes-konflikt vid skapande av resurs: {}", message);
            return Response.status(Response.Status.CONFLICT)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                    "error", "Resursen finns redan",
                    "code", "ALREADY_EXISTS",
                    "message", message.isEmpty() ? "Resursen finns redan" : message
                ))
                .build();
        }

        LOG.error("Kubernetes-klientfel", exception);
        return Response.status(Response.Status.BAD_GATEWAY)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of(
                "error", "Kubernetes-API-fel",
                "code", "KUBERNETES_API_ERROR",
                "message", message.isEmpty() ? "Kunde inte kommunicera med Kubernetes-API" : message
            ))
            .build();
    }
}
