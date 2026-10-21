package test.inv.batch.step.myapp;

import test.inv.batch.configuration.myapp.MyAppBatchVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@ComponentScan({ "test.v.impuls.utfardakort.UtfardaKortRestProxy" })
@EntityScan("test.g.domanmodell.vo")
public class UtfardaKortWriter implements ItemWriter<MyAppBatchVO>
{
   private final Logger logger = LoggerFactory.getLogger(UtfardaKortWriter.class);

   protected List<String> batchRader;
   //protected UtFilSkrivare utFilSkrivare;
   private String batchId;

   @BeforeStep
   public void before(StepExecution stepExecution)
   {
      batchId = stepExecution.getExecutionContext().getString("MyAppBatchId");

      logger.debug("MyApp, Batch Id: {}", batchId);

      final Date filtidpunkt = new Date();

      final String filnamn = "EU1030." + new SimpleDateFormat("yyyyMMdd.HHmmss").format(filtidpunkt) + ".Sjukvardskort";
      //utFilSkrivare = UtFilSkrivare.skapaSkrivareEu(filnamn);

      batchRader = new ArrayList<>();
      batchRader.add("01 - StartpostSjukförsäkringskort" + new SimpleDateFormat("yyyyMMdd.HH.mm.ss").format(filtidpunkt));
   }

   @AfterStep
   public void after(StepExecution stepExecution)
   {
      batchRader.add("99 - Slutpost" + String.format("%08d", batchRader.size() + 1));

      logger.debug("MyApp, skriver fil");

      try
      {
         //utFilSkrivare.skrivFil(String.join(System.lineSeparator(), batchRader));
      }
      catch (Exception e)
      {
         logger.error("Fel vid skrivning av Eu-kortsfil", e);

         stepExecution.setExitStatus(new ExitStatus(ExitStatus.FAILED.getExitCode(), e.getMessage()));
         return;
      }

      try
      {
         logger.debug("Flaggar alla kort som utfärdade för batchId {}", batchId);
         MyAppBatchVO kortVO = new MyAppBatchVO();
         kortVO.setBatchId(batchId);
         //utfardaKortRestProxy.uppdateraAllaKortSattUtfardatMedBatchIdSystemAnvandare(kortVO);
      }
      catch (Exception e)
      {
         logger.error("Fel vid flaggande alla kort som utfärdade för batchId {}", batchId, e);

         stepExecution.setExitStatus(new ExitStatus(ExitStatus.FAILED.getExitCode(), e.getMessage()));
      }
   }

   @Override
   public void write(Chunk<? extends MyAppBatchVO> rader)
   {
      logger.debug("MyApp, skriver rad");

      for (MyAppBatchVO rad : rader)
      {
         logger.debug("Skriver batch för kundId: {}", rad.getKundId());

         if (rad.getBatchFilRad() != null)
         {
            batchRader.add(rad.getBatchFilRad());
         }

         try
         {
            MyAppBatchVO kortVO = new MyAppBatchVO();
            kortVO.setKundId(rad.getKundId());
            kortVO.setTypIntyg(rad.getTypIntyg());
            kortVO.setBatchId(batchId);
            //utfardaKortRestProxy.uppdateraKortMedBatchIdSystemAnvandare(kortVO);
         }
         catch (Exception e)
         {
            logger.error("Fel vid uppdatering av batchId, kundId {}", rad.getKundId(), e);
         }
      }
   }
}
