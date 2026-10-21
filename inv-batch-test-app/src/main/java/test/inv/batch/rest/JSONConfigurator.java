package test.inv.batch.rest;


import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.ws.rs.ext.ContextResolver;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONConfigurator implements ContextResolver<Jsonb>
{
   private static final Logger LOG = LoggerFactory.getLogger(JSONConfigurator.class);
   public static final String DATE_FORMAT_STR = "yyyy-MM-dd'T'HH:mm:ss";
   public static final boolean SERIALIZE_NULL_VALUES = true;

   @Override
   public Jsonb getContext(Class type)
   {
      LOG.debug(
            "Konfigurerar Jsonb: JsonbConfig().withNullValues(%b).withDateFormat(%s)", SERIALIZE_NULL_VALUES, DATE_FORMAT_STR);

      JsonbConfig config = new JsonbConfig()
            .withNullValues(SERIALIZE_NULL_VALUES)
            .withDateFormat(DATE_FORMAT_STR, Locale.getDefault());

      LOG.debug("Jsonb konfiguration:" + config.getAsMap());

      return JsonbBuilder.newBuilder()
            .withConfig(config)
            .build();
   }
}
