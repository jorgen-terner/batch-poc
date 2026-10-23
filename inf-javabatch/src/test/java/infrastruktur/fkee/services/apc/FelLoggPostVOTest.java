package infrastruktur.batch.javabatch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

class FelLoggPostVOTest
{
   private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (SSS)");

   @Test
   void buildShouldPopulateAllFieldsAndSetTimestamp()
   {
      FelLoggPostVO vo = FelLoggPostVO.builder()
            .anvandare("anna")
            .felLoggNr("E123")
            .tjanst("batch-service")
            .kortText("Kunde kunde inte hämtas")
            .corrId("corr-42")
            .stringData("{\"key\":\"value\"}")
            .build();

      assertNotNull(vo);
      assertEquals("anna", vo.anvandare());
      assertEquals("E123", vo.felLoggNr());
      assertEquals("batch-service", vo.tjanst());
      assertEquals("Kunde kunde inte hämtas", vo.kortText());
      assertEquals("corr-42", vo.corrId());
      assertEquals("{\"key\":\"value\"}", vo.stringData());
      assertDoesNotThrow(() -> LocalDateTime.parse(vo.timestamp(), TIMESTAMP_FORMAT));
   }

   @Test
   void buildShouldCreateTimestampOnCurrentTimeInExpectedFormat()
   {
      String before = LocalDateTime.now().format(TIMESTAMP_FORMAT);

      FelLoggPostVO vo = FelLoggPostVO.builder()
            .anvandare("bengt")
            .felLoggNr("F99")
            .tjanst("job")
            .kortText("Kort meddelande")
            .corrId("id-7")
            .stringData("payload")
            .build();

      String after = LocalDateTime.now().format(TIMESTAMP_FORMAT);

      assertTrue(vo.timestamp().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\(\\d{3}\\)"));
      assertTrue(vo.timestamp().compareTo(before) >= 0);
      assertTrue(vo.timestamp().compareTo(after) <= 0);
   }

   @Test
   void buildShouldRejectBlankRequiredFields()
   {
      IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> FelLoggPostVO.builder()
                  .anvandare("anna")
                  .felLoggNr("E123")
                  .tjanst("batch-service")
                  .kortText("   ")
                  .corrId("corr-42")
                  .stringData("payload")
                  .build());

      assertEquals("kortText must not be blank", ex.getMessage());
   }
}
