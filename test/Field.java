
import java.io.Serializable;
import java.util.Arrays;

public class Field implements Serializable
{
    static final long serialVersionUID = -4794323776990373712L;

    protected final static String ID_BYTE = "B";
    protected final static String ID_CHAR = "C";
    protected final static String ID_DOUBLE = "D";
    protected final static String ID_FLOAT = "F";
    protected final static String ID_INT = "I";
    protected final static String ID_BYTEARRAY = "A";
    protected final static String ID_STRING = "Z";
    protected final static String ID_EMPTY_STRING = "T";
    protected final static String ID_SHORT = "S";


    private final String mNamn;


    private final Object mVarde;


    public Field(String namn, Object varde)
    {
        mNamn = namn;
        if (varde instanceof Long)
        {
           mVarde = FmlLongToIntUtil.fmlLongToInt((Long)varde, namn);
        }
        else
        {
           mVarde = varde;
        }
    }

    public String getNamn()
    {
        return mNamn;
    }

    public Object getVarde()
    {
        return mVarde;
    }


    public String getTypeID()
    {
        if (mVarde == null)
        {
            throw new RuntimeException("Värde null för  = " + mNamn);
        }

        if (mVarde instanceof String)
        {
            return mVarde.equals("") ? ID_EMPTY_STRING : ID_STRING;
        }
        else if (mVarde instanceof Integer)
        {
            return ID_INT;
        }
        else if (mVarde instanceof Double)
        {
            return ID_DOUBLE;
        }
        else if (mVarde instanceof Float)
        {
            return ID_FLOAT;
        }
        else if (mVarde instanceof byte[])
        {
            return ID_BYTEARRAY;
        }
        else if (mVarde instanceof Character)
        {
            return ID_CHAR;
        }
        else if (mVarde instanceof Short)
        {
            return ID_SHORT;
        }
        else if (mVarde instanceof Byte)
        {
            return ID_BYTE;
        }
        else
        {
            throw new RuntimeException("Typ som inte stödjs : " + mVarde.getClass().getName());
        }
    }
}

