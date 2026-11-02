package test.inv.batch.runner;

import infrastruktur.batch.contract.BatchRunner;
import java.util.ServiceLoader;

public final class BatchRunnerProvider {
    private BatchRunnerProvider() {
    }

    public static BatchRunner get() {
        ServiceLoader<BatchRunner> loader = ServiceLoader.load(BatchRunner.class);

        for (BatchRunner runner : loader) {
            return runner;
        }

        throw new IllegalStateException(
                "No BatchRunner implementation found on classpath. " +
                        "Expected external runner jar in /opt/inf-javabatch-runner or on classpath."
        );
    }
}
