package test.inv.batch.configuration.myapp;

import java.io.Serializable;
import java.util.Objects;

public class MyAppBatchVO implements Serializable
{
   private static final long serialVersionUID = 1804511022999346536L;

   private Long kundId;
   private Long typIntyg;
   private String batchFilRad;
   private String batchId;

   public MyAppBatchVO()
   {
      // Tom
   }

   public MyAppBatchVO(final Long kundId, final Long typIntyg, final String batchFilRad)
   {
      this.kundId = kundId;
      this.typIntyg = typIntyg;
      this.batchFilRad = batchFilRad;
      this.batchId = null;
   }

   public Long getKundId()
   {
      return kundId;
   }

   public void setKundId(Long kundId)
   {
      this.kundId = kundId;
   }

   public Long getTypIntyg()
   {
      return typIntyg;
   }

   public void setTypIntyg(Long typIntyg)
   {
      this.typIntyg = typIntyg;
   }

   public String getBatchFilRad()
   {
      return batchFilRad;
   }

   public void setBatchFilRad(String batchFilRad)
   {
      this.batchFilRad = batchFilRad;
   }

   public String getBatchId()
   {
      return batchId;
   }

   public void setBatchId(String batchId)
   {
      this.batchId = batchId;
   }

   @Override
   public boolean equals(Object o)
   {
      if (o == null || getClass() != o.getClass())
         return false;
      MyAppBatchVO that = (MyAppBatchVO) o;
      return Objects.equals(kundId, that.kundId) &&
            Objects.equals(typIntyg, that.typIntyg) &&
            Objects.equals(batchFilRad, that.batchFilRad) &&
            Objects.equals(batchId, that.batchId);
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(kundId, typIntyg, batchFilRad, batchId);
   }

   @Override
   public String toString()
   {
      return "MyAppBatchVO{" +
            "kundId=" + kundId +
            ", typIntyg=" + typIntyg +
            ", batchFilRad='" + batchFilRad + '\'' +
            ", batchId='" + batchId + '\'' +
            '}';
   }
}