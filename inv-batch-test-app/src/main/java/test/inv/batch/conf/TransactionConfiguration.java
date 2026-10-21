package test.inv.batch.conf;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;

@Configuration
public class TransactionConfiguration
{
   @Autowired
   public TransactionConfiguration(PlatformTransactionManager transactionManager)
   {
      JtaTransactionManager jta = (JtaTransactionManager) transactionManager;
      jta.setAllowCustomIsolationLevels(true);
   }
}


