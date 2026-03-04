package com.example.infbatchjob.controller;

import com.example.infbatchjob.exception.JobException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof JobException) {
            return handleJobException((JobException) exception);
        } else if (exception instanceof ConstraintViolationException) {
            return handleValidationException((ConstraintViolationException) exception);
        } else {
            return handleGenericException(exception);
        }
    }

    private Response handleJobException(JobException e) {
        Map<String, String> response = new HashMap<>();
        response.put("error", e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
    }

    private Response handleValidationException(ConstraintViolationException e) {
        Map<String, String> response = e.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (v1, v2) -> v1 + ", " + v2
            ));
        return Response.status(Response.Status.BAD_REQUEST).entity(response).build();
    }

    private Response handleGenericException(Exception e) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Internt serverfel: " + e.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(response).build();
    }
}
