package infrastruktur.batch.runner;

public final class InfJavabatchRunnerMain {
    private InfJavabatchRunnerMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        int exitCode = runToCompletionFromEnvironment();
        System.exit(exitCode);
    }

    public static int runToCompletionFromEnvironment() throws InterruptedException {
        RunnerConfig config = RunnerConfig.fromEnvironment();
        JobHookClient client = new JobHookClient(config);
        JobHookRunner runner = new JobHookRunner(config, client);
        return runner.runToCompletion();
    }
}
