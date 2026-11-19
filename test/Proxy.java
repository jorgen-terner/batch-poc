
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class Proxy
{
    private static final Pattern CHARSET_PATTERN = Pattern.compile("^.+charset=([^;]+).*$");
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    private final URL mURL;

    public Proxy(URL url)
    {
        mURL = url;
    }

    public Svar anropa(String tjanst, Buffer buffer) throws IllegalArgumentException, IOException, MalformedURLException, ClassNotFoundException, RFVException
    {
        if (buffer == null)
        {
            throw new IllegalArgumentException("Buffer är null");
        }
        if (tjanst == null)
        {
            throw new IllegalArgumentException("Tjänstnamn är null");
        }

        HttpURLConnection connection = (HttpURLConnection) skapaURL(tjanst).openConnection();
        try
        {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", JSON_CONTENT_TYPE);
            connection.setRequestProperty("Accept", "application/json");

            byte[] payload = ProxyJson.toJsonPayload(buffer).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream())
            {
                out.write(payload);
            }

            int statusCode = connection.getResponseCode();
            String contentType = connection.getContentType();
            String responseBody = readResponseBody(connection, statusCode);

            if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST)
            {
                return felSvar(statusCode, responseBody, contentType);
            }

            return parseResponse(responseBody, contentType);
        }
        finally
        {
            connection.disconnect();
        }
    }

    private Svar parseResponse(String responseBody, String contentType) throws IOException
    {
        if (responseBody == null || responseBody.trim().isEmpty())
        {
            return new Svar();
        }

        if ((contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html"))
                || responseBody.trim().startsWith("<"))
        {
            RFVDebug.visa("HTML-svar mottaget från backend/proxy: " + responseBody);
            Svar svar = new Svar();
            svar.lasHTMLSvar(new ByteArrayInputStream(responseBody.getBytes(UTF_8)), UTF_8.name());
            return svar;
        }

        java.util.Map<String, Object> root = ProxyJson.parseObject(responseBody);
        if (root == null)
        {
            Svar svar = new Svar();
            svar.setTperrnoStr("Ogiltigt svar från backend");
            return svar;
        }

        if (root.containsKey("error"))
        {
            return felSvar(HttpURLConnection.HTTP_OK, String.valueOf(root.get("error")), null);
        }

        if (root.containsKey("response"))
        {
            return toSvar(root.get("response"));
        }

        return toSvar(root);
    }

    private Svar felSvar(int statusCode, String responseBody, String contentType) throws IOException
    {
        if ((contentType != null && contentType.toLowerCase(Locale.ROOT).contains("text/html"))
                || (responseBody != null && responseBody.trim().startsWith("<")))
        {
            Svar svar = new Svar();
            svar.lasHTMLSvar(new ByteArrayInputStream(responseBody.getBytes(UTF_8)), UTF_8.name());
            return svar;
        }

        Svar svar = new Svar();
        String message = ProxyJson.extractErrorMessage(responseBody);
        if (message == null || message.trim().isEmpty())
        {
            message = felmeddelandeFor(statusCode, responseBody);
        }
        svar.setTperrno(statusCode);
        svar.setTperrnoStr(message);
        return svar;
    }

    private Svar toSvar(Object responseValue)
    {
        Svar svar = new Svar();
        Buffer buffer = new Buffer();

        if (responseValue instanceof List)
        {
            for (Object item : (List<?>) responseValue)
            {
                addToBuffer(buffer, item);
            }
        }
        else if (responseValue instanceof Map)
        {
            addToBuffer(buffer, responseValue);
        }

        svar.setBuffer(buffer);
        return svar;
    }

    private void addToBuffer(Buffer buffer, Object value)
    {
        if (value instanceof Map)
        {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                buffer.add(new Falt(String.valueOf(entry.getKey()), toLegacyValue(entry.getValue())));
            }
        }
    }

    private Object toLegacyValue(Object value)
    {
        if (value instanceof Number)
        {
            return normalizeLegacyNumber((Number) value);
        }
        if (value instanceof Boolean || value instanceof String || value instanceof Character)
        {
            return value;
        }
        if (value instanceof List)
        {
            return value;
        }
        if (value instanceof Map)
        {
            return value;
        }
        return String.valueOf(value);
    }

    private Object normalizeLegacyNumber(Number value)
    {
        if (value instanceof Double)
        {
            try
            {
                return Integer.valueOf(BigDecimal.valueOf(value.doubleValue()).intValueExact());
            }
            catch (ArithmeticException ex)
            {
                return value;
            }
        }
        if (value instanceof Float)
        {
            try
            {
                return Integer.valueOf(BigDecimal.valueOf(value.doubleValue()).intValueExact());
            }
            catch (ArithmeticException ex)
            {
                return value;
            }
        }
        return value;
    }

    private String felmeddelandeFor(int statusCode, String responseBody)
    {
        String message = ProxyJson.extractErrorMessage(responseBody);
        if (message != null && !message.trim().isEmpty())
        {
            return message;
        }

        switch (statusCode)
        {
            case HttpURLConnection.HTTP_BAD_REQUEST:
                return "Ett serverfel har uppstått (400)";
            case HttpURLConnection.HTTP_NOT_FOUND:
                return "Ett serverfel har uppstått (404)";
            case HttpURLConnection.HTTP_BAD_METHOD:
                return "Ett serverfel har uppstått (405)";
            case HttpURLConnection.HTTP_INTERNAL_ERROR:
                return "Ett serverfel har uppstått (500)";
            case 504:
                return "Tjänstanropet avbröts eftersom det tog för lång tid (504)";
            case 507:
                return "Ett serverfel har uppstått (507)";
            default:
                return "Ett serverfel har uppstått (" + statusCode + ")";
        }
    }

    private String readResponseBody(HttpURLConnection connection, int statusCode) throws IOException
    {
        InputStream stream = null;
        try
        {
            stream = statusCode >= HttpURLConnection.HTTP_BAD_REQUEST ? connection.getErrorStream() : connection.getInputStream();
        }
        catch (IOException ignored)
        {
            stream = connection.getErrorStream();
        }

        if (stream == null)
        {
            return "";
        }

        String charset = getCharsetFranContentType(connection.getContentType());
        if (charset == null)
        {
            charset = UTF_8.name();
        }

        try (Reader reader = new InputStreamReader(stream, charset))
        {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            for (int read; (read = reader.read(buffer)) != -1; )
            {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    private URL skapaURL(String tjanst) throws MalformedURLException
    {
        String servlet = getServlet();
        String urlText = mURL.toString() + servlet + "?tjanst=" + tjanst;
        RFVDebug.visa("Servletens url: " + urlText);
        return new URL(urlText);
    }

    private String getServlet()
    {
        String servlet = Konstanter.getProperty(StandardVarden.SERVLET_CONTEXT_ROOT,
                StandardVarden.DEFAULT_SERVLET_CONTEXT_ROOT);
        RFVDebug.visa(" contextroot: " + servlet);
        if (!servlet.startsWith("/"))
        {
            servlet = "/" + servlet;
        }
        return servlet;
    }

    public URL getMURL()
    {
        return mURL;
    }

    public static String getCharsetFranContentType(String contentType)
    {
        if (contentType == null)
        {
            return null;
        }

        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (matcher.matches())
        {
            return matcher.group(1).trim();
        }
        return null;
    }
}
