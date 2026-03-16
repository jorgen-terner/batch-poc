package com.example.infbatchjob.controller;

import com.example.infbatchjob.exception.JobException;
import com.example.infbatchjob.service.KubernetesJobService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@QuarkusTest
class JobControllerRestartIT {

    @InjectMock
    KubernetesJobService kubernetesJobService;

    @Test
    void shouldRestartJobAndReturnCreated() {
        when(kubernetesJobService.restartJob("job-123")).thenReturn("job-456");

        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/jobs/{jobId}/restart", "job-123")
            .then()
            .statusCode(201)
            .body("jobId", equalTo("job-456"))
            .body("jobName", equalTo("job-456"))
            .body("message", equalTo("Job restartat framgångsrikt"))
            .body("success", equalTo(true));
    }

    @Test
    void shouldReturnBadRequestWhenRestartFails() {
        doThrow(new JobException("Job saknar configMap-label och kan inte restartas"))
            .when(kubernetesJobService)
            .restartJob("job-999");

        given()
            .contentType(ContentType.JSON)
            .when()
            .post("/api/jobs/{jobId}/restart", "job-999")
            .then()
            .statusCode(400)
            .body("success", equalTo(false))
            .body("message", equalTo("Job saknar configMap-label och kan inte restartas"));
    }
}
