package test.inv.batch.configuration.myapp;

import test.inv.batch.exception.InvSkipPolicy;
import test.inv.batch.listener.InvJobExecutionListener;
import test.inv.batch.step.myapp.FlyttaBestallningReader;
import test.inv.batch.step.myapp.FlyttaBestallningWriter;
import test.inv.batch.step.myapp.UtfardaKortProcessor;
import test.inv.batch.step.myapp.UtfardaKortReader;
import test.inv.batch.step.myapp.UtfardaKortWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfigurationMyApp implements ApplicationContextAware
{
   private static ApplicationContext context;

   public static synchronized ApplicationContext getApplicationContext()
   {
      return context;
   }

   @Autowired
   PlatformTransactionManager transactionManager;

   @Override
   public synchronized void setApplicationContext(@NonNull ApplicationContext contextParameter)
   {
      context = contextParameter;
   }

   @Autowired
   private JobRepository jobRepository;

   @Bean
   @Qualifier("myAppJob")
   public Job skapaMyAppJob()
   {
      return new JobBuilder("myAppJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(skapaMyAppListener())
            .start(skapaFlyttaBestallningMyAppStep())
            .next(skapaUtfardaMyAppStep())
            .build();
   }

   @Bean(name = "myAppJobLauncher")
   public JobLauncher skapaMyAppJobLauncher() throws Exception
   {
      TaskExecutorJobLauncher taskExecutorJobLauncher = new TaskExecutorJobLauncher();
      taskExecutorJobLauncher.setJobRepository(jobRepository);
      taskExecutorJobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor());
      taskExecutorJobLauncher.afterPropertiesSet();
      return taskExecutorJobLauncher;
   }

   @Bean
   public Step skapaFlyttaBestallningMyAppStep()
   {
      return new StepBuilder("flyttaBestallningMyAppStep", jobRepository).<BestallningVO, BestallningVO> chunk(1,
                  transactionManager)
            .reader(new FlyttaBestallningReader()).faultTolerant().skipPolicy(skapaMyAppSkipPolicy())
            .writer(new FlyttaBestallningWriter()).faultTolerant().skipPolicy(skapaMyAppSkipPolicy()).build();
   }

   @Bean
   public Step skapaUtfardaMyAppStep()
   {
      return new StepBuilder("utfardaMyAppStep", jobRepository).<KortVO, MyAppBatchVO> chunk(1, transactionManager)
            .reader(new UtfardaKortReader()).faultTolerant().skipPolicy(skapaMyAppSkipPolicy())
            .processor(new UtfardaKortProcessor()).faultTolerant().skipPolicy(skapaMyAppSkipPolicy())
            .writer(new UtfardaKortWriter()).faultTolerant().skipPolicy(skapaMyAppSkipPolicy()).build();
   }

   @Bean
   public InvJobExecutionListener skapaMyAppListener()
   {
      return new InvJobExecutionListener();
   }

   @Bean
   public SkipPolicy skapaMyAppSkipPolicy()
   {
      return new InvSkipPolicy("EU-kort");
   }
}
