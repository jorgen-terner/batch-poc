package infrastruktur.batch.service;

import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class JobMetricsCollector {
    public PodMetricsSummary collect(List<Pod> pods) {
        int totalRestarts = 0;
        List<TerminationSnapshot> terminations = new ArrayList<>();

        for (Pod pod : pods) {
            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                continue;
            }
            String podName = pod.getMetadata() == null ? "" : String.valueOf(pod.getMetadata().getName());

            for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                totalRestarts += cs.getRestartCount() == null ? 0 : cs.getRestartCount();
                if (cs.getState() != null && cs.getState().getTerminated() != null) {
                    ContainerStateTerminated terminated = cs.getState().getTerminated();
                    terminations.add(new TerminationSnapshot(
                        parseInstant(terminated.getFinishedAt()),
                        podName,
                        cs.getName() == null ? "" : cs.getName(),
                        terminated.getExitCode(),
                        terminated.getReason(),
                        terminated.getMessage()
                    ));
                }
            }
        }

        terminations.sort(Comparator
            .comparing(TerminationSnapshot::finishedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TerminationSnapshot::podName)
            .thenComparing(TerminationSnapshot::containerName));

        TerminationSnapshot latest = terminations.isEmpty() ? null : terminations.get(terminations.size() - 1);
        Integer lastExitCode = latest == null ? null : latest.exitCode();
        String lastTerminationReason = latest == null ? null : blankToNull(latest.reason());
        String lastTerminationMessage = latest == null ? null : blankToNull(latest.message());

        return new PodMetricsSummary(totalRestarts, lastExitCode, lastTerminationReason, lastTerminationMessage);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private record TerminationSnapshot(
        Instant finishedAt,
        String podName,
        String containerName,
        Integer exitCode,
        String reason,
        String message
    ) {
    }

    public record PodMetricsSummary(
        int totalRestarts,
        Integer lastExitCode,
        String lastTerminationReason,
        String lastTerminationMessage
    ) {
    }
}