import com.google.gson.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * ForecastApp showing forecast in Fahrenheit, with 30-minute caching.
 */
public class ForecastApp extends JFrame {
    private JTextField zipField;
    private JLabel output;
    private JLabel source;
    private static final long CACHE_TIME = 30 * 60 * 1000; // 30 min
    private static final Map<String, Cache> cache = new HashMap<>();

    public ForecastApp() {
        setTitle("Weather App");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        JPanel top = new JPanel();
        zipField = new JTextField(10);
        JButton btn = new JButton("Get Weather");
        top.add(new JLabel("ZIP:"));
        top.add(zipField);
        top.add(btn);

        output = new JLabel("Temperature: -- °F", SwingConstants.CENTER);
        source = new JLabel("", SwingConstants.CENTER);
        source.setForeground(Color.GRAY);

        add(top, BorderLayout.NORTH);
        add(output, BorderLayout.CENTER);
        add(source, BorderLayout.SOUTH);

        btn.addActionListener(this::fetchWeather);

        setSize(400,180);
        setLocationRelativeTo(null);
    }

    private void fetchWeather(ActionEvent e) {
        String zip = zipField.getText().trim();
        if (zip.isEmpty()) {
            JOptionPane.showMessageDialog(this,"Enter a ZIP code");
            return;
        }
        output.setText("Loading...");
        source.setText("");

        new Thread(() -> {
            try {
                Forecast f = getForecast(zip);
                SwingUtilities.invokeLater(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("<html>Current: %.1f °F<br>", f.currentF));
                    sb.append(String.format("Today High: %.1f °F, Low: %.1f °F<br><br>", f.todayHighF, f.todayLowF));
                    sb.append("<b>Next 3 days:</b><br>");
                    for (int i = 0; i < f.nextDays.size(); i++) {
                        DayForecast d = f.nextDays.get(i);
                        sb.append(String.format("%s → High: %.1f °F, Low: %.1f °F<br>", d.date, d.highF, d.lowF));
                    }
                    sb.append("</html>");
                    output.setText(sb.toString());
                    source.setText(f.cached ? "⚡ From cache" : "☁️ Live data");
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() ->
                        output.setText("Failed to load weather"));
            }
        }).start();
    }


    private Forecast getForecast(String zip) throws IOException {
        long now = System.currentTimeMillis();
        if (cache.containsKey(zip) && now - cache.get(zip).time < CACHE_TIME) {
            Forecast cachedForecast = cache.get(zip).forecast;
            // return a new Forecast object with cached = true
            return new Forecast(
                    cachedForecast.currentF,
                    cachedForecast.todayHighF,
                    cachedForecast.todayLowF,
                    cachedForecast.nextDays,
                    true  // <-- mark as cached
            );
        }

        // --- 1. Get latitude/longitude ---
        URL geoUrl = new URL("https://api.zippopotam.us/us/" + zip);
        HttpURLConnection geoCon = (HttpURLConnection) geoUrl.openConnection();
        geoCon.setConnectTimeout(8000);
        geoCon.setReadTimeout(8000);
        if (geoCon.getResponseCode() != 200)
            throw new IOException("Invalid ZIP");
        String geoJsonStr = new Scanner(geoCon.getInputStream()).useDelimiter("\\A").next();
        JsonObject geoJson = JsonParser.parseString(geoJsonStr).getAsJsonObject();
        JsonObject place = geoJson.getAsJsonArray("places").get(0).getAsJsonObject();
        double lat = Double.parseDouble(place.get("latitude").getAsString());
        double lon = Double.parseDouble(place.get("longitude").getAsString());

        // --- 2. Fetch current + daily forecast ---
        String url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true&daily=temperature_2m_max,temperature_2m_min&timezone=auto",
                lat, lon);
        HttpURLConnection weatherCon = (HttpURLConnection) new URL(url).openConnection();
        weatherCon.setConnectTimeout(8000);
        weatherCon.setReadTimeout(8000);
        if (weatherCon.getResponseCode() != 200)
            throw new IOException("Weather API error");

        String weatherJsonStr = new Scanner(weatherCon.getInputStream()).useDelimiter("\\A").next();
        JsonObject root = JsonParser.parseString(weatherJsonStr).getAsJsonObject();
        JsonObject cur = root.getAsJsonObject("current_weather");
        double tempC = cur.get("temperature").getAsDouble();
        double tempF = (tempC * 9 / 5) + 32;

        JsonObject daily = root.getAsJsonObject("daily");
        JsonArray dates = daily.getAsJsonArray("time");
        JsonArray highs = daily.getAsJsonArray("temperature_2m_max");
        JsonArray lows = daily.getAsJsonArray("temperature_2m_min");

        double todayHighF = (highs.get(0).getAsDouble() * 9 / 5) + 32;
        double todayLowF = (lows.get(0).getAsDouble() * 9 / 5) + 32;

        List<DayForecast> days = new ArrayList<>();
        for (int i = 1; i < Math.min(4, dates.size()); i++) { // next 3 days
            String date = dates.get(i).getAsString();
            double highF = (highs.get(i).getAsDouble() * 9 / 5) + 32;
            double lowF = (lows.get(i).getAsDouble() * 9 / 5) + 32;
            days.add(new DayForecast(date, highF, lowF));
        }

        Forecast f = new Forecast(tempF, todayHighF, todayLowF, days, false);
        cache.put(zip, new Cache(f, now));
        return f;
    }


    private record DayForecast(String date, double highF, double lowF) {}
    private record Forecast(double currentF, double todayHighF, double todayLowF, List<DayForecast> nextDays, boolean cached) {}
    private record Cache(Forecast forecast, long time) {}


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ForecastApp().setVisible(true));
    }
}
