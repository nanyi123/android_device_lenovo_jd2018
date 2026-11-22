package com.zui.position.travel.util;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 经纬度逆地理反查地名工具 (Reverse Geocoding Helper)
 * 将 (lat, lng) 坐标反查为人类可读地名（如：合肥市 琥珀街道 · 安徽农业大学体育场）
 */
public class GeocodingHelper {
    public interface Callback {
        void onResult(String placeName);
    }

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public static void reverseGeocode(double lat, double lon, Callback callback) {
        sExecutor.execute(() -> {
            String name = null;
            try {
                String urlStr = String.format(Locale.US,
                        "https://photon.komoot.io/reverse?lat=%.6f&lon=%.6f", lat, lon);
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestProperty("User-Agent", "PositionTravel/1.0");

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();

                    JSONObject json = new JSONObject(sb.toString());
                    JSONArray features = json.optJSONArray("features");
                    if (features != null && features.length() > 0) {
                        JSONObject props = features.getJSONObject(0).optJSONObject("properties");
                        if (props != null) {
                            String poiName = props.optString("name", "").trim();
                            String city = props.optString("city", props.optString("state", "")).trim();
                            String district = props.optString("district", props.optString("locality", "")).trim();
                            String street = props.optString("street", "").trim();

                            if (!poiName.isEmpty()) {
                                name = district.isEmpty() ? (city + " " + poiName) : (city + " " + district + " · " + poiName);
                            } else if (!street.isEmpty()) {
                                name = city + " " + district + " " + street;
                            } else if (!city.isEmpty()) {
                                name = city + " " + district;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            final String finalName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
            sMainHandler.post(() -> {
                if (callback != null) {
                    callback.onResult(finalName);
                }
            });
        });
    }
}
