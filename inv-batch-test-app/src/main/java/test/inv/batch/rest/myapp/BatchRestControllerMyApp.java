package test.inv.batch.rest.myapp;

import test.inv.batch.rest.CommonRestController;

import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST-gränssnitt för att hantera batchjobb.
 */
@RestController
@Tag(name = "Batch för MyApp")
public class BatchRestControllerMyApp extends CommonRestController
{
   // För att enkelt kunna testa vad som händer om ett job misslyckas
   private static boolean isStatusFailed = false;
   
   public BatchRestControllerMyApp(@Qualifier("myAppJobLauncher") JobLauncher jobLauncher, @Qualifier("myAppJob") Job job,
         JobOperator jobOperator, JobExplorer jobExplorer)
   {
      super(job,
            jobExplorer,
            jobLauncher,
            jobOperator,
            LoggerFactory.getLogger(BatchRestControllerMyApp.class),
            "MyApp");
   }

   @Operation(summary = "Starta en MyAppbatch")
   @GetMapping(value = "/myapp/start", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> start()
   {
      return startBatch();
   }

   @Operation(summary = "Hämta status för en batchkörning")
   @GetMapping(value = "/myapp/status", produces = MediaType.APPLICATION_JSON_VALUE)
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public ResponseEntity<String> status(@RequestParam("execId") String execId)
   {
      if (isStatusFailed)
      {
         return new ResponseEntity<>(String.valueOf(BatchStatus.FAILED), HttpStatus.OK);
      }
      return statusBatch(execId);
   }

   @Operation(summary = "Hämta detaljerad summering för en batchkörning")
   @GetMapping(value = "/myapp/summering", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> summary(@RequestParam("execId") String execId)
   {
      return summaryBatch(execId);
   }

   @Operation(summary = "Starta om en batchkörning. Omstart av ett jobb stöds för närvarande inte! Denna tjänst kommer returnera felmeddelande.")
   @GetMapping(value = "/myapp/restart", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> restart(@RequestParam("execId") String execId)
   {
      return restartBatch(execId);
   }

   @Operation(summary = "Stoppa en batchkörning")
   @GetMapping(value = "/myapp/stop", produces = MediaType.APPLICATION_JSON_VALUE)
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public ResponseEntity<String> stop(@RequestParam("execId") String execId) throws InterruptedException
   {
      return stopBatch(execId);
   }

   @Operation(summary = "Överge en batchkörning")
   @GetMapping(value = "/myapp/abandon", produces = MediaType.APPLICATION_JSON_VALUE)
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public ResponseEntity<String> abandon(@RequestParam("execId") String execId)
   {
      return abandonBatch(execId);
   }
   
   @Operation(summary = "Toggla failed mode")
   @GetMapping(value = "/myapp/togglefail", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> togglefail()
   {
      isStatusFailed = !isStatusFailed;
      return new ResponseEntity<>("OK", HttpStatus.OK);
   }   
}
