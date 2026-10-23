package infrastruktur.batch.javabatch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

   static Stream<Arguments> blankOrNullRequiredValues()
   {
      return Stream.of(
            Arguments.of((Consumer<FelLoggPostVO.Builder>) builder -> builder.anvandare(null), "anvandare"),
            Arguments.of((Consumer<FelLoggPostVO.Builder>) builder -> builder.felLoggNr(""), "felLoggNr"),
            Arguments.of((Consumer<FelLoggPostVO.Builder>) builder -> builder.tjanst("   "), "tjanst"),
            Arguments.of((Consumer<FelLoggPostVO.Builder>) builder -> builder.kortText(null), "kortText"),
            Arguments.of((Consumer<FelLoggPostVO.Builder>) builder -> builder.corrId("\n"), "corrId"),
            Arguments.of((Consumer<FelLoggPostVO.Builder>) builder -> builder.stringData(" "), "stringData"));
   }

   @ParameterizedTest
   @MethodSource("blankOrNullRequiredValues")
   void buildShouldRejectBlankOrNullRequiredFields(Consumer<FelLoggPostVO.Builder> invalidValue, String fieldName)
   {
      FelLoggPostVO.Builder builder = FelLoggPostVO.builder()
            .anvandare("anna")
            .felLoggNr("E123")
            .tjanst("batch-service")
            .kortText("Kunde kunde inte hämtas")
            .corrId("corr-42")
            .stringData("payload");

      invalidValue.accept(builder);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
      assertEquals(fieldName + " must not be blank", ex.getMessage());
   }
}
