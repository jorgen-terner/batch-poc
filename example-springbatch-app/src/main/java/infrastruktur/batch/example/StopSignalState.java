package infrastruktur.batch.example;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sun.misc.Signal;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class StopSignalState {

    private static final Logger LOG = LoggerFactory.getLogger(StopSignalState.class);

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    @PostConstruct
    void registerSignalHandlers() {
        register("TERM");
        register("INT");
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    private void register(String signalName) {
        try {
            Signal.handle(new Signal(signalName), signal -> {
                if (stopRequested.compareAndSet(false, true)) {
                    LOG.info("Received {} signal. Requesting graceful Spring Batch stop.", signal.getName());
                }
            });
        } catch (IllegalArgumentException ex) {
            // Some platforms/JVMs do not support all signals.
            LOG.debug("Signal {} not available on this platform/JVM: {}", signalName, ex.getMessage());
        }
    }
}
