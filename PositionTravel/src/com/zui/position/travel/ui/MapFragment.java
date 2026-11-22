package com.zui.position.travel.ui;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zui.position.travel.R;
import com.zui.position.travel.model.TravelConfig;
import com.zui.position.travel.util.CoordinateUtils;
import com.zui.position.travel.util.GeocodingHelper;
import com.zui.position.travel.util.TravelIpcHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MapFragment extends Fragment {
    private WebView mWebView;
    private TravelConfig mConfig;
    private EditText mEtLat;
    private EditText mEtLng;
    private EditText mEtSearch;
    private ProgressBar mProgressSearch;
    private TextView mTvSelectedName;

    private static class SearchResult {
        final String name;
        final String desc;
        final double lat;
        final double lng;

        SearchResult(String name, String desc, double lat, double lng) {
            this.name = name;
            this.desc = desc;
            this.lat = lat;
            this.lng = lng;
        }

        @NonNull
        @Override
        public String toString() {
            return desc.isEmpty() ? name : name + " (" + desc + ")";
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        mConfig = new TravelConfig(requireContext());

        FrameLayout mapContainer = view.findViewById(R.id.map_container);
        mEtLat = view.findViewById(R.id.et_latitude);
        mEtLng = view.findViewById(R.id.et_longitude);
        mEtSearch = view.findViewById(R.id.et_map_search);
        mProgressSearch = view.findViewById(R.id.progress_map_search);
        mTvSelectedName = view.findViewById(R.id.tv_map_selected_name);

        initMapContainer(mapContainer);

        mEtLat.setText(String.format(Locale.US, "%.6f", mConfig.getLatitude()));
        mEtLng.setText(String.format(Locale.US, "%.6f", mConfig.getLongitude()));

        String currentName = mConfig.getLocationName();
        if (currentName.isEmpty() || "精确坐标输入".equals(currentName) || "地图点选位置".equals(currentName)) {
            mTvSelectedName.setText("解析地名中...");
            GeocodingHelper.reverseGeocode(mConfig.getLatitude(), mConfig.getLongitude(), placeName -> {
                if (isAdded()) {
                    String finalName = placeName != null ? placeName : String.format(Locale.US, "位置 (%.4f, %.4f)", mConfig.getLatitude(), mConfig.getLongitude());
                    mConfig.setCoordinates(mConfig.getLatitude(), mConfig.getLongitude(), finalName);
                    if (mTvSelectedName != null) {
                        mTvSelectedName.setText(finalName);
                    }
                }
            });
        } else {
            mTvSelectedName.setText(currentName);
        }

        // 浮动当前位置定位按钮 (回到已保存的穿越目标)
        View fabCurrent = view.findViewById(R.id.fab_current_location);
        if (fabCurrent != null) {
            fabCurrent.setOnClickListener(v -> {
                flyTo(mConfig.getLatitude(), mConfig.getLongitude());
                Toast.makeText(requireContext(), "已回准当前位置", Toast.LENGTH_SHORT).show();
            });
        }

        // 精确坐标手动应用按钮
        view.findViewById(R.id.btn_apply_coord).setOnClickListener(v -> {
            try {
                double lat = Double.parseDouble(mEtLat.getText().toString());
                double lng = Double.parseDouble(mEtLng.getText().toString());
                if (mTvSelectedName != null) {
                    mTvSelectedName.setText("解析地名中...");
                }
                applyPreset(lat, lng, "精确坐标输入");
                GeocodingHelper.reverseGeocode(lat, lng, placeName -> {
                    if (isAdded()) {
                        String finalName = placeName != null ? placeName : String.format(Locale.US, "位置 (%.4f, %.4f)", lat, lng);
                        mConfig.setCoordinates(lat, lng, finalName);
                        if (mTvSelectedName != null) {
                            mTvSelectedName.setText(finalName);
                        }
                    }
                });
                Toast.makeText(requireContext(), "坐标已更新", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "请输入有效的经纬度数值", Toast.LENGTH_SHORT).show();
            }
        });

        // 搜索框回车/搜索按键监听
        mEtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                performSearch(mEtSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        // 预设城市点击
        setupPresets(view);

        return view;
    }

    private void performSearch(String query) {
        if (query.isEmpty()) return;

        // 1. 优先尝试直接匹配经纬度数值 (如: "39.908722, 116.397496" 或 "39.908 116.397")
        String[] parts = query.split("[,，\\s]+");
        if (parts.length == 2) {
            try {
                double lat = Double.parseDouble(parts[0]);
                double lng = Double.parseDouble(parts[1]);
                if (Math.abs(lat) <= 90.0 && Math.abs(lng) <= 180.0) {
                    applyPreset(lat, lng, "搜索坐标位置");
                    return;
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. 地理编码搜索 (Photon OpenStreetMap 全球免费极速搜索)
        mProgressSearch.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().execute(() -> {
            List<SearchResult> results = new ArrayList<>();
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
                String urlStr = "https://photon.komoot.io/api/?q=" + encoded + "&limit=5";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
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
                    if (features != null) {
                        for (int i = 0; i < features.length(); i++) {
                            JSONObject feat = features.getJSONObject(i);
                            JSONObject geom = feat.optJSONObject("geometry");
                            JSONObject props = feat.optJSONObject("properties");
                            if (geom == null || props == null) continue;

                            JSONArray coords = geom.optJSONArray("coordinates");
                            if (coords == null || coords.length() < 2) continue;

                            double rawLng = coords.getDouble(0);
                            double rawLat = coords.getDouble(1);

                            // Photon API 默认返回 WGS-84 标准坐标，直接存储为 WGS-84
                            String name = props.optString("name", query);
                            String city = props.optString("city", props.optString("state", ""));
                            String district = props.optString("district", props.optString("locality", ""));
                            String desc = (city + " " + district).trim();

                            results.add(new SearchResult(name, desc, rawLat, rawLng));
                        }
                    }
                }
            } catch (Exception e) {
                // 网络或解析异常
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                mProgressSearch.setVisibility(View.GONE);
                if (!isAdded()) return;

                if (results.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.location_not_found, Toast.LENGTH_SHORT).show();
                } else if (results.size() == 1) {
                    SearchResult single = results.get(0);
                    applyPreset(single.lat, single.lng, single.name);
                } else {
                    // 多条结果弹出选择列表
                    String[] titles = new String[results.size()];
                    for (int i = 0; i < results.size(); i++) {
                        titles[i] = results.get(i).toString();
                    }
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.select_search_result)
                            .setItems(titles, (dialog, which) -> {
                                SearchResult item = results.get(which);
                                applyPreset(item.lat, item.lng, item.name);
                            })
                            .show();
                }
            });
        });
    }

    private void hideKeyboard() {
        if (getActivity() == null || getView() == null) return;
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
        }
    }

    private void applyGcjPreset(double gcjLat, double gcjLng, String name) {
        double[] wgs = CoordinateUtils.gcj02ToWgs84(gcjLat, gcjLng);
        applyPreset(wgs[0], wgs[1], name);
    }

    private void setupPresets(View root) {
        root.findViewById(R.id.chip_beijing).setOnClickListener(v -> applyGcjPreset(39.908722, 116.397496, "北京 天安门"));
        root.findViewById(R.id.chip_shanghai).setOnClickListener(v -> applyGcjPreset(31.239693, 121.499762, "上海 外滩"));
        root.findViewById(R.id.chip_shenzhen).setOnClickListener(v -> applyGcjPreset(22.540498, 113.934457, "深圳 科技园"));
        root.findViewById(R.id.chip_hangzhou).setOnClickListener(v -> applyGcjPreset(30.245853, 120.149791, "杭州 西湖"));
        root.findViewById(R.id.chip_tokyo).setOnClickListener(v -> applyGcjPreset(35.659518, 139.700464, "东京 涩谷"));
        root.findViewById(R.id.chip_siliconvalley).setOnClickListener(v -> applyGcjPreset(37.334644, -122.008972, "硅谷 Apple Park"));
        root.findViewById(R.id.chip_paris).setOnClickListener(v -> applyGcjPreset(48.858370, 2.294481, "巴黎 埃菲尔铁塔"));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initMapContainer(ViewGroup container) {
        if (container == null || getContext() == null) return;
        UserManager um = requireContext().getSystemService(UserManager.class);
        if (um != null && !um.isUserUnlocked()) {
            TextView tv = new TextView(requireContext());
            tv.setText("提示：设备当前处于未解锁保护状态\n解锁屏幕后将自动载入高德交互式地图");
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(14);
            tv.setTextColor(0xFF888888);
            container.removeAllViews();
            container.addView(tv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
            requireContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        context.unregisterReceiver(this);
                    } catch (Exception ignored) {}
                    if (isAdded() && getView() != null) {
                        ViewGroup c = getView().findViewById(R.id.map_container);
                        if (c != null) {
                            initWebView(c);
                        }
                    }
                }
            }, filter, Context.RECEIVER_EXPORTED);
            return;
        }

        initWebView(container);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(ViewGroup container) {
        if (container == null || getContext() == null) return;
        try {
            if (mWebView == null) {
                mWebView = new WebView(requireContext());
                WebSettings ws = mWebView.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setDomStorageEnabled(true);

                mWebView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void onLocationSelected(double lat, double lng) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                // 核心解偏：高德瓦片传出的 lat, lng 为 GCJ-02 火星坐标，必须牛顿迭代反解为 WGS-84 标准 GPS 坐标存入系统
                                double[] wgs = CoordinateUtils.gcj02ToWgs84(lat, lng);
                                double wgsLat = wgs[0];
                                double wgsLng = wgs[1];
                                mConfig.setCoordinates(wgsLat, wgsLng, "地图点选位置");
                                mEtLat.setText(String.format(Locale.US, "%.6f", wgsLat));
                                mEtLng.setText(String.format(Locale.US, "%.6f", wgsLng));
                                if (mTvSelectedName != null) {
                                    mTvSelectedName.setText("解析地名中...");
                                }
                                GeocodingHelper.reverseGeocode(wgsLat, wgsLng, placeName -> {
                                    if (isAdded()) {
                                        String finalName = placeName != null ? placeName : String.format(Locale.US, "位置 (%.4f, %.4f)", wgsLat, wgsLng);
                                        mConfig.setCoordinates(wgsLat, wgsLng, finalName);
                                        if (mTvSelectedName != null) {
                                            mTvSelectedName.setText(finalName);
                                        }
                                    }
                                });
                                TravelIpcHelper.syncLocationToSystem(requireContext(), mConfig);
                            });
                        }
                    }
                }, "AndroidBridge");

                mWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        flyTo(mConfig.getLatitude(), mConfig.getLongitude());
                    }
                });

                mWebView.loadUrl("file:///android_asset/map.html");
            }

            ViewGroup parent = (ViewGroup) mWebView.getParent();
            if (parent != null) {
                parent.removeView(mWebView);
            }
            container.removeAllViews();
            container.addView(mWebView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (mEtLat != null && mEtLng != null) {
                mEtLat.setText(String.format(Locale.US, "%.6f", mConfig.getLatitude()));
                mEtLng.setText(String.format(Locale.US, "%.6f", mConfig.getLongitude()));
            }
            flyTo(mConfig.getLatitude(), mConfig.getLongitude());
        } catch (Exception e) {
            Log.e("MapFragment", "Failed to init WebView", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mWebView != null) {
            ViewGroup parent = (ViewGroup) mWebView.getParent();
            if (parent != null) {
                parent.removeView(mWebView);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mConfig != null) {
            if (mEtLat != null && mEtLng != null) {
                mEtLat.setText(String.format(Locale.US, "%.6f", mConfig.getLatitude()));
                mEtLng.setText(String.format(Locale.US, "%.6f", mConfig.getLongitude()));
            }
            if (mTvSelectedName != null) {
                mTvSelectedName.setText(mConfig.getLocationName());
            }
        }
    }

    private void applyPreset(double lat, double lng, String name) {
        mConfig.setCoordinates(lat, lng, name);
        mEtLat.setText(String.format(Locale.US, "%.6f", lat));
        mEtLng.setText(String.format(Locale.US, "%.6f", lng));
        if (mTvSelectedName != null) {
            mTvSelectedName.setText(name);
        }
        flyTo(lat, lng);
        TravelIpcHelper.syncLocationToSystem(requireContext(), mConfig);
        Toast.makeText(requireContext(), "已设定为: " + name, Toast.LENGTH_SHORT).show();
    }

    private void flyTo(double wgsLat, double wgsLng) {
        if (mWebView != null) {
            // 系统内保存的是 WGS-84 标准坐标，传给高德底图渲染前转换为 GCJ-02 火星坐标，确保图钉与瓦片地物绝对重合
            double[] gcj = CoordinateUtils.wgs84ToGcj02(wgsLat, wgsLng);
            mWebView.evaluateJavascript("flyTo(" + gcj[0] + "," + gcj[1] + ");", null);
        }
    }
}
