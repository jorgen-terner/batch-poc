import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProxyJson
{
    private static final Gson GSON = new Gson();

    private ProxyJson()
    {
    }

    public static String toJsonPayload(Buffer buffer)
    {
        if (buffer == null)
        {
            return "{\"payload\":[],\"messageInstructions\":[]}";
        }

        List<Map<String, Object>> payload = new ArrayList<Map<String, Object>>();
        List<Map<String, String>> messageInstructions = new ArrayList<Map<String, String>>();
        Iterator<Falt> iterator = buffer.iterator();

        while (iterator.hasNext())
        {
            Falt field = iterator.next();
            Object value = field.getVarde();
            Object jsonValue = serializeFieldValue(value);

            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put(field.getNamn(), jsonValue);
            payload.add(item);

            if (value instanceof String || value instanceof byte[])
            {
                Map<String, String> instruction = new LinkedHashMap<String, String>();
                instruction.put("fieldName", field.getNamn());
                instruction.put("direction", "in");
                instruction.put("instruction", "decode");
                messageInstructions.add(instruction);
            }
        }

        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("payload", payload);
        root.put("messageInstructions", messageInstructions);
        return GSON.toJson(root);
    }

    public static Map<String, Object> parseObject(String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return null;
        }

        try
        {
            Type type = new TypeToken<Map<String, Object>>()
            {
            }.getType();
            return GSON.fromJson(json, type);
        }
        catch (JsonParseException ignored)
        {
            return null;
        }
    }

    public static String extractErrorMessage(String responseBody)
    {
        if (responseBody == null || responseBody.trim().isEmpty())
        {
            return null;
        }

        Map<String, Object> root = parseObject(responseBody);
        if (root != null)
        {
            if (root.containsKey("error"))
            {
                return String.valueOf(root.get("error"));
            }
            if (root.containsKey("message"))
            {
                return String.valueOf(root.get("message"));
            }
        }
        return null;
    }

    private static Object serializeFieldValue(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof String)
        {
            return encodeBase64(((String) value).getBytes(StandardCharsets.UTF_8));
        }
        if (value instanceof byte[])
        {
            return encodeBase64((byte[]) value);
        }
        return value;
    }

    private static String encodeBase64(byte[] value)
    {
        return Base64.getEncoder().encodeToString(value);
    }
}
