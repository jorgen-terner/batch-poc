package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SSE-event från GET /api/executions/{name}/stream.
 *
 * <ul>
 *   <li>{@code "status"} – aktuell fas och pod-räknare</li>
 *   <li>{@code "log"}    – stdout från en pod</li>
 *   <li>{@code "done"}   – terminal fas nådd, exitCode sätts</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionStreamEventVO(
    String type,
    String phase,
    Integer activePods,
    Integer succeededPods,
    Integer failedPods,
    String pod,
    String output,
    Integer exitCode
) {}
