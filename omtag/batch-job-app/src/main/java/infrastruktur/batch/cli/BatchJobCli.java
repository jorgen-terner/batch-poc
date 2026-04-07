package infrastruktur.batch.cli;

import infrastruktur.batch.model.ActionResponse;
import infrastruktur.batch.model.JobMetricsResponse;
import infrastruktur.batch.model.JobStatusResponse;
import infrastruktur.batch.service.JobControlService;
import infrastruktur.batch.service.JobPhaseResolver;
import infrastruktur.batch.store.JobReportStore;
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
        BatchJobCli.MetricsCommand.class
    }
)
public final class BatchJobCli implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(BatchJobCli.class);

    @Option(names = {"-n", "--namespace"}, defaultValue = "default", description = "Kubernetes namespace")
    String namespace;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CommandLine commandLine;
    private KubernetesClient kubernetesClient;
    private JobControlService jobControlService;

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
            jobControlService = new JobControlService(kubernetesClient, new JobReportStore());
        }
        return jobControlService;
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
        }
        return commandLine;
    }

    private void close() {
        if (kubernetesClient != null) {
            kubernetesClient.close();
        }
    }

    @Command(name = "start", description = "Start a suspended Job")
    static final class StartCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Job name")
        private String jobName;

        @Option(names = {"--timeout-seconds"}, description = "Optional max runtime (activeDeadlineSeconds)")
        private Long timeoutSeconds;

        @Override
        public Integer call() {
            ActionResponse response = parent.service().start(parent.namespace, jobName, timeoutSeconds);
            parent.printJson(response);
            return parent.exitCodeFromState(response.state());
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
            ActionResponse response = parent.service().stop(parent.namespace, jobName);
            parent.printJson(response);
            return parent.exitCodeFromState(response.state());
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

        @Override
        public Integer call() {
            ActionResponse response = parent.service().restart(parent.namespace, jobName, timeoutSeconds, keepFailedPods);
            parent.printJson(response);
            return parent.exitCodeFromState(response.state());
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
        }
    }

    @Command(name = "metrics", description = "Get Job metrics")
    static final class MetricsCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private BatchJobCli parent;

        @Parameters(index = "0", description = "Job name")
        private String jobName;

        @Override
        public Integer call() {
            JobMetricsResponse metrics = parent.service().metrics(parent.namespace, jobName);
            parent.printJson(metrics);
            return parent.exitCodeFromPhase(metrics.phase());
        }
    }

    private int exitCodeFromState(String state) {
        return switch (JobPhaseResolver.normalize(state)) {
            case "SUCCEEDED" -> 0;
            case "RUNNING", "PENDING" -> 10;
            case "FAILED" -> 2;
            case "SUSPENDED" -> 3;
            default -> 4;
        };
    }

    private int exitCodeFromPhase(String phase) {
        return switch (JobPhaseResolver.normalize(phase)) {
            case "SUCCEEDED" -> 0;
            case "RUNNING", "PENDING" -> 10;
            case "FAILED" -> 2;
            case "SUSPENDED" -> 3;
            default -> 4;
        };
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
