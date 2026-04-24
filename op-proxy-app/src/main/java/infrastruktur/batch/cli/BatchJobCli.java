package infrastruktur.batch.cli;

import infrastruktur.batch.model.ActionResponse;
import infrastruktur.batch.model.ExecutionActionResponseVO;
import infrastruktur.batch.model.ExecutionStatusResponseVO;
import infrastruktur.batch.model.StopExecutionRequestVO;
import infrastruktur.batch.model.StartExecutionRequestVO;
import infrastruktur.batch.model.JobParameterVO;
import infrastruktur.batch.model.RestartJobRequestVO;
import infrastruktur.batch.model.StartJobRequestVO;
import infrastruktur.batch.model.JobStatusResponse;
import infrastruktur.batch.service.JobControlService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
    name = "batch-job",
    mixinStandardHelpOptions = true,
    description = "CLI for controlling pre-created Kubernetes batch Jobs",
    subcommands = {
        BatchJobCli.StartCommand.class,
        BatchJobCli.StopCommand.class,
        BatchJobCli.RestartCommand.class,
        BatchJobCli.StatusCommand.class,
        BatchJobCli.StartExecutionCommand.class,
        BatchJobCli.ExecutionStatusCommand.class,
        BatchJobCli.StopExecutionCommand.class
    }
)
public final class BatchJobCli implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(BatchJobCli.class);

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Kubernetes namespace")
    String namespace;

    @FunctionalInterface
    private interface CommandAction {
        int run();
    }

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CommandLine commandLine;
    private KubernetesClient kubernetesClient;
    private JobControlService jobControlService;
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

    private JobControlService service() {
        if (jobControlService == null) {
            kubernetesClient = new KubernetesClientBuilder().build();
            jobControlService = new JobControlService(kubernetesClient);
        }
        return jobControlService;
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
            LOG.error("Failed to serialize CLI output payload", ex);
            throw new IllegalStateException("Failed to serialize CLI output", ex);
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
                cmd.getErr().println("Error: " + message);
                LOG.debug("CLI command failed", ex);
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

        Map<String, String> deduplicated = new LinkedHashMap<>();
        Set<String> duplicateGuard = new HashSet<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                throw new IllegalArgumentException("--parameter expects name=value");
            }

            int separatorIndex = entry.indexOf('=');
            if (separatorIndex <= 0) {
                throw new IllegalArgumentException("--parameter expects name=value, got: " + entry);
            }

            String name = entry.substring(0, separatorIndex).trim();
            String value = entry.substring(separatorIndex + 1);
            if (name.isBlank()) {
                throw new IllegalArgumentException("--parameter name must not be blank");
            }
            if (!duplicateGuard.add(name)) {
                throw new IllegalArgumentException("Duplicate parameter name: " + name);
            }

            deduplicated.put(name, value);
        }

        List<JobParameterVO> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : deduplicated.entrySet()) {
            result.add(new JobParameterVO(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @Command(name = "start", description = "Start a suspended Job")
    static final class StartCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Job name")
        private String jobName;

        @Option(names = {"--timeout-seconds"}, description = "Optional max runtime (activeDeadlineSeconds)")
        private Long timeoutSeconds;

        @Option(names = {"-p", "--parameter"}, description = "Job parameter as name=value (repeat option for multiple values)")
        private List<String> parameters;

        @Override
        public Integer call() {
            return parent.executeWithNotFoundHandling(() -> {
                ActionResponse response = parent.service().start(
                    parent.namespace,
                    jobName,
                    new StartJobRequestVO(timeoutSeconds, parent.parseParameters(parameters))
                );
                parent.printJson(response);
                return parent.exitCodeFromState(response.state());
            });
        }
    }

    @Command(name = "stop", description = "Stop a running Job")
    static final class StopCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Job name")
        private String jobName;

        @Override
        public Integer call() {
            return parent.executeWithNotFoundHandling(() -> {
                ActionResponse response = parent.service().stop(parent.namespace, jobName);
                parent.printJson(response);
                return parent.exitCodeFromState(response.state());
            });
        }
    }

    @Command(name = "restart", description = "Restart a Job by recreate")
    static final class RestartCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Job name")
        private String jobName;

        @Option(names = {"--timeout-seconds"}, description = "Optional max runtime (activeDeadlineSeconds)")
        private Long timeoutSeconds;

        @Option(names = {"--keep-failed-pods"}, defaultValue = "true", description = "Keep failed/succeeded pods for troubleshooting")
        private boolean keepFailedPods;

        @Option(names = {"-p", "--parameter"}, description = "Job parameter as name=value (repeat option for multiple values)")
        private List<String> parameters;

        @Override
        public Integer call() {
            return parent.executeWithNotFoundHandling(() -> {
                ActionResponse response = parent.service().restart(
                    parent.namespace,
                    jobName,
                    new RestartJobRequestVO(timeoutSeconds, keepFailedPods, parent.parseParameters(parameters))
                );
                parent.printJson(response);
                return parent.exitCodeFromState(response.state());
            });
        }
    }

    @Command(name = "status", description = "Get Job status (optionally watch until terminal state)")
    static final class StatusCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Job name")
        private String jobName;

        @Option(names = {"-w", "--watch"}, description = "Poll status until SUCCEEDED or FAILED")
        private boolean watch;

        @Option(names = {"--interval-seconds"}, defaultValue = "5", description = "Polling interval when --watch is enabled")
        private long intervalSeconds;

        @Option(names = {"--timeout-seconds"}, description = "Optional timeout for watch mode")
        private Long timeoutSeconds;

        @Override
        public Integer call() {
            return parent.executeWithNotFoundHandling(() -> {
                if (!watch) {
                    JobStatusResponse status = parent.service().status(parent.namespace, jobName);
                    parent.printJson(status);
                    return parent.exitCodeFromPhase(status.phase());
                }

                if (intervalSeconds < 1) {
                    throw new IllegalArgumentException("--interval-seconds must be >= 1");
                }

                Instant started = Instant.now();
                while (true) {
                    JobStatusResponse status = parent.service().status(parent.namespace, jobName);
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

    @Command(name = "start-execution", description = "Start an execution from a template (v2)")
    static final class StartExecutionCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "OpenShift Template resource name")
        private String templateName;

        @Option(names = {"--client-request-id"}, description = "Optional client correlation id")
        private String clientRequestId;

        @Option(names = {"--timeout-seconds"}, description = "Optional max runtime (activeDeadlineSeconds)")
        private Long timeoutSeconds;

        @Option(names = {"-p", "--parameter"}, description = "Execution parameter as name=value (repeat option for multiple values)")
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

    @Command(name = "execution-status", description = "Get execution status (v2, optionally watch until terminal state)")
    static final class ExecutionStatusCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Execution name")
        private String executionName;

        @Option(names = {"-w", "--watch"}, description = "Poll status until SUCCEEDED or FAILED")
        private boolean watch;

        @Option(names = {"--interval-seconds"}, defaultValue = "5", description = "Polling interval when --watch is enabled")
        private long intervalSeconds;

        @Option(names = {"--timeout-seconds"}, description = "Optional timeout for watch mode")
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
                    throw new IllegalArgumentException("--interval-seconds must be >= 1");
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

    @Command(name = "stop-execution", description = "Stop an execution (v2)")
    static final class StopExecutionCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Execution name")
        private String executionName;

        @Option(names = {"--delete-pods"}, defaultValue = "false", description = "Delete pods together with the execution Job")
        private boolean deletePods;

        @Override
        public Integer call() {
            return parent.executeWithNotFoundHandling(() -> {
                ExecutionActionResponseVO response = parent.templateService().stop(
                    parent.namespace,
                    executionName,
                    new StopExecutionRequestVO(deletePods)
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
            reason = "Resource not found";
        }

        String normalizedReason = reason.endsWith(".") ? reason.substring(0, reason.length() - 1) : reason;

        cli().getErr().println(normalizedReason + ".");
        cli().getErr().println("Possible causes: wrong namespace/name, resource stopped, deleted or removed after ttlSecondsAfterFinished.");
        return 4;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting in watch mode", ex);
        }
    }
}
