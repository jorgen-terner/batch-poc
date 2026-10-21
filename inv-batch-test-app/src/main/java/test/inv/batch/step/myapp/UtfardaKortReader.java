package test.inv.batch.step.myapp;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemReader;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

import test.inv.batch.configuration.myapp.KortVO;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@ComponentScan({ "test.v.impuls.utfardakort.UtfardaKortRestProxy" })
@EntityScan("test.g.domanmodell.vo")
public class UtfardaKortReader implements ItemReader<KortVO>
{
   private Iterator<KortVO> myappIterator;

   @BeforeStep
   public void before(StepExecution stepExecution)
   {
      // Generera batchId
      final String batchId = "EU" + new SimpleDateFormat("yyMMddHHmmss").format(new Date());
      stepExecution.getExecutionContext().putString("MyAppBatchId", batchId);

      final List<KortVO> allaEjUtfardadeKort = new ArrayList<KortVO>();
      KortVO vo = new KortVO();
      vo.setBatchId("42");
      vo.setFromDat(new Date(2026, 1, 1));
      allaEjUtfardadeKort.add(vo);
      if (allaEjUtfardadeKort != null)
      {
         myappIterator = allaEjUtfardadeKort.iterator();
      }
   }

   @Override
   public KortVO read()
   {
      if (myappIterator != null && myappIterator.hasNext())
      {
         return myappIterator.next();
      }
      else
      {
         return null;
      }
   }
}


