package com.batchjobapp.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger LOG = LoggerFactory.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Unhandled error", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(Map.of("error", exception.getMessage()))
            .build();
    }
}
