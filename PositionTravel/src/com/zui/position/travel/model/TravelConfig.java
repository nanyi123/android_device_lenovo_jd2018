package com.zui.position.travel.model;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TravelConfig {
    private static final String PREF_NAME = "travel_settings";

    private static final String KEY_MASTER_ENABLED = "master_enabled";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_LOCATION_NAME = "location_name";
    private static final String KEY_TARGET_UIDS = "target_uids";
    private static final String KEY_JOYSTICK_ENABLED = "joystick_enabled";
    private static final String KEY_MOVE_SPEED = "move_speed"; // 步长，默认 0.0001 (约 10m)
    private static final String KEY_ALTITUDE = "altitude";
    private static final String KEY_BLANK_WIFI = "blank_wifi";
    private static final String KEY_BLANK_CELL = "blank_cell";
    private static final String KEY_FAKE_COMPASS = "fake_compass";
    private static final String KEY_LAST_BEARING = "last_bearing";

    private final SharedPreferences mPrefs;

    public TravelConfig(Context context) {
        Context deContext = context.createDeviceProtectedStorageContext();
        mPrefs = deContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isMasterEnabled() {
        return mPrefs.getBoolean(KEY_MASTER_ENABLED, false);
    }

    public void setMasterEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply();
    }

    public double getLatitude() {
        return Double.longBitsToDouble(mPrefs.getLong(KEY_LATITUDE, Double.doubleToLongBits(39.907321)));
    }

    public double getLongitude() {
        return Double.longBitsToDouble(mPrefs.getLong(KEY_LONGITUDE, Double.doubleToLongBits(116.391255)));
    }

    public void setCoordinates(double lat, double lng, String name) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putLong(KEY_LATITUDE, Double.doubleToLongBits(lat));
        editor.putLong(KEY_LONGITUDE, Double.doubleToLongBits(lng));
        if (name != null) {
            editor.putString(KEY_LOCATION_NAME, name);
        }
        editor.apply();
    }

    public String getLocationName() {
        return mPrefs.getString(KEY_LOCATION_NAME, "北京 天安门");
    }

    public ArrayList<Integer> getTargetUids() {
        Set<String> set = mPrefs.getStringSet(KEY_TARGET_UIDS, null);
        ArrayList<Integer> list = new ArrayList<>();
        if (set != null) {
            for (String s : set) {
                try {
                    list.add(Integer.parseInt(s));
                } catch (NumberFormatException ignored) {}
            }
        }
        return list;
    }

    public void setTargetUids(Set<Integer> uids) {
        Set<String> set = new HashSet<>();
        for (Integer uid : uids) {
            set.add(String.valueOf(uid));
        }
        mPrefs.edit().putStringSet(KEY_TARGET_UIDS, set).apply();
    }

    public boolean isJoystickEnabled() {
        return mPrefs.getBoolean(KEY_JOYSTICK_ENABLED, false);
    }

    public void setJoystickEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_JOYSTICK_ENABLED, enabled).apply();
    }

    public float getMoveSpeed() {
        return mPrefs.getFloat(KEY_MOVE_SPEED, 0.00005f); // 约 5 米步长
    }

    public double getAltitude() {
        return Double.longBitsToDouble(mPrefs.getLong(KEY_ALTITUDE, Double.doubleToLongBits(45.0)));
    }

    public void setAltitude(double altitude) {
        mPrefs.edit().putLong(KEY_ALTITUDE, Double.doubleToLongBits(altitude)).apply();
    }

    public boolean isBlankWifiEnabled() {
        return mPrefs.getBoolean(KEY_BLANK_WIFI, true);
    }

    public void setBlankWifiEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_BLANK_WIFI, enabled).apply();
    }

    public boolean isBlankCellEnabled() {
        return mPrefs.getBoolean(KEY_BLANK_CELL, true);
    }

    public void setBlankCellEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_BLANK_CELL, enabled).apply();
    }

    public boolean isFakeCompassEnabled() {
        return mPrefs.getBoolean(KEY_FAKE_COMPASS, true);
    }

    public void setFakeCompassEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_FAKE_COMPASS, enabled).apply();
    }

    public float getLastBearing() {
        return mPrefs.getFloat(KEY_LAST_BEARING, 0.0f);
    }

    public void setLastBearing(float bearing) {
        mPrefs.edit().putFloat(KEY_LAST_BEARING, bearing).apply();
    }

    public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        mPrefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
