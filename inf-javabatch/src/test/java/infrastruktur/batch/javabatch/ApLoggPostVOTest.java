package infrastruktur.fkee.services.apc;

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

class ApLoggPostVOTest
{
   private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (SSS)");

   @Test
   void builderShouldPopulateAllValuesAndSetCurrentTimestamp()
   {
      LocalDateTime before = LocalDateTime.now();

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

      LocalDateTime after = LocalDateTime.now();

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
      assertNotNull(vo.timestamp());
      assertTrue(vo.timestamp().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} \\(\\d{3}\\)"));

      LocalDateTime parsedTimestamp = LocalDateTime.parse(vo.timestamp(), TIMESTAMP_FORMAT);
      assertTrue(!parsedTimestamp.isBefore(before) && !parsedTimestamp.isAfter(after));
   }

   @Test
   void buildShouldAlwaysSetTimestampFromCurrentTime()
   {
      LocalDateTime before = LocalDateTime.now();

      ApLoggPostVO vo = ApLoggPostVO.builder()
            .funktion("process")
            .corrId("corr-88")
            .typ("WARN")
            .apId("app-88")
            .apSteg((short) 3)
            .antal1(12L)
            .antal2(34L)
            .tjanst("svc")
            .version("1.0")
            .meddelande("warning")
            .apTask((short) 9)
            .kalla("call")
            .destination("dest")
            .build();

      LocalDateTime after = LocalDateTime.now();
      LocalDateTime parsedTimestamp = LocalDateTime.parse(vo.timestamp(), TIMESTAMP_FORMAT);

      assertTrue(parsedTimestamp.isAfter(before.minusSeconds(1)) || parsedTimestamp.equals(before));
      assertTrue(parsedTimestamp.isBefore(after.plusSeconds(1)) || parsedTimestamp.equals(after));
   }

   static Stream<Arguments> blankOrNullRequiredValues()
   {
      return Stream.of(
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.funktion(null), "funktion"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.funktion(" "), "funktion"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.corrId(""), "corrId"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.typ("   "), "typ"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.apId(null), "apId"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.tjanst("\t"), "tjanst"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.version(""), "version"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.meddelande(null), "meddelande"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.kalla(" "), "kalla"),
            Arguments.of((Consumer<ApLoggPostVO.Builder>) builder -> builder.destination(""), "destination"));
   }

   @ParameterizedTest
   @MethodSource("blankOrNullRequiredValues")
   void buildShouldRejectBlankOrNullRequiredFields(Consumer<ApLoggPostVO.Builder> invalidValue, String fieldName)
   {
      ApLoggPostVO.Builder builder = ApLoggPostVO.builder()
            .funktion("Hämta")
            .corrId("corr-123")
            .typ("INFO")
            .apId("ap-7")
            .apSteg((short) 1)
            .antal1(1L)
            .antal2(2L)
            .tjanst("batch-job")
            .version("v1")
            .meddelande("ok")
            .apTask((short) 2)
            .kalla("call")
            .destination("dest");

      invalidValue.accept(builder);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
      assertEquals(fieldName + " must not be blank", ex.getMessage());
   }
}
