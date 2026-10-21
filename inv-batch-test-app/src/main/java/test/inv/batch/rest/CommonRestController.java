package test.inv.batch.rest;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CommonRestController
{
   private final Job job;
   private final JobExplorer jobExplorer;
   private final JobLauncher jobLauncher;
   private final JobOperator jobOperator;
   private final Logger logger;
   private final String namn;

   public CommonRestController(final Job job, final JobExplorer jobExplorer, final JobLauncher jobLauncher,
         final JobOperator jobOperator, final Logger logger, final String namn)
   {
      this.job = job;
      this.jobExplorer = jobExplorer;
      this.jobLauncher = jobLauncher;
      this.jobOperator = jobOperator;
      this.logger = logger;
      this.namn = namn;
   }

   protected ResponseEntity<String> startBatch()
   {
      try
      {
         logger.debug("Startar jobb för {}", namn);

         JobParameters jobParameters = new JobParametersBuilder().addLong("time", System.currentTimeMillis()).toJobParameters();

         SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd.HHmmss");
         String starttime = format.format(new Date());
         MDC.put("jobid", String.format("%s.jbatch.%s", "Forlikning", starttime));

         JobExecution execution = jobLauncher.run(job, jobParameters);
         logger.debug("Startat jobb: {}. Starttid: {}.", job.getName(), execution.getStartTime());

         return new ResponseEntity<>(String.valueOf(execution.getId()), HttpStatus.ACCEPTED);
      }
      catch (Exception e)
      {
         logger.error("Problem inträffade vid start av jobb.", e);
         return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
      }
   }

   protected ResponseEntity<String> statusBatch(String execId)
   {
      JobExecution jobExecution = jobExplorer.getJobExecution(Long.valueOf(execId));
      if (jobExecution == null)
      {
         return new ResponseEntity<>("Hittar inte execution Id " + execId, HttpStatus.NOT_FOUND);
      }
      return new ResponseEntity<>(String.valueOf(jobExecution.getStatus()), HttpStatus.OK);
   }

   protected ResponseEntity<String> summaryBatch(String execId)
   {
      try
      {
         String summering = jobOperator.getSummary(Long.parseLong(execId));
         return new ResponseEntity<>(summering, HttpStatus.OK);
      }
      catch (Exception e)
      {
         logger.error(e.getMessage());
         return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
      }
   }

   protected ResponseEntity<String> restartBatch(String execId)
   {
      try
      {
         long executionId = jobOperator.restart(Long.parseLong(execId));

         return new ResponseEntity<>(String.valueOf(executionId), HttpStatus.OK);
      }
      catch (Exception e)
      {
         logger.error(e.getMessage());
         return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
      }
   }

   protected ResponseEntity<String> stopBatch(String execId) throws InterruptedException
   {
      try
      {
         jobOperator.stop(Long.parseLong(execId));
         Thread.sleep(1000);

         JobExecution jobExecution = jobExplorer.getJobExecution(Long.parseLong(execId));
         if (jobExecution == null)
         {
            throw new NoSuchJobExecutionException("Ingen JobExecution hittades");
         }

         String status = jobExecution.getExitStatus().getExitCode();
         return new ResponseEntity<>(String.format("Status för jobb med execId %s är %s. " +
               "Kolla loggen om det inte är förväntad status, jobbet kan ha stoppats efter return", execId, status),
               HttpStatus.OK);
      }
      catch (InterruptedException ie)
      {
         logger.error("Timeout fel vid stoppande av batch", ie);
         throw ie;
      }
      catch (Exception e)
      {
         logger.error(e.getMessage());
         return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
      }
   }

   protected ResponseEntity<String> abandonBatch(String execId)
   {
      try
      {
         logger.debug("Försöker överge jobb med executionID: {}", execId);
         jobOperator.abandon(Long.parseLong(execId));
         return new ResponseEntity<>("Överger jobb...", HttpStatus.ACCEPTED);
      }
      catch (Exception e)
      {
         logger.error(e.getMessage());
         return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
      }
   }
}
