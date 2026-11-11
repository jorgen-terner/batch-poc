package infrastruktur.batch.runner;

import infrastruktur.batch.api.BatchRunner;

public class DefaultBatchRunner implements BatchRunner {
    @Override
    public int runToCompletionFromEnvironment() throws InterruptedException {
        RunnerConfig config = RunnerConfig.fromEnvironment();
        JobHookClient client = new JobHookClient(config);
        JobHookRunner runner = new JobHookRunner(config, client);
        return runner.runToCompletion();
    }
}
