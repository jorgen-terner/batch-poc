package infrastruktur.batch.javabatch;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

@ApplicationScoped
public class JobHookLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(JobHookLifecycle.class);

    private final JobHookClient jobHookClient;
    private final long stopWaitSeconds;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    @Inject
    public JobHookLifecycle(
        JobHookClient jobHookClient,
        @ConfigProperty(name = "job.hook.stop-wait-seconds", defaultValue = "15") long stopWaitSeconds
    ) {
        if (stopWaitSeconds < 0) {
            throw new IllegalArgumentException("job.hook.stop-wait-seconds must be >= 0");
        }
        this.jobHookClient = jobHookClient;
        this.stopWaitSeconds = stopWaitSeconds;
    }

    void onStart(@Observes StartupEvent event) {
        jobHookClient.invokeStart();
        LOG.info("inf-javabatch started and is now waiting for a termination signal");
    }

    void onStop(@Observes ShutdownEvent event) {
        try {
            if (jobHookClient.shouldInvokeStopHook()) {
                jobHookClient.invokeStop();
            } else {
                LOG.info("Skipping STOP hook based on STATUS response");
            }
            if (stopWaitSeconds > 0) {
                LOG.info("Waiting {} second(s) before shutdown completes", stopWaitSeconds);
                Thread.sleep(stopWaitSeconds * 1000);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting before shutdown", ex);
        } catch (RuntimeException ex) {
            LOG.warn("Stop hook failed", ex);
        } finally {
            shutdownLatch.countDown();
        }
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }
}