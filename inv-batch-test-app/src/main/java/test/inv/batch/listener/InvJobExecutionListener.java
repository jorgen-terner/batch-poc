package test.inv.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

public class InvJobExecutionListener implements JobExecutionListener
{
   private Logger logger = LoggerFactory.getLogger(InvJobExecutionListener.class);

   @Override
   public void afterJob(JobExecution jobExecution)
   {
      if (jobExecution.getStatus() == BatchStatus.COMPLETED)
      {
         logger.info("BATCH JOB COMPLETED SUCCESSFULLY");
      }
   }

   // För testning
   protected void setLogger(final Logger logger)
   {
      this.logger = logger;
   }
}
