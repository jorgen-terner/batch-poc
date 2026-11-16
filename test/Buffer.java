
import java.io.*;
import java.util.*;
public final class Buffer implements Serializable
{
    static final long serialVersionUID = -8153296916742601449L;

    private final static int HEX_BREDD = 72;

    private final static String AVGRANSNING = "&"
            + Integer.toHexString((new Random()).nextInt());

    private List<Falt> mFalt = new LinkedList<Falt>();

    @Deprecated
    public void add(Falt falt)
    {
        mFalt.add(falt);
    }

    public Falt getFalt(String namn)
    {
        Iterator<Falt> it = iterator();
        while (it.hasNext())
        {
            Falt f = it.next();
            if (f.getNamn().equals(namn))
            {
                return f;
            }
        }
        return null;
    }

    public Iterator<Falt> iterator()
    {
        return mFalt.iterator();
    }

    public Object getVarde(String namn, Object standard)
    {
        Falt falt = getFalt(namn);
        return (falt == null) ? standard : falt.getVarde();
    }

    public Falt[] getAllaFalt(String namn)
    {
        List<Falt> lista = new LinkedList<Falt>();
        Iterator<Falt> it = iterator();
        while (it.hasNext())
        {
            Falt f = it.next();
            if (f.getNamn().equals(namn))
            {
                lista.add(f);
            }
        }
        return lista.toArray(new Falt[lista.size()]);
    }

    public Object[] getAllaVarden(String namn)
    {
        List<Object> lista = new LinkedList<Object>();

        Iterator<Falt> it = iterator();
        while (it.hasNext())
        {
            Falt f = it.next();

            if (f.getNamn().equals(namn))
            {
                lista.add(f.getVarde());
            }
        }
        return lista.toArray(new Object[lista.size()]);
    }

    public int getAntalFalt(String namn)
    {
        int antal = 0;
        Iterator<Falt> it = iterator();
        while (it.hasNext())
        {
            Falt f = it.next();
            if (f.getNamn().equals(namn))
            {
                antal++;
            }
        }
        return antal;
    }

    public void clear()
    {
        mFalt.clear();
    }

    public void remove(String namn)
    {
        Iterator<Falt> it = iterator();

        while (it.hasNext())
        {
            Falt falt = it.next();
            if (falt.getNamn().equals(namn))
            {
                it.remove();
            }
        }
    }

    public void skrivBuffer(OutputStream outputstream, String encoding)
            throws IOException
    {
        PrintWriter writer;
        if (encoding != null)
        {
            writer = new PrintWriter(
                    new OutputStreamWriter(outputstream, encoding), true);
        }
        else
        {
            writer = new PrintWriter(new OutputStreamWriter(outputstream), true);
        }

        Iterator<Falt> it = iterator();
        while (it.hasNext())
        {
            Falt falt = it.next();
            if (falt.getTypeID().equals(Falt.ID_EMPTY_STRING))
            {
                writer.println(falt.getNamn());
                writer.println(falt.getTypeID());
            }
            else if (falt.getTypeID().equals(Falt.ID_STRING))
            {
                writer.println(falt.getNamn());
                String avgransare = skapaAvgransare(falt);
                writer.println(avgransare);
                writer.write(falt.getVarde().toString());
                writer.println();
                writer.println(avgransare);
            }
            else if (falt.getTypeID().equals(Falt.ID_BYTEARRAY))
            {
                writer.println(falt.getNamn());
                String avgransare = skapaAvgransare(falt);
                writer.println(avgransare);
                writeBytes(writer, (byte[]) falt.getVarde());
                writer.println(avgransare);
            }
            else
            {
                writer.println(falt.getNamn());
                writer.println(falt.getTypeID());
                writer.println(falt.getVarde());
            }
        }
        writer.flush();
    }

    private void writeBytes(PrintWriter writer, byte[] varde)
    {
        final int radlangd = (HEX_BREDD * 3) / 4;
        Base64.Encoder encoder = Base64.getEncoder();

        int start = 0;
        for (; start + radlangd < varde.length; start += radlangd)
        {
            byte[] rad = Arrays.copyOfRange(varde, start, start + radlangd);
            writer.print(encoder.encodeToString(rad));
            writer.print("\n"); 
        }
        byte[] rad = Arrays.copyOfRange(varde, start, varde.length);
        writer.println(encoder.encodeToString(rad));
    }

