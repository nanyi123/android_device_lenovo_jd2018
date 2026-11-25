package com.zui.position.travel;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import com.zui.position.travel.model.TravelConfig;
import com.zui.position.travel.util.TravelIpcHelper;

public class PositionTravelApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 启用 Android 16 Material You 动态取色
        DynamicColors.applyToActivitiesIfAvailable(this);

        // 进程启动时兜底同步一次穿越状态到 system_server，防止与系统内存状态不同步
        TravelConfig config = new TravelConfig(this);
        if (config.isMasterEnabled()) {
            TravelIpcHelper.syncLocationToSystem(this, config);
        }
    }
}
