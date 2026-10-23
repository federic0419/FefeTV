package tv.fefetv.home;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Current weather from Open-Meteo (free, no account, no key). The only network use in FefeTV.
 * Call from a background thread.
 */
final class Weather {
    final double temp;
    final int code;
    final boolean day;

    private Weather(double temp, int code, boolean day) {
        this.temp = temp;
        this.code = code;
        this.day = day;
    }

    static Weather fetch(double lat, double lon) throws IOException {
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day",
                lat, lon);
        try {
            JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
            return new Weather(cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    /** Looks up a city name; returns {lat, lon, display name} or null. */
    static Object[] geocode(String city) throws IOException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=it&name="
                + URLEncoder.encode(city, "UTF-8");
        try {
            JSONArray res = new JSONObject(get(url)).optJSONArray("results");
            if (res == null || res.length() == 0) return null;
            JSONObject r = res.getJSONObject(0);
            return new Object[] {r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name")};
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
