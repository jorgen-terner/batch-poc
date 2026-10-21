package test.inv.batch.step.myapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

import test.inv.batch.configuration.myapp.BestallningVO;

@ComponentScan({ "test.v.impuls.utfardakort.UtfardaKortRestProxy" })
@EntityScan("test.g.domanmodell.vo")
public class FlyttaBestallningWriter implements ItemWriter<BestallningVO>
{
   private final Logger logger = LoggerFactory.getLogger(FlyttaBestallningWriter.class);

   @Override
   public void write(Chunk<? extends BestallningVO> chunk)
   {
      chunk.forEach(this::flyttaBestallning);
   }

   private void flyttaBestallning(final BestallningVO item)
   {
      try
      {
         logger.debug("Flyttar beställning för kundId: {}", item.getKundid());
      }
      catch (Exception e)
      {
         logger.error("Kunde inte flytta beställning för kundId: {}", item.getKundid(), e);
      }
   }
}
