package test.inv.batch.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;

public class InvSkipPolicy implements SkipPolicy
{
   private Logger logger = LoggerFactory.getLogger(InvSkipPolicy.class);

   private final String namn;

   public InvSkipPolicy()
   {
      this.namn = "INV";
   }

   public InvSkipPolicy(final String namn)
   {
      this.namn = namn;
   }

   @Override
   public boolean shouldSkip(Throwable exception, long skipCount) throws SkipLimitExceededException
   {
      logger.error("Fel vid körning av batch {} error {} skippar", namn, exception.getMessage());
      return true;
   }

   // För testning
   protected void setLogger(final Logger logger)
   {
      this.logger = logger;
   }
}
