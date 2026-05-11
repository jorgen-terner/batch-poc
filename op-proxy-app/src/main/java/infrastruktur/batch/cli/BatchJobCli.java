package infrastruktur.batch.cli;

import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.model.StartExecutionRequestVO;
import infrastruktur.batch.model.JobParameterVO;
import infrastruktur.batch.service.JobPhaseResolver;
import infrastruktur.batch.service.KubernetesJobGateway;
import infrastruktur.batch.service.TemplateExecutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

@Command(
    name = "batch-job",
    mixinStandardHelpOptions = true,
    description = "CLI för att styra templatebaserade körningar",
    subcommands = {
        BatchJobCli.StartExecutionCommand.class,
        BatchJobCli.ExecutionStatusCommand.class,
        BatchJobCli.StopExecutionCommand.class
    }
)
public final class BatchJobCli implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(BatchJobCli.class);

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Kubernetes-namespace")
    String namespace;

    @FunctionalInterface
    private interface CommandAction {
        int run();
    }

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CommandLine commandLine;
    private KubernetesClient kubernetesClient;
    private TemplateExecutionService templateExecutionService;

    public static void main(String[] args) {
        BatchJobCli root = new BatchJobCli();
        int exitCode;
        try {
            exitCode = root.cli().execute(args);
        } finally {
            root.close();
        }
        System.exit(exitCode);
    }

    @Override
    public void run() {
        cli().usage(cli().getOut());
    }

    private TemplateExecutionService templateService() {
        if (templateExecutionService == null) {
            if (kubernetesClient == null) {
                kubernetesClient = new KubernetesClientBuilder().build();
            }
            templateExecutionService = new TemplateExecutionService(
                new KubernetesJobGateway(kubernetesClient),
                new JobPhaseResolver(),
                (namespace, scope, name, status, metrics, attributes) -> {
                }
            );
        }
        return templateExecutionService;
    }

    private void printJson(Object payload) {
        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            cli().getOut().println(json);
        } catch (JsonProcessingException ex) {
            LOG.error("Kunde inte serialisera CLI-utdata", ex);
            throw new IllegalStateException("Kunde inte serialisera CLI-utdata", ex);
        }
    }

    private CommandLine cli() {
        if (commandLine == null) {
            commandLine = new CommandLine(this);
            commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                String message = ex.getMessage();
                if (message == null || message.isBlank()) {
                    message = ex.getClass().getSimpleName();
                }
                cmd.getErr().println("Fel: " + message);
                LOG.debug("CLI-kommando misslyckades", ex);
                return cmd.getCommandSpec().exitCodeOnExecutionException();
            });
        }
        return commandLine;
    }

    private void close() {
        if (kubernetesClient != null) {
            kubernetesClient.close();
        }
    }

    private List<JobParameterVO> parseParameters(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        List<JobParameterVO> result = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalArgumentException("--parameter förväntar name=value");
            }

            int separatorIndex = entry.indexOf('=');
            if (separatorIndex <= 0) {
                throw new IllegalArgumentException("--parameter förväntar name=value, fick: " + entry);
            }

            String name = entry.substring(0, separatorIndex).trim();
            String value = entry.substring(separatorIndex + 1);
            if (name.isBlank()) {
                throw new IllegalArgumentException("--parameter-namn får inte vara tomt");
            }

            result.add(new JobParameterVO(name, value));
        }
        return result;
    }

    @Command(name = "start-execution", description = "Starta en körning från en template")
    static final class StartExecutionCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Namn på OpenShift Template-resurs")
        private String templateName;

        @Option(names = {"--client-request-id"}, description = "Valfritt korrelations-id från klient")
        private String clientRequestId;

        @Option(names = {"--timeout-seconds"}, description = "Valfri maxkörtid (activeDeadlineSeconds)")
        private Long timeoutSeconds;

        @Option(names = {"-p", "--parameter"}, description = "Template-parameter som name=value (upprepa flaggan för flera värden)")
        private List<String> parameters;

        @Override
        public Integer call() {
            return parent.executeWithNotFoundHandling(() -> {
                ExecutionActionResponseVO response = parent.templateService().start(
                    parent.namespace,
                    templateName,
                    new StartExecutionRequestVO(clientRequestId, timeoutSeconds, parent.parseParameters(parameters))
                );
                parent.printJson(response);
                return parent.exitCodeFromState(response.state());
            });
        }
    }

    @Command(name = "execution-status", description = "Hämta körningsstatus (kan bevakas till terminalt läge)")
    static final class ExecutionStatusCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Körningsnamn")
        private String executionName;

        @Option(names = {"-w", "--watch"}, description = "Polla status tills SUCCEEDED eller FAILED")
        private boolean watch;

        @Option(names = {"--interval-seconds"}, defaultValue = "5", description = "Pollningsintervall när --watch är aktiverat")
        private long intervalSeconds;

        @Option(names = {"--timeout-seconds"}, description = "Valfri timeout för watch-läge")
        private Long timeoutSeconds;

        @Override
        public Integer call() {
            return parent.executeWithNotFoundHandling(() -> {
                if (!watch) {
                    ExecutionStatusResponseVO status = parent.templateService().status(parent.namespace, executionName);
                    parent.printJson(status);
                    return parent.exitCodeFromPhase(status.phase());
                }

                if (intervalSeconds < 1) {
                    throw new IllegalArgumentException("--interval-seconds måste vara >= 1");
                }

                Instant started = Instant.now();
                while (true) {
                    ExecutionStatusResponseVO status = parent.templateService().status(parent.namespace, executionName);
                    parent.printJson(status);

                    String phase = status.phase();
                    if ("SUCCEEDED".equalsIgnoreCase(phase) || "FAILED".equalsIgnoreCase(phase)) {
                        return parent.exitCodeFromPhase(phase);
                    }

                    if (timeoutSeconds != null) {
                        long elapsed = Duration.between(started, Instant.now()).toSeconds();
                        if (elapsed >= timeoutSeconds) {
                            return 124;
                        }
                    }

                    parent.sleep(intervalSeconds * 1000);
                }
            });
        }
    }

    @Command(name = "stop-execution", description = "Stoppa en körning")
    static final class StopExecutionCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Körningsnamn")
        private String executionName;

        @Override
        public Integer call() {
            return parent.executeWithNotFoundHandling(() -> {
                ExecutionActionResponseVO response = parent.templateService().stop(
                    parent.namespace,
                    executionName
                );
                parent.printJson(response);
                return parent.exitCodeFromState(response.state());
            });
        }
    }

    private int exitCodeFromState(String state) {
        return switch (JobPhaseResolver.normalize(state)) {
            case "SUCCEEDED", "CANCELLED", "STOPPED" -> 0;
            case "RUNNING", "PENDING" -> 10;
            case "FAILED" -> 2;
            case "SUSPENDED" -> 3;
            default -> 4;
        };
    }

    private int exitCodeFromPhase(String phase) {
        return switch (JobPhaseResolver.normalize(phase)) {
            case "SUCCEEDED", "CANCELLED", "STOPPED" -> 0;
            case "RUNNING", "PENDING" -> 10;
            case "FAILED" -> 2;
            case "SUSPENDED" -> 3;
            default -> 4;
        };
    }

    private int executeWithNotFoundHandling(CommandAction action) {
        try {
            return action.run();
        } catch (NoSuchElementException ex) {
            return handleNotFound(ex);
        }
    }

    private int handleNotFound(NoSuchElementException exception) {
        String reason = exception.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = "Resursen hittades inte";
        }

        String normalizedReason = reason.endsWith(".") ? reason.substring(0, reason.length() - 1) : reason;

        cli().getErr().println(normalizedReason + ".");
        cli().getErr().println("Möjliga orsaker: fel namespace/namn, resursen är stoppad, raderad eller borttagen efter ttlSecondsAfterFinished.");
        return 4;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Avbruten under väntan i watch-läge", ex);
        }
    }
}



