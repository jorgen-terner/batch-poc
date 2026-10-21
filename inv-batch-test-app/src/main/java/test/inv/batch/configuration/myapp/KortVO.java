package test.inv.batch.configuration.myapp;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

public class KortVO implements Serializable
{
   private static final long serialVersionUID = -5032114667001722406L;

   public static final String UTFARDAT_NEJ = "0";
   public static final String UTFARDAT_JA = "1";

   public static final long INTYGTYP_MYAPP = 0L;
   public static final long INTYGTYP_PROVISORISKT = 1L;

   public static final String KORT_KALLA = "KALLA2";
   public static final String KORT_KALLA_MANUELL_UTREDNING = "´KALLA_MAN";

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

   private Long kundId;
   private Long inloggadKundId;
   private Long typIntyg;
   private String lkNr;
   private Long lopnr;
   private Date fromDat;
   private Date tomDat;
   private String kalla;
   private String kortTyp;
   private String pnr;
   private boolean utfardat;
   private Date skapattstamp;
   private Date tstamp;
   private Integer adressTypId;
   private String batchId;

   public KortVO()
   {
      // Tom
   }

   public Long getKundId()
   {
      return kundId;
   }

   public void setKundId(Long kundId)
   {
      this.kundId = kundId;
      uppdateraIloggKundId();
   }

   public Long getTypIntyg()
   {
      return typIntyg;
   }

   public void setTypIntyg(Long typIntyg)
   {
      this.typIntyg = typIntyg;
   }

   public Long getInloggadKundId()
   {
      return inloggadKundId;
   }

   public void setInloggadKundId(Long inloggadKundId)
   {
      this.inloggadKundId = inloggadKundId;
   }

   public String getLkNr()
   {
      return lkNr;
   }

   public void setLkNr(String lkNr)
   {
      this.lkNr = lkNr;
   }

   public Long getLopnr()
   {
      return lopnr;
   }

   public void setLopnr(Long lopnr)
   {
      this.lopnr = lopnr;
   }

   public Date getFromDat()
   {
      return fromDat;
   }

   public void setFromDat(Date fromDat)
   {
      this.fromDat = fromDat;
   }

   public Date getTomDat()
   {
      return tomDat;
   }

   public void setTomDat(Date tomDat)
   {
      this.tomDat = tomDat;
   }

   public String getKalla()
   {
      return kalla;
   }

   public void setKalla(String kalla)
   {
      this.kalla = kalla;
   }

   public String getKortTyp()
   {
      return kortTyp;
   }

   public void setKortTyp(String kortTyp)
   {
      this.kortTyp = kortTyp;
   }

   public String getPnr()
   {
      return pnr;
   }

   public void setPnr(String pnr)
   {
      this.pnr = pnr;
      uppdateraIloggPersonnummer();
   }

   public boolean isUtfardat()
   {
      return utfardat;
   }

   public void setUtfardat(boolean utfardat)
   {
      this.utfardat = utfardat;
   }

   public Date getSkapattstamp()
   {
      return skapattstamp;
   }

   public void setSkapattstamp(Date skapattstamp)
   {
      this.skapattstamp = skapattstamp;
   }

   public Date getTstamp()
   {
      return tstamp;
   }

   public void setTstamp(Date tstamp)
   {
      this.tstamp = tstamp;
   }

   public Integer getAdressTypId()
   {
      return adressTypId;
   }

   public void setAdressTypId(Integer adressTypId)
   {
      this.adressTypId = adressTypId;
   }

   public String getBatchId()
   {
      return batchId;
   }

   public void setBatchId(String batchId)
   {
      this.batchId = batchId;
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
      if (this.kundId == null)
      {
         this.iloggKundId = null;
      }
      else
      {
         this.iloggKundId = String.valueOf(this.kundId);
      }
   }

   @Override
   public boolean equals(Object o)
   {
      if (o == null || getClass() != o.getClass())
         return false;
      KortVO kortVO = (KortVO) o;
      return utfardat == kortVO.utfardat &&
            Objects.equals(kundId, kortVO.kundId) &&
            Objects.equals(typIntyg, kortVO.typIntyg) &&
            Objects.equals(inloggadKundId, kortVO.inloggadKundId) &&
            Objects.equals(lkNr, kortVO.lkNr) &&
            Objects.equals(lopnr, kortVO.lopnr) &&
            Objects.equals(fromDat, kortVO.fromDat) &&
            Objects.equals(tomDat, kortVO.tomDat) &&
            Objects.equals(kalla, kortVO.kalla) &&
            Objects.equals(kortTyp, kortVO.kortTyp) &&
            Objects.equals(pnr, kortVO.pnr) &&
            Objects.equals(adressTypId, kortVO.adressTypId) &&
            Objects.equals(batchId, kortVO.batchId);
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(kundId, typIntyg, inloggadKundId, lkNr, lopnr, fromDat, tomDat, kalla, kortTyp, pnr,
            utfardat, skapattstamp, tstamp, adressTypId, batchId);
   }

   @Override
   public String toString()
   {
      return "KortVO{" +
            "kundId=" + kundId +
            ", typIntyg=" + typIntyg +
            ", inloggadKundId=" + inloggadKundId +
            ", lkNr='" + lkNr + '\'' +
            ", lopnr=" + lopnr +
            ", fromDat=" + fromDat +
            ", tomDat=" + tomDat +
            ", kalla='" + kalla + '\'' +
            ", kortTyp='" + kortTyp + '\'' +
            ", pnr='" + pnr + '\'' +
            ", utfardat=" + utfardat +
            ", skapattstamp=" + skapattstamp +
            ", tstamp=" + tstamp +
            ", adressTypId=" + adressTypId +
            ", batchId='" + batchId + '\'' +
            '}';
   }
}