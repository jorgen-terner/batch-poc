package infrastruktur.batch.client;

import infrastruktur.batch.client.model.ExecutionActionResponseVO;
import infrastruktur.batch.client.model.ExecutionLogsResponseVO;
import infrastruktur.batch.client.model.ExecutionStatusResponseVO;
import infrastruktur.batch.client.model.JobParameterVO;
import infrastruktur.batch.client.model.StartExecutionRequestVO;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Java-klient för op-proxy-app — motsvarar start-batch.sh fast med SSE-stöd.
 *
 * <p>Exit-koder:
 * <ul>
 *   <li>0   – SUCCEEDED / STOPPED</li>
 *   <li>2   – FAILED</li>
 *   <li>3   – SUSPENDED</li>
 *   <li>4   – UNKNOWN eller oväntat läge</li>
 *   <li>10  – Aktiv (internt, returneras ej till anroparen)</li>
 *   <li>124 – Timeout i bevakningsläge</li>
 *   <li>130 – Avbruten (SIGINT)</li>
 *   <li>1   – Konfigurationsfel eller nätverksfel</li>
 * </ul>
 */
@Command(
    name = "batch-start",
    mixinStandardHelpOptions = true,
    description = "Starta en körning via op-proxy-app och vänta på resultat. " +
                  "Stödjer polling (standard) och SSE-strömning (--sse)."
)
public class BatchStartCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Template-namn")
    private String templateName;

    @Option(
        names = {"--base-url"},
        defaultValue = "${OP_PROXY_BASE_URL:-http://localhost:8080}",
        description = "Bas-URL till op-proxy-app (standard: $${OP_PROXY_BASE_URL:-http://localhost:8080})"
    )
    private String baseUrl;

    @Option(names = {"--client-request-id"}, description = "Valfritt clientRequestId")
    private String clientRequestId;

    @Option(names = {"--timeout-seconds"}, description = "Valfritt activeDeadlineSeconds till körningen")
    private Long timeoutSeconds;

    @Option(
        names = {"-p", "--parameter"},
        description = "Template-parameter som NAME=VALUE (kan anges flera gånger)"
    )
    private List<String> parameters = new ArrayList<>();

    @Option(
        names = {"--interval-seconds"},
        defaultValue = "5",
        description = "Poll-/strömningsintervall i sekunder (standard: 5, min: 1)"
    )
    private int intervalSeconds;

    @Option(
        names = {"--watch-timeout-seconds"},
        defaultValue = "3600",
        description = "Max tid att vaka i sekunder (standard: 3600, 0 = ingen timeout)"
    )
    private long watchTimeoutSeconds;

    @Option(names = {"--sse"}, description = "Använd SSE-strömning i stället för polling")
    private boolean sse;

    @Option(names = {"--show-logs"}, description = "Skriv ut pod-loggar när körningen avslutas (polling-läge)")
    private boolean showLogs;

    @Option(names = {"--logs-tail-lines"}, description = "Max antal loggrader per pod (standard: alla)")
    private Integer logsTailLines;

    @Option(names = {"--namespace"}, defaultValue = "default", description = "Kubernetes-namespace (standard: default)")
    private String kubeNamespace;

    public static void main(String[] args) {
        System.exit(new CommandLine(new BatchStartCommand()).execute(args));
    }

    @Override
    public Integer call() {
        if (intervalSeconds < 1) {
            System.err.println("FEL: --interval-seconds måste vara >= 1");
            return 1;
        }
        if (logsTailLines != null && logsTailLines < 1) {
            System.err.println("FEL: --logs-tail-lines måste vara >= 1");
            return 1;
        }

        List<JobParameterVO> jobParams = parseParameters();
        if (jobParams == null) {
            return 1; // fel loggat i parseParameters
        }

        // Håller execution-namn för shutdown hook – null = klar, ingen stop nödvändig.
        AtomicReference<String> executionNameRef = new AtomicReference<>();

        Thread stopHook = buildStopHook(executionNameRef);
        Runtime.getRuntime().addShutdownHook(stopHook);

        try (OpProxyApiClient client = new OpProxyApiClient(baseUrl)) {

            ExecutionActionResponseVO started = client.start(
                templateName,
                new StartExecutionRequestVO(
                    blankToNull(clientRequestId),
                    timeoutSeconds,
                    jobParams.isEmpty() ? null : jobParams
                ),
                kubeNamespace
            );

            String executionName = started.executionName();
            if (executionName == null || executionName.trim().isEmpty()) {
                System.err.println("FEL: startsvaret saknar executionName");
                return 1;
            }

            executionNameRef.set(executionName);
            System.out.println("Körning startad: " + executionName);

            int result = sse
                ? watchViaSse(client, executionName)
                : watchViaPolling(client, executionName);

            // Normal avslut – nollställ så att shutdown hook inte skickar stop.
            executionNameRef.set(null);
            tryRemoveShutdownHook(stopHook);

            if (showLogs && !sse) {
                printLogs(client, executionName);
            }

            return result;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return 130;
        } catch (ApiException ex) {
            System.err.println("FEL: " + ex.getMessage());
            return 1;
        } catch (Exception ex) {
            System.err.println("FEL: " + ex.getMessage());
            return 1;
        }
    }

    // ─── bevakning: polling ────────────────────────────────────────────────────

    private int watchViaPolling(OpProxyApiClient client, String executionName)
            throws Exception {
        Instant started = Instant.now();
        while (true) {
            ExecutionStatusResponseVO status = client.status(executionName, kubeNamespace);
            System.out.printf("Status: fas=%s executionName=%s%n", status.phase(), executionName);

            int code = phaseToExitCode(status.phase());
            if (code != 10) {
                return code;
            }

            if (watchTimeoutSeconds > 0) {
                long elapsed = Duration.between(started, Instant.now()).getSeconds();
                if (elapsed >= watchTimeoutSeconds) {
                    System.err.printf("Timeout efter %ds (senaste fas=%s)%n",
                        watchTimeoutSeconds, status.phase());
                    return 124;
                }
            }

            Thread.sleep(intervalSeconds * 1000L);
        }
    }

    // ─── bevakning: SSE ────────────────────────────────────────────────────────

    private int watchViaSse(OpProxyApiClient client, String executionName)
            throws Exception {
        // exitCode[0] används som muterbar int i lambda
        int[] exitCode = {4};
        boolean[] completed = {false};
        String[] sinceCursor = {null};
        Instant started = Instant.now();

        while (!completed[0]) {
            if (watchTimeoutSeconds > 0) {
                long elapsed = Duration.between(started, Instant.now()).getSeconds();
                if (elapsed >= watchTimeoutSeconds) {
                    System.err.printf("Timeout efter %ds i SSE-läge%n", watchTimeoutSeconds);
                    return 124;
                }
            }

            try {
                client.stream(executionName, intervalSeconds, kubeNamespace, sinceCursor[0], event -> {
                    String eventType = event.type();
                    if ("status".equals(eventType)) {
                        System.out.printf(
                            "Status: fas=%s (aktiva=%d lyckade=%d misslyckade=%d)%n",
                            event.phase(),
                            orZero(event.activePods()),
                            orZero(event.succeededPods()),
                            orZero(event.failedPods()));
                    } else if ("log".equals(eventType)) {
                        if (event.cursor() != null && !event.cursor().trim().isEmpty()) {
                            sinceCursor[0] = event.cursor();
                        }
                        System.out.printf("--- Logg: %s ---%n", event.pod());
                        if (event.output() != null) {
                            System.out.println(event.output());
                        }
                    } else if ("done".equals(eventType)) {
                        if (event.cursor() != null && !event.cursor().trim().isEmpty()) {
                            sinceCursor[0] = event.cursor();
                        }
                        System.out.printf("Klar: fas=%s exitCode=%d%n",
                            event.phase(), orZero(event.exitCode()));
                        exitCode[0] = orZero(event.exitCode());
                        completed[0] = true;
                    }
                });
                if (!completed[0]) {
                    System.err.println("Varning: SSE-strömmen avslutades utan done-händelse, försöker återansluta...");
                    Thread.sleep(Math.max(1000L, intervalSeconds * 1000L));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (Exception ex) {
                if (completed[0]) {
                    break;
                }
                System.err.println("Varning: SSE-anslutning bröts, försöker återansluta: " + ex.getMessage());
                Thread.sleep(Math.max(1000L, intervalSeconds * 1000L));
            }
        }

        return exitCode[0];
    }

    // ─── loggar (pollingläge) ──────────────────────────────────────────────────

    private void printLogs(OpProxyApiClient client, String executionName) {
        System.out.printf("--- Loggar för körning %s ---%n", executionName);
        try {
            ExecutionLogsResponseVO logs = client.logs(executionName, logsTailLines, kubeNamespace);
            if (logs.logsByPod() != null) {
                for (Map.Entry<String, String> entry : logs.logsByPod().entrySet()) {
                    System.out.printf("[%s]%n%s%n", entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception ex) {
            System.err.println("Varning: kunde inte hämta loggar: " + ex.getMessage());
        }
        System.out.println("--- Slut loggar ---");
    }

    // ─── shutdown hook ─────────────────────────────────────────────────────────

    private Thread buildStopHook(AtomicReference<String> executionNameRef) {
        return new Thread(() -> {
            String name = executionNameRef.getAndSet(null);
            if (name == null) {
                return;
            }
            System.err.println("\nAvbruten – skickar stop för körning " + name + " …");
            try (OpProxyApiClient hookClient = new OpProxyApiClient(baseUrl)) {
                hookClient.stop(name, kubeNamespace);
                System.err.println("Stop-anrop skickat.");
            } catch (Exception ex) {
                System.err.println("Varning: stop-anrop misslyckades: " + ex.getMessage());
            }
        }, "stop-shutdown-hook");
    }

    private static void tryRemoveShutdownHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM håller redan på att stängas ner. Hooken körs, men executionNameRef är null -> no-op
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private List<JobParameterVO> parseParameters() {
        List<JobParameterVO> result = new ArrayList<>();
        for (String p : parameters) {
            int idx = p.indexOf('=');
            if (idx <= 0) {
                System.err.println("FEL: --parameter måste vara NAME=VALUE, fick: " + p);
                return null;
            }
            result.add(new JobParameterVO(p.substring(0, idx).trim(), p.substring(idx + 1)));
        }
        return result;
    }

    private static int phaseToExitCode(String phase) {
        if (phase == null) {
            return 4;
        }
        String normalized = phase.toUpperCase(java.util.Locale.ROOT);
        if ("SUCCEEDED".equals(normalized) || "STOPPED".equals(normalized) || "CANCELLED".equals(normalized)) {
            return 0;
        }
        if ("RUNNING".equals(normalized) || "PENDING".equals(normalized)) {
            return 10;
        }
        if ("FAILED".equals(normalized)) {
            return 2;
        }
        if ("SUSPENDED".equals(normalized)) {
            return 3;
        }
        return 4;
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static String blankToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s;
    }
}
