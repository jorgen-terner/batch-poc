package test.inv.batch.step.myapp;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import test.inv.batch.configuration.myapp.MyAppBatchVO;
import test.inv.batch.configuration.myapp.KortVO;

@ComponentScan({ "test.v.impuls.utfardakort.UtfardaKortRestProxy" })
@EntityScan("test.g.domanmodell.vo")
@Component
public class UtfardaKortProcessor implements ItemProcessor<KortVO, MyAppBatchVO>
{
   private final Logger logger = LoggerFactory.getLogger(UtfardaKortProcessor.class);
   
   @Override
   public MyAppBatchVO process(@NonNull KortVO kort)
   {
      try
      {
         logger.debug("skapar batch rad för beställning, kundId " + kort.getKundId());
         MyAppBatchVO vo = new MyAppBatchVO();
         vo.setBatchFilRad("2");
         vo.setBatchId("42");
         vo.setKundId(1938011010012L);
         vo.setTypIntyg(2L);
         for (int i = 0; i < 20; i++)
         {
            try
            {
               Thread.sleep(2000);
            }
            catch (InterruptedException e)
            {
               // Do nothing
            }
         }
         return vo;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Kunde inte skapa batch rad för beställning, kundId " + kort.getKundId(), e);
      }
   }
}