    public void lasBuffer(BufferedReader reader) throws IOException
    {
        String faltNamn = null;
        String faltTyp = null;

        faltNamn = reader.readLine();

        while (faltNamn != null)
        {
            faltTyp = reader.readLine();
            if(faltTyp == null)
            {
                throw new IOException("Buffer gick inte att tolka som FML. " +
                      "FÃ¤lttyp saknas fÃ¶r fÃ¤lt: " + faltNamn);
            }

            if (faltTyp.startsWith(Falt.ID_STRING))
            {
                mFalt.add(new Falt(faltNamn,
                        lasMellanAvgransare(faltTyp, reader)));
            }
            else if (faltTyp.startsWith(Falt.ID_BYTEARRAY))
            {
                mFalt.add(new Falt(faltNamn, readBytes(faltTyp, reader)));
            }
            else if (faltTyp.equals(Falt.ID_EMPTY_STRING))
            {
                mFalt.add(new Falt(faltNamn, ""));
            }
            else
            {
                String varde = reader.readLine();
                skapaFalt(faltNamn, faltTyp, varde);
            }
            faltNamn = reader.readLine();
        }
    }

    private void skapaFalt(String faltNamn, String faltTyp, String varde)
            throws IOException
    {
        try
        {
            if (faltTyp.equals(Falt.ID_SHORT))
            {
                mFalt.add(new Falt(faltNamn, new Short(Short
                        .parseShort(varde))));
            }
            else if (faltTyp.equals(Falt.ID_FLOAT))
            {
                mFalt.add(new Falt(faltNamn, new Float(Float
                        .parseFloat(varde))));
            }
            else if (faltTyp.equals(Falt.ID_INT))
            {
                mFalt.add(new Falt(faltNamn,
                        new Integer(Integer.parseInt(varde))));
            }
            else if (faltTyp.equals(Falt.ID_DOUBLE))
            {
                mFalt.add(new Falt(faltNamn, new Double(Double
                        .parseDouble(varde))));
            }
            else if (faltTyp.equals(Falt.ID_CHAR))
            {
                mFalt.add(new Falt(faltNamn, new Character(varde.charAt(0))));
            }

        }
        catch (NumberFormatException nfe)
        {
            String felmeddelande = "Exception Typ: " + nfe.toString() + "\n"
                    + "FÃ¤ltnamn: " + faltNamn + "\n" + "FÃ¤lttyp: " + faltTyp
                    + "\n" + "VÃ¤rde: " + varde;
            skrivUtMeddelande(getClass().getName() + ".lasBuffer -> " + felmeddelande);
            throw nfe;
        }
    }


    private void skrivUtMeddelande(String meddelande)
    {
        System.out.println(skapaTidsStampel() + meddelande);
    }

    private String skapaTidsStampel()
    {
        try
        {
            return DateUtil.dateToString("HH:mm:ss.SSS ", DateUtil.currentDate());
        }
        catch (BasException be)
        {
            return "";
        }
    }


    private static byte[] readBytes(String avgransare,
                                    BufferedReader reader) throws IOException
    {
        Base64.Decoder decoder = Base64.getDecoder();
        List<byte[]> data = new LinkedList<>();
        StringBuilder sb = new StringBuilder();

        String rad = reader.readLine();
        while (!rad.equals(avgransare))
        {
            sb.append(rad);
            if (sb.length() > 32_000)
            {
                data.add(decoder.decode(sb.toString()));
                sb.setLength(0);
            }
            rad = reader.readLine();
        }
        data.add(decoder.decode(sb.toString()));
        sb = null;

        byte[] retVal = new byte[data.stream().mapToInt(a -> a.length).sum()];

        int pos = 0;
        while (!data.isEmpty())
        {
            byte[] array = data.remove(0);
            System.arraycopy(array, 0, retVal, pos, array.length);
            pos += array.length;
        }

        return retVal;
    }

    private final static String lasMellanAvgransare(String avgransare,
                                                    BufferedReader reader) throws IOException
    {
        StringBuffer sb = new StringBuffer();
        String rad = reader.readLine();

        while (!rad.equals(avgransare))
        {
            sb.append(rad);
            rad = reader.readLine();
            if (!rad.equals(avgransare))
            {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String skapaAvgransare(Falt falt)
    {
        return falt.getTypeID() + AVGRANSNING;
    }

    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (other == null)
        {
            return false;
        }

        if (other instanceof FMLBuffer)
        {
            FMLBuffer andra = (FMLBuffer) other;
            return mFalt.equals(andra.mFalt);
        }

        return false;
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < mFalt.size(); i++)
        {
            sb.append(mFalt.get(i));
            sb.append("\n");
        }
        return mFalt.toString();
    }
}
