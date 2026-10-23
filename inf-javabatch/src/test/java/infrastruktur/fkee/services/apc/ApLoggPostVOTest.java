package infrastruktur.fkee.services.apc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApLoggPostVOTest
{
   @Test
   void builderShouldRejectBlankRequiredFields()
   {
      IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ApLoggPostVO.builder()
                  .funktion("Hämta")
                  .corrId("corr-123")
                  .typ("INFO")
                  .apId("ap-7")
                  .apSteg((short) 1)
                  .antal1(1L)
                  .antal2(2L)
                  .tjanst("batch-job")
                  .version("v1")
                  .meddelande(" ")
                  .apTask((short) 2)
                  .kalla("call")
                  .destination("dest")
                  .build());

      assertEquals("meddelande must not be blank", ex.getMessage());
   }

   @Test
   void builderShouldPopulateAllRequiredFields()
   {
      ApLoggPostVO vo = ApLoggPostVO.builder()
            .funktion("Hämta")
            .corrId("corr-123")
            .typ("INFO")
            .apId("ap-7")
            .apSteg((short) 5)
            .antal1(42L)
            .antal2(99L)
            .tjanst("batch-job")
            .version("v1.2.3")
            .meddelande("Meddelande")
            .apTask((short) 11)
            .kalla("kalla-1")
            .destination("dest-1")
            .build();

      assertEquals("Hämta", vo.funktion());
      assertEquals("corr-123", vo.corrId());
      assertEquals("INFO", vo.typ());
      assertEquals("ap-7", vo.apId());
      assertEquals((short) 5, vo.apSteg());
      assertEquals(42L, vo.antal1());
      assertEquals(99L, vo.antal2());
      assertEquals("batch-job", vo.tjanst());
      assertEquals("v1.2.3", vo.version());
      assertEquals("Meddelande", vo.meddelande());
      assertEquals((short) 11, vo.apTask());
      assertEquals("kalla-1", vo.kalla());
      assertEquals("dest-1", vo.destination());
      assertTrue(vo.timestamp().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\(\\d{3}\\)"));
   }
}
