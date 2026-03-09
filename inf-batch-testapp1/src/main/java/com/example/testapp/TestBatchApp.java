package com.example.testapp;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@QuarkusMain
public class TestBatchApp implements QuarkusApplication {

    private static final Logger LOG = Logger.getLogger(TestBatchApp.class);

    @Override
    public int run(String... args) throws Exception {
        LOG.info("=".repeat(60));
        LOG.info("TEST BATCH APPLICATION STARTED");
        LOG.info("Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        LOG.info("=".repeat(60));

        // Simulera lite arbete
        LOG.info("Processing batch job...");
        
        for (int i = 1; i <= 5; i++) {
            LOG.info("Step " + i + " of 5 - Processing...");
            Thread.sleep(2000); // Vänta 2 sekunder
        }

        LOG.info("=".repeat(60));
        LOG.info("TEST BATCH APPLICATION COMPLETED SUCCESSFULLY");
        LOG.info("=".repeat(60));

        return 0; // Exit code 0 = success
    }

    public static void main(String[] args) {
        Quarkus.run(TestBatchApp.class, args);
    }
}
