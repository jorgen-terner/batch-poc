import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Utilityklass för JNDI-uppslag.
 */
public final class Example
{
   private static final Log LOG = Log.getLog(Example.class);
   public static final String JAVA_GLOBAL_CONTEXT = "java:global/";
   public static final String CURRENT_CONTEXT = "";

   private Example()
   {
   }

   /**
    * Finns det en host för applikationsservern konfigurerat antas det att man
    * vill göra ett remote-anrop mot den adressen. Finns ingen host antas man
    * göra ett anrop inom en applikationsserver.
    * <p>
    * Sätter man username så används password om det finns, annars skickas tom
    * sträng.
    *
    * @throws SystemException om en Context inte kan skapas eller om JNDI-uppslag
    *                         misslyckas av ett verkligt fel
    */
   public static <T> Optional<T> lookupJNDIResource(Class<T> klass)
   {
      Objects.requireNonNull(klass, "klass");

      ApplicationServerClientConfig config = new ApplicationServerClientConfig();
      Context context = null;
      try
      {
         context = createContext(config);
         if (config.getServer() != ServerType.WILDFLY)
         {
            throw new SystemException("Fel vid sökning efter JNDI-objekt för en okänd serverkonfiguration.");
         }

         LOG.debug("Uppslag av JNDI på följande klass :: %s", klass.getName());
         return lookupJNDIResourceWildfly(context, klass);
      }
      catch (NamingException e)
      {
         throw new SystemException(buildTechnicalMessage(config), e);
      }
      finally
      {
         closeQuietly(context);
      }
   }

   private static Context createContext(ApplicationServerClientConfig config) throws NamingException
   {
      if (config.isInContainer())
      {
         return new InitialContext();
      }

      config.read();
      Properties properties = config.buildJNDIProperties();
      if (LOG.isDebugEnabled())
      {
         LOG.debug("Properties=%s", properties.toString());
      }
      return new InitialContext(properties);
   }

   private static String buildTechnicalMessage(ApplicationServerClientConfig config)
   {
      if (config.isInContainer())
      {
         return "Kunde ej skapa eller använda InitialContext inom en appserver";
      }

      return StringUtils.formatString(
            "Kunde ej skapa eller använda en InitialContext med följande properties %s mot appservern",
            config.buildJNDIProperties());
   }

   private static <T> Optional<T> lookupJNDIResourceWildfly(Context context, Class<T> klass) throws NamingException
   {
      NamingEnumeration<NameClassPair> list = context.list(JAVA_GLOBAL_CONTEXT);
      try
      {
         while (list.hasMoreElements())
         {
            NameClassPair next = list.next();
            String jndiPath = JAVA_GLOBAL_CONTEXT + next.getName();
            Optional<T> instance = findWildflyJndi(context.lookup(jndiPath), jndiPath, klass);
            if (instance.isPresent())
            {
               return instance;
            }
         }
         return Optional.empty();
      }
      finally
      {
         closeQuietly(list);
      }
   }

   private static <T> Optional<T> findWildflyJndi(Object binding, String jndiPath, Class<T> klass) throws NamingException
   {
      if (klass.isInstance(binding))
      {
         return Optional.of(klass.cast(binding));
      }

      if (!(binding instanceof Context))
      {
         return Optional.empty();
      }

      Context childContext = (Context) binding;

      NamingEnumeration<NameClassPair> list = childContext.list(CURRENT_CONTEXT);
      try
      {
         while (list.hasMoreElements())
         {
            NameClassPair next = list.next();
            String name = next.getName();
            try
            {
               Object candidate = childContext.lookup(name);
               String childPath = jndiPath + "/" + name;
               Optional<T> instance = findWildflyJndi(candidate, childPath, klass);
               if (instance.isPresent())
               {
                  return instance;
               }
            }
            catch (NamingException e)
            {
               if (LOG.isDebugEnabled())
               {
                  LOG.debug("Kunde inte läsa JNDI-nod %s", name);
               }
            }
         }
         return Optional.empty();
      }
      finally
      {
         closeQuietly(list);
         closeQuietly(childContext);
      }
   }

   private static void closeQuietly(NamingEnumeration<?> enumeration)
   {
      if (enumeration == null)
      {
         return;
      }

      try
      {
         enumeration.close();
      }
      catch (NamingException ignored)
      {
         // Best effort.
      }
   }

   private static void closeQuietly(Context context)
   {
      if (context == null)
      {
         return;
      }

      try
      {
         context.close();
      }
      catch (NamingException ignored)
      {
         // Best effort.
      }
   }
}
