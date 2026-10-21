package test.inv.batch.rest.myapp;

import test.inv.batch.rest.CommonRestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.batch.runtime.BatchStatus;

import org.slf4j.LoggerFactory;
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

/**
 * REST-gränssnitt för att hantera batchjobb.
 */
@RestController
@Api(tags = "Batch för MyApp")
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

   @ApiOperation(value = "Starta en MyAppbatch")
   @GetMapping(value = "/myapp/start", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> start()
   {
      return startBatch();
   }

   @ApiOperation(value = "Hämta status för en batchkörning")
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

   @ApiOperation(value = "Hämta detaljerad summering för en batchkörning")
   @GetMapping(value = "/myapp/summering", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> summary(@RequestParam("execId") String execId)
   {
      return summaryBatch(execId);
   }

   @ApiOperation(value = "Starta om en batchkörning. Omstart av ett jobb stöds för närvarande inte! Denna tjänst kommer returnera felmeddelande.")
   @GetMapping(value = "/myapp/restart", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> restart(@RequestParam("execId") String execId)
   {
      return restartBatch(execId);
   }

   @ApiOperation(value = "Stoppa en batchkörning")
   @GetMapping(value = "/myapp/stop", produces = MediaType.APPLICATION_JSON_VALUE)
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public ResponseEntity<String> stop(@RequestParam("execId") String execId) throws InterruptedException
   {
      return stopBatch(execId);
   }

   @ApiOperation(value = "Överge en batchkörning")
   @GetMapping(value = "/myapp/abandon", produces = MediaType.APPLICATION_JSON_VALUE)
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   public ResponseEntity<String> abandon(@RequestParam("execId") String execId)
   {
      return abandonBatch(execId);
   }
   
   @ApiOperation(value = "Toggla failed mode")
   @GetMapping(value = "/myapp/togglefail", produces = MediaType.APPLICATION_JSON_VALUE)
   public ResponseEntity<String> togglefail()
   {
      isStatusFailed = !isStatusFailed;
      return new ResponseEntity<>("OK", HttpStatus.OK);
   }   
}
