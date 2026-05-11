package infrastruktur.batch.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE-event som sänds via GET /api/executions/{name}/stream.
 *
 * <p>Möjliga typer:
 * <ul>
 *   <li>{@code "status"} – aktuell fas och pod-räknare (skickas periodiskt)</li>
 *   <li>{@code "log"}    – stdout/stderr från en avslutad pod (skickas en gång vid terminal fas)</li>
 *   <li>{@code "done"}   – terminal fas nådd, strömmen stängs efter detta event</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutionStreamEventVO(
    String type,
    String phase,
    Integer activePods,
    Integer succeededPods,
    Integer failedPods,
    String pod,
    String output,
    Integer exitCode,
    String cursor
) {
    public static ExecutionStreamEventVO status(ExecutionStatusResponseVO s, String cursor) {
        return new ExecutionStreamEventVO(
            "status", s.phase(),
            s.activePods(), s.succeededPods(), s.failedPods(),
            null, null, null, cursor
        );
    }

    public static ExecutionStreamEventVO log(String pod, String output, String cursor) {
        return new ExecutionStreamEventVO("log", null, null, null, null, pod, output, null, cursor);
    }

    public static ExecutionStreamEventVO done(String phase, int exitCode, String cursor) {
        return new ExecutionStreamEventVO("done", phase, null, null, null, null, null, exitCode, cursor);
    }
}
