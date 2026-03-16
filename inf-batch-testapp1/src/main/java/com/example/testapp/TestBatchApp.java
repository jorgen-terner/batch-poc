package com.example.testapp;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@QuarkusMain
public class TestBatchApp implements QuarkusApplication {

    private static final Logger LOG = LoggerFactory.getLogger(TestBatchApp.class);

    @Override
    public int run(String... args) throws Exception {
        // Läs konfiguration från miljövariabler (som kan komma från ConfigMap)
        int simulationSteps = getEnvAsInt("SIMULATION_STEPS", 5);
        int delaySeconds = getEnvAsInt("SIMULATION_DELAY_SECONDS", 2);
        String logLevel = System.getenv().getOrDefault("LOG_LEVEL", "INFO");
        String batchSize = System.getenv().getOrDefault("BATCH_SIZE", "N/A");
        String runId = System.getenv().getOrDefault("RUN_ID", "default");
        
        LOG.info("=".repeat(60));
        LOG.info("TEST BATCH APPLICATION STARTED");
        LOG.info("Timestamp: {}", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        LOG.info("Run ID: {}", runId);
        LOG.info("Configuration:");
        LOG.info("  - Simulation Steps: {}", simulationSteps);
        LOG.info("  - Delay Between Steps: {} seconds", delaySeconds);
        LOG.info("  - Log Level: {}", logLevel);
        LOG.info("  - Batch Size: {}", batchSize);
        LOG.info("=".repeat(60));

        // Simulera batch job arbete
        LOG.info("Processing batch job...");
        
        for (int i = 1; i <= simulationSteps; i++) {
            LOG.info("Step {} of {} - Processing...", i, simulationSteps);
            Thread.sleep(delaySeconds * 1000L);
        }

        LOG.info("=".repeat(60));
        LOG.info("TEST BATCH APPLICATION COMPLETED SUCCESSFULLY");
        LOG.info("Processed {} steps", simulationSteps);
        LOG.info("=".repeat(60));

        return 0; // Exit code 0 = success
    }

    /**
     * Läs miljövariabel som integer med fallback-värde
     */
    private int getEnvAsInt(String envName, int defaultValue) {
        String value = System.getenv(envName);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid value for {}: {}, using default: {}", envName, value, defaultValue);
            }
        }
        return defaultValue;
    }

    public static void main(String[] args) {
        Quarkus.run(TestBatchApp.class, args);
    }
}
