package test.inv.batch.step.myapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemReader;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

import test.inv.batch.configuration.myapp.BestallningVO;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@ComponentScan({ "test.v.impuls.utfardakort.UtfardaKortRestProxy" })
@EntityScan("test.g.domanmodell.vo")
public class FlyttaBestallningReader implements ItemReader<BestallningVO>
{
   private final Logger logger = LoggerFactory.getLogger(FlyttaBestallningReader.class);

   private Iterator<BestallningVO> bestallningIterator;

   @BeforeStep
   public void before(StepExecution ignored)
   {
      final List<BestallningVO> allaBestallningar = new ArrayList<BestallningVO>();
      BestallningVO vo = new BestallningVO();
      vo.setFromdat(new Date(2025, 1, 1));
      vo.setInloggadKundId(1938011010012L);
      vo.setKanal("brev");
      vo.setKorttyp("2");
      allaBestallningar.add(vo);
      if (allaBestallningar != null)
      {
         logger.info("Flyttar beställningar, antal: {}", allaBestallningar.size());
         bestallningIterator = allaBestallningar.iterator();
      }
   }

   @Override
   public BestallningVO read()
   {
      if (bestallningIterator != null && bestallningIterator.hasNext())
      {
         for (int i = 0; i < 5; i++)
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
         return bestallningIterator.next();
      }
      else
      {
         return null;
      }
   }
}
