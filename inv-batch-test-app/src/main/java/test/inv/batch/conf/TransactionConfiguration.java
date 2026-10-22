package test.inv.batch.conf;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TransactionConfiguration
{
   @Autowired
   public TransactionConfiguration(PlatformTransactionManager transactionManager)
   {
      if (transactionManager instanceof JtaTransactionManager jtaTransactionManager)
      {
         jtaTransactionManager.setAllowCustomIsolationLevels(true);
      }
      if (transactionManager instanceof AbstractPlatformTransactionManager abstractTransactionManager)
      {
         abstractTransactionManager.setValidateExistingTransaction(false);
      }
   }
}
