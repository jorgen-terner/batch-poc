package infrastruktur.batch.client.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public class ExecutionStreamEventVO {
    private final String type;
    private final String phase;
    private final Integer activePods;
    private final Integer succeededPods;
    private final Integer failedPods;
    private final String pod;
    private final String output;
    private final Integer exitCode;
    private final String cursor;

    @JsonCreator
    public ExecutionStreamEventVO(
            @JsonProperty("type") String type,
            @JsonProperty("phase") String phase,
            @JsonProperty("activePods") Integer activePods,
            @JsonProperty("succeededPods") Integer succeededPods,
            @JsonProperty("failedPods") Integer failedPods,
            @JsonProperty("pod") String pod,
            @JsonProperty("output") String output,
            @JsonProperty("exitCode") Integer exitCode,
            @JsonProperty("cursor") String cursor) {
        this.type = type;
        this.phase = phase;
        this.activePods = activePods;
        this.succeededPods = succeededPods;
        this.failedPods = failedPods;
        this.pod = pod;
        this.output = output;
        this.exitCode = exitCode;
        this.cursor = cursor;
    }

    public String type() {
        return type;
    }

    public String phase() {
        return phase;
    }

    public Integer activePods() {
        return activePods;
    }

    public Integer succeededPods() {
        return succeededPods;
    }

    public Integer failedPods() {
        return failedPods;
    }

    public String pod() {
        return pod;
    }

    public String output() {
        return output;
    }

    public Integer exitCode() {
        return exitCode;
    }

    public String cursor() {
        return cursor;
    }
}
