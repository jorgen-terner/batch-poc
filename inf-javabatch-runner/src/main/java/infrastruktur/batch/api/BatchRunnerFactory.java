package infrastruktur.batch.api;

import infrastruktur.batch.runner.DefaultBatchRunner;

public final class BatchRunnerFactory {
    private BatchRunnerFactory() {
    }

    public static BatchRunner createDefault() {
        return new DefaultBatchRunner();
    }
}
