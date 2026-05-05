package infrastruktur.batch.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * DEPRECATED: Use JsonErrorHelper utility instead.
 *
 * This class was experimental for RESTEasy Reactive @ServerExceptionMapper.
 * Consolidated into IllegalArgumentExceptionMapper approach for unified error handling.
 *
 * All 400 Bad Request responses now follow the same contract via
 * IllegalArgumentExceptionMapper, delegating deserialization to resource methods.
 */
public class ReactiveBadRequestExceptionMapper {
    private static final Logger LOG = LoggerFactory.getLogger(ReactiveBadRequestExceptionMapper.class);

    // @ServerExceptionMapper removed - this mapper is not active
    public Response mapBadRequest(BadRequestException exception) {
        throw new UnsupportedOperationException(
            "ReactiveBadRequestExceptionMapper is deprecated. " +
            "Use IllegalArgumentExceptionMapper via JsonErrorHelper instead."
        );
    }
}
