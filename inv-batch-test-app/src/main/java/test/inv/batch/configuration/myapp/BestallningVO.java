package test.inv.batch.configuration.myapp;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

public class BestallningVO implements Serializable
{
   private static final long serialVersionUID = 8521240248642539525L;

   /**
    * Endast för ILoggning av personnummer.
    */
   @SuppressWarnings("unused")
   private String iLoggPersonnummer;

   /**
    * Endast för ILoggning av kundId.
    */
   @SuppressWarnings("unused")
   private String iloggKundId;

   private String pnr;
   private Long kundid;
   private Long inloggadKundId;
   private Date fromdat;
   private Date tomdat;
   private String kanal;
   private String korttyp;
   private Date bestallningsDatum;

   public BestallningVO(Long kundid, Long inloggadKundId, Date fromdat, Date tomdat, String kanal, String korttyp, Date tstamp)
   {
      this(null, kundid, inloggadKundId,fromdat, tomdat, kanal, korttyp, tstamp);
   }

   public BestallningVO(String pnr, Long kundid, Long inloggadKundId,Date fromdat, Date tomdat, String kanal, String korttyp, Date tstamp)
   {
      super();
      this.pnr = pnr;
      this.kundid = kundid;
      this.inloggadKundId = inloggadKundId;
      this.fromdat = fromdat;
      this.tomdat = tomdat;
      this.kanal = kanal;
      this.korttyp = korttyp;
      this.bestallningsDatum = tstamp;
   }

   public BestallningVO()
   {
   }

   public String getPnr()
   {
      return this.pnr;
   }

   public void setPnr(String pnr)
   {
      this.pnr = pnr;
      uppdateraIloggPersonnummer();
   }

   public Long getKundid()
   {
      return this.kundid;
   }

   public void setKundid(Long kundid)
   {
      this.kundid = kundid;
      uppdateraIloggKundId();
   }

   public Long getInloggadKundId()
   {
      return inloggadKundId;
   }

   public void setInloggadKundId(Long inloggadKundId)
   {
      this.inloggadKundId = inloggadKundId;
   }

   public Date getFromdat()
   {
      return this.fromdat;
   }

   public void setFromdat(Date fromdat)
   {
      this.fromdat = fromdat;
   }

   public Date getTomdat()
   {
      return this.tomdat;
   }

   public void setTomdat(Date tomdat)
   {
      this.tomdat = tomdat;
   }

   public String getKanal()
   {
      return this.kanal;
   }

   public void setKanal(String kanal)
   {
      this.kanal = kanal;
   }

   public String getKorttyp()
   {
      return this.korttyp;
   }

   public void setKorttyp(String korttyp)
   {
      this.korttyp = korttyp;
   }

   public Date getBestallningsDatum()
   {
      return this.bestallningsDatum;
   }

   public void setBestallningsDatum(Date bestallningsDatum)
   {
      this.bestallningsDatum = bestallningsDatum;
   }

   private void uppdateraIloggPersonnummer()
   {
      if (this.pnr == null)
      {
         this.iLoggPersonnummer = null;
      }
      else
      {
         this.iLoggPersonnummer = this.pnr;
      }
   }

   private void uppdateraIloggKundId()
   {
      if (this.kundid == null)
      {
         this.iloggKundId = null;
      }
      else
      {
         this.iloggKundId = String.valueOf(this.kundid);
      }
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(pnr, kundid, inloggadKundId, fromdat, tomdat, kanal, korttyp, bestallningsDatum);
   }

   @Override
   public boolean equals(Object o)
   {
      if (o == null || getClass() != o.getClass())
         return false;
      BestallningVO that = (BestallningVO) o;
      return Objects.equals(pnr, that.pnr) && Objects.equals(kundid, that.kundid) && Objects.equals(
            inloggadKundId, that.inloggadKundId) && Objects.equals(fromdat, that.fromdat) && Objects.equals(
            tomdat, that.tomdat) && Objects.equals(kanal, that.kanal) && Objects.equals(korttyp,
            that.korttyp);
   }

   @Override
   public String toString()
   {
      return "BestallningVO{" +
            "pnr='" + pnr + '\'' +
            ", kundid=" + kundid +
            ", inloggadKundId=" + inloggadKundId +
            ", fromdat=" + fromdat +
            ", tomdat=" + tomdat +
            ", kanal='" + kanal + '\'' +
            ", korttyp='" + korttyp + '\'' +
            ", bestallningsDatum=" + bestallningsDatum +
            '}';
   }
}