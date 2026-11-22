package com.zui.position.travel;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class PositionTravelApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 启用 Android 16 Material You 动态取色
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
