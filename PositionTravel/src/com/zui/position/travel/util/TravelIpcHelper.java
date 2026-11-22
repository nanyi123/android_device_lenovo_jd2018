package com.zui.position.travel.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import com.zui.position.travel.model.TravelConfig;

import java.util.ArrayList;

public class TravelIpcHelper {
    private static final String TAG = "TravelIpcHelper";

    private static final String KEY_TRAVEL_UID_CMD = "travel_uid_cmd";
    private static final String KEY_TRAVEL_UIDS = "travel_uids";

    public static final String KEY_TRAVEL_BLANK_WIFI = "travel_blank_wifi";
    public static final String KEY_TRAVEL_BLANK_CELL = "travel_blank_cell";
    public static final String KEY_TRAVEL_ALTITUDE = "travel_altitude";
    public static final String KEY_RUN_MODE = "travel_run_mode";
    public static final String KEY_ACCEL_X = "travel_accel_x";
    public static final String KEY_ACCEL_Y = "travel_accel_y";
    public static final String KEY_ACCEL_Z = "travel_accel_z";
    public static final String KEY_STEP_COUNT = "travel_step_count";
    public static final String KEY_TRAVEL_FAKE_COMPASS = "travel_fake_compass";
    public static final String KEY_BEARING = "travel_bearing";

    public static void syncRunFrameToSystem(Context context, TravelConfig config,
                                            com.zui.position.travel.engine.UnifiedRunPhysicsEngine.PhysicsFrame frame) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || frame == null || frame.location == null) {
            return;
        }

        try {
            ArrayList<Integer> uids = parseUids(context, config.getTargetUids());
            Location loc = frame.location;

            Bundle bundle = loc.getExtras() != null ? new Bundle(loc.getExtras()) : new Bundle();
            bundle.putInt(KEY_TRAVEL_UID_CMD, 0);
            bundle.putIntegerArrayList(KEY_TRAVEL_UIDS, uids);
            bundle.putBoolean(KEY_TRAVEL_BLANK_WIFI, config.isBlankWifiEnabled());
            bundle.putBoolean(KEY_TRAVEL_BLANK_CELL, config.isBlankCellEnabled());
            bundle.putDouble(KEY_TRAVEL_ALTITUDE, loc.getAltitude());
            bundle.putBoolean(KEY_RUN_MODE, true);
            bundle.putFloat(KEY_ACCEL_X, frame.deviceAccel[0]);
            bundle.putFloat(KEY_ACCEL_Y, frame.deviceAccel[1]);
            bundle.putFloat(KEY_ACCEL_Z, frame.deviceAccel[2]);
            bundle.putInt(KEY_STEP_COUNT, frame.stepCount);
            bundle.putBoolean(KEY_TRAVEL_FAKE_COMPASS, config.isFakeCompassEnabled());
            bundle.putFloat(KEY_BEARING, (float) frame.currentBearing);
            loc.setExtras(bundle);

            lm.setTestProviderLocation("gps", loc);
            lm.setTestProviderLocation("network", loc);
        } catch (Exception e) {
            Log.w(TAG, "Failed to sync run frame to system", e);
        }
    }

    public static void syncLocationToSystem(Context context, TravelConfig config) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return;
        }

        try {
            if (config.isMasterEnabled()) {
                double lat = config.getLatitude();
                double lng = config.getLongitude();
                double alt = config.getAltitude();
                boolean blankWifi = config.isBlankWifiEnabled();
                boolean blankCell = config.isBlankCellEnabled();
                boolean fakeCompass = config.isFakeCompassEnabled();
                float bearing = config.getLastBearing();
                ArrayList<Integer> uids = parseUids(context, config.getTargetUids());

                sendLocation(lm, "gps", lat, lng, alt, 0, uids, blankWifi, blankCell, fakeCompass, bearing);
                sendLocation(lm, "network", lat, lng, alt, 0, uids, blankWifi, blankCell, fakeCompass, bearing);
                Log.d(TAG, "Sync to system: enabled, lat=" + lat + ", lng=" + lng + ", alt=" + alt
                        + ", blankWifi=" + blankWifi + ", blankCell=" + blankCell + ", uids=" + uids);
            } else {
                // 关闭并清除
                sendLocation(lm, "gps", 0, 0, 0, 3, new ArrayList<>(), false, false, false, 0.0f);
                Log.d(TAG, "Sync to system: disabled");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to send travel location to system", e);
        }
    }

    private static void sendLocation(LocationManager lm, String provider, double lat, double lng,
                                    double alt, int cmd, ArrayList<Integer> uids,
                                    boolean blankWifi, boolean blankCell,
                                    boolean fakeCompass, float bearing) {
        Location loc = new Location(provider);
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        loc.setAltitude(alt);
        loc.setBearing(bearing);
        loc.setBearingAccuracyDegrees(2.5f);
        loc.setAccuracy(3.5f);
        loc.setVerticalAccuracyMeters(2.5f);
        loc.setTime(System.currentTimeMillis());
        loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

        Bundle bundle = new Bundle();
        bundle.putInt(KEY_TRAVEL_UID_CMD, cmd);
        bundle.putIntegerArrayList(KEY_TRAVEL_UIDS, uids);
        bundle.putBoolean(KEY_TRAVEL_BLANK_WIFI, blankWifi);
        bundle.putBoolean(KEY_TRAVEL_BLANK_CELL, blankCell);
        bundle.putDouble(KEY_TRAVEL_ALTITUDE, alt);
        bundle.putBoolean(KEY_TRAVEL_FAKE_COMPASS, fakeCompass);
        bundle.putFloat(KEY_BEARING, bearing);
        loc.setExtras(bundle);

        lm.setTestProviderLocation(provider, loc);
    }

    // 保留原厂对 Pokémon GO 的 GMS UID 自动映射，防止封号
    private static ArrayList<Integer> parseUids(Context context, ArrayList<Integer> rawUids) {
        PackageManager pm = context.getPackageManager();
        ArrayList<Integer> result = new ArrayList<>();

        for (int uid : rawUids) {
            String[] pkgs = pm.getPackagesForUid(uid);
            if (pkgs != null && pkgs.length > 0) {
                if ("com.nianticlabs.pokemongo".equals(pkgs[0])) {
                    try {
                        ApplicationInfo info = pm.getApplicationInfo("com.google.android.gms", 0);
                        result.add(info.uid);
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
            }
            result.add(uid);
        }
        return result;
    }
}
