package com.batchjobapp.service;

import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class JobMetricsCollector {
    public PodMetricsSummary collect(List<Pod> pods) {
        int totalRestarts = 0;
        Integer lastExitCode = null;
        String lastTerminationReason = null;
        String lastTerminationMessage = null;

        for (Pod pod : pods) {
            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                continue;
            }
            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                totalRestarts += cs.getRestartCount() == null ? 0 : cs.getRestartCount();
                if (cs.getState() != null && cs.getState().getTerminated() != null) {
                    ContainerStateTerminated terminated = cs.getState().getTerminated();
                    if (terminated.getExitCode() != null) {
                        lastExitCode = terminated.getExitCode();
                    }
                    if (terminated.getReason() != null && !terminated.getReason().isBlank()) {
                        lastTerminationReason = terminated.getReason();
                    }
                    if (terminated.getMessage() != null && !terminated.getMessage().isBlank()) {
                        lastTerminationMessage = terminated.getMessage();
                    }
                }
            }
        }

        return new PodMetricsSummary(totalRestarts, lastExitCode, lastTerminationReason, lastTerminationMessage);
    }

    public record PodMetricsSummary(
        int totalRestarts,
        Integer lastExitCode,
        String lastTerminationReason,
        String lastTerminationMessage
    ) {
    }
}