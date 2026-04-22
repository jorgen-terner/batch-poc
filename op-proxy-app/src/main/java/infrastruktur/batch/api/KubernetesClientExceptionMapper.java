package infrastruktur.batch.api;

import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Provider
public class KubernetesClientExceptionMapper implements ExceptionMapper<KubernetesClientException> {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesClientExceptionMapper.class);

    @Override
    public Response toResponse(KubernetesClientException exception) {
        int code = exception.getCode();
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        boolean alreadyExists = code == 409 || message.contains("AlreadyExists");

        if (alreadyExists) {
            LOG.info("Kubernetes conflict while creating resource: {}", message);
            return Response.status(Response.Status.CONFLICT)
                .entity(Map.of(
                    "error", "Resource already exists",
                    "code", "ALREADY_EXISTS"
                ))
                .build();
        }

        LOG.error("Kubernetes client error", exception);
        return Response.status(Response.Status.BAD_GATEWAY)
            .entity(Map.of(
                "error", "Kubernetes API error",
                "code", "KUBERNETES_API_ERROR"
            ))
            .build();
    }
}
