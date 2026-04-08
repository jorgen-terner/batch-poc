package infrastruktur.batch.javabatch;

import io.quarkus.runtime.QuarkusApplication;
import jakarta.inject.Inject;

public class JobHookApplication implements QuarkusApplication {
    private final JobHookLifecycle lifecycle;

    @Inject
    public JobHookApplication(JobHookLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public int run(String... args) throws InterruptedException {
        lifecycle.awaitShutdown();
        return 0;
    }
}