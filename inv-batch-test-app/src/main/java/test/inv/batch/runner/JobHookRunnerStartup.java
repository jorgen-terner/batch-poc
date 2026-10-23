package test.inv.batch.runner;

import infrastruktur.batch.runner.InfJavabatchRunnerMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "batch.runner.enabled", havingValue = "true")
public class JobHookRunnerStartup implements ApplicationRunner
{
   private static final Logger LOG = LoggerFactory.getLogger(JobHookRunnerStartup.class);

   private final ConfigurableApplicationContext applicationContext;

   public JobHookRunnerStartup(ConfigurableApplicationContext applicationContext)
   {
      this.applicationContext = applicationContext;
   }

   @Override
   public void run(ApplicationArguments args)
   {
      int exitCode = 1;
      try
      {
         LOG.info("batch.runner.enabled=true, starting in-process job hook runner");
         exitCode = InfJavabatchRunnerMain.runToCompletionFromEnvironment();
      }
      catch (InterruptedException ex)
      {
         Thread.currentThread().interrupt();
         LOG.error("Runner interrupted", ex);
      }
      catch (RuntimeException ex)
      {
         LOG.error("Runner failed", ex);
      }

      LOG.info("Runner finished with exit code {}", exitCode);
      final int finalExitCode = exitCode;
      SpringApplication.exit(applicationContext, () -> finalExitCode);
      System.exit(exitCode);
   }
}
