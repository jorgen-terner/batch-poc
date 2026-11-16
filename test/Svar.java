
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.IOException;
import java.io.BufferedReader;

public class Svar implements Serializable
{
   @Deprecated //2017-09-14
   static final long serialVersionUID = 6261941819999291145L;

   private final static String INTERNAL_TPURCODE_FIELD = "h.tpurcode";
   private final static String INTERNAL_TPERRNO_FIELD = "h.tperrno";
   private final static String INTERNAL_TPERRNOSTR_FIELD = "h.tperrnostr";
   private final static String INTERNAL_TPERRNODETAIL_FIELD = "h.tperrnodetail";
   private final static String INTERNAL_TPERRNODETAILSTR_FIELD = "h.tperrnodetailstr";
   private final static String INTERNAL_FERROR_FIELD = "h.ferror";
   private final static String INTERNAL_EXCEPTION_FIELD = "h.exception";
   public static String DEFAULT_ENCODING = "ISO-8859-1";

   private Buffer mBuffer;
   private String mRedirectText;
   private int mTpurcode;
   private int mTperrno;
   private String mTperrnoStr;
   private int mTperrnoDetail;
   private String mTperrnoDetailStr;
   private int mFerror;
   private String mFerrorStr;
   private String mExceptionnamn;

   public final Buffer getBuffer()
   {
      return mBuffer;
   }

   public final void setBuffer(Buffer varde)
   {
      mBuffer = varde;
   }

   public final int getTpurcode()
   {
      return mTpurcode;
   }

   public final void setTpurcode(int varde)
   {
      mTpurcode = varde;
   }

   public final int getTperrno()
   {
      return mTperrno;
   }

   public final void setTperrno(int varde)
   {
      mTperrno = varde;
   }

   public final String getRedirectText()
   {
      return mRedirectText;
   }

   public final int getTperrnoDetail()
   {
      return mTperrnoDetail;
   }

   public final void setTperrnoDetail(int varde)
   {
      mTperrnoDetail = varde;
   }

   public final String getTperrnoDetailStr()
   {
      return mTperrnoDetailStr;
   }

   public final void setTperrnoDetailStr(String varde)

   {
      mTperrnoDetailStr = varde;
   }

   public final String getTperrnoStr()
   {
      return mTperrnoStr;
   }

   public final void setTperrnoStr(String varde)
   {
      mTperrnoStr = varde;
   }

   public final String getExceptionnamn()
   {
      return mExceptionnamn;
   }

   public final void setExceptionnamn(String varde)

   {
      mExceptionnamn = varde;
   }

   public final boolean hasException()
   {
      if (mExceptionnamn == null)
      {
         return false;
      }
      else
      {
         return mExceptionnamn.length() != 0;
      }
   }

   public final int getFerror()
   {
      return mFerror;
   }

   public final void setFerror(int varde)

   {
      mFerror = varde;
   }

   public final String getFerrorStr()
   {
      return mFerrorStr;
   }

   public final void setFerrorStr(String varde)
   {
      mFerrorStr = varde;
   }

   private final Integer NOLLA = new Integer(0);

   public void lasSvar(InputStream inputstream, String encoding) throws IOException
   {
      this.lasSvar(new BufferedReader(new InputStreamReader(inputstream, encoding)));
   }

   private void lasSvar(BufferedReader reader) throws IOException
   {
      Buffer fml = new Buffer();

      fml.lasBuffer(reader);

      mTpurcode = ((Integer) fml.getVarde(INTERNAL_TPURCODE_FIELD, NOLLA)).intValue();
      //		fml.remove(INTERNAL_TPURCODE_FIELD);

      mTperrno = ((Integer) fml.getVarde(INTERNAL_TPERRNO_FIELD, NOLLA)).intValue();
      //		fml.remove(INTERNAL_TPERRNO_FIELD);

      mTperrnoStr = (String) (fml.getVarde(INTERNAL_TPERRNOSTR_FIELD, null));
      //		fml.remove(INTERNAL_TPERRNOSTR_FIELD);
      mTperrnoDetail = ((Integer) fml.getVarde(INTERNAL_TPERRNODETAIL_FIELD, NOLLA)).intValue();
      //		fml.remove(INTERNAL_TPERRNODETAIL_FIELD);
      mTperrnoDetailStr = (String) (fml.getVarde(INTERNAL_TPERRNODETAILSTR_FIELD, null));
      //		fml.remove(INTERNAL_TPERRNODETAILSTR_FIELD);

      mFerror = ((Integer) fml.getVarde(INTERNAL_TPERRNODETAIL_FIELD, NOLLA)).intValue();
      //		fml.remove(INTERNAL_TPERRNODETAIL_FIELD);

      mExceptionnamn = (String) (fml.getVarde(INTERNAL_EXCEPTION_FIELD, null));
      //		fml.remove(INTERNAL_EXCEPTION_FIELD);

      setBuffer(fml);
   }

   public void lasHTMLSvar(InputStream inputstream, String encoding) throws IOException
   {
      lasHTMLSvar(new BufferedReader(new InputStreamReader(inputstream, encoding)));
   }

   private void lasHTMLSvar(BufferedReader reader) throws IOException
   {
      StringBuffer innehall = new StringBuffer();
      String rad = null;
      while ((rad = reader.readLine()) != null)
      {
         innehall.append(rad);
         innehall.append(System.getProperty("line.separator"));
      }
      mRedirectText = innehall.toString();
   }

   public void skrivSvar(OutputStream outputstream, String encoding) throws IOException
   {
      Buffer fml = getBuffer();

      if (fml == null)
      {
         fml = new Buffer();
      }

      fml.add(new FMLFalt(INTERNAL_TPURCODE_FIELD, new Integer(mTpurcode)));
      fml.add(new FMLFalt(INTERNAL_TPERRNO_FIELD, new Integer(mTperrno)));

      if (mTperrnoStr != null)
      {
         fml.add(new FMLFalt(INTERNAL_TPERRNOSTR_FIELD, mTperrnoStr));
      }

      fml.add(new FMLFalt(INTERNAL_TPERRNODETAIL_FIELD, new Integer(mTperrnoDetail)));

      if (mTperrnoDetailStr != null)
      {
         fml.add(new FMLFalt(INTERNAL_TPERRNODETAILSTR_FIELD, mTperrnoDetailStr));
      }

      fml.add(new FMLFalt(INTERNAL_FERROR_FIELD, new Integer(mFerror)));

      if (mExceptionnamn != null)
      {
         fml.add(new FMLFalt(INTERNAL_EXCEPTION_FIELD, mExceptionnamn));
      }

      fml.skrivBuffer(outputstream, DEFAULT_ENCODING);
   }

   public String toString()
   {
      StringBuffer sb = new StringBuffer();
      sb.append(" mTpurcode = ");
      sb.append(mTpurcode);

      sb.append(" mTperrno = ");
      sb.append(mTperrno);

      sb.append(" mTperrnoStr = ");
      sb.append(mTperrnoStr);

      sb.append(" mTperrnoDetail = ");
      sb.append(mTperrnoDetail);

      sb.append(" mTperrnoDetailStr = ");
      sb.append(mTperrnoDetailStr);

      sb.append(" mFerror = ");
      sb.append(mFerror);

      sb.append(" mFerrorStr = ");
      sb.append(mFerrorStr);

      sb.append(" mExceptionnamn = ");
      sb.append(mExceptionnamn);

      return sb.toString();
   }
}
