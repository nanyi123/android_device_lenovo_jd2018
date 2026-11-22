package com.zui.position.travel.model;

import android.graphics.drawable.Drawable;

public class AppItem implements Comparable<AppItem> {
    public final String appName;
    public final String packageName;
    public final int uid;
    public final Drawable icon;
    public final boolean isSystemApp;
    public boolean isSelected;

    public AppItem(String appName, String packageName, int uid, Drawable icon, boolean isSystemApp, boolean isSelected) {
        this.appName = appName;
        this.packageName = packageName;
        this.uid = uid;
        this.icon = icon;
        this.isSystemApp = isSystemApp;
        this.isSelected = isSelected;
    }

    @Override
    public int compareTo(AppItem o) {
        if (this.isSelected != o.isSelected) {
            return this.isSelected ? -1 : 1;
        }
        return this.appName.compareToIgnoreCase(o.appName);
    }
}
