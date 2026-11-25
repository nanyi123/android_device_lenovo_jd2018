package com.zui.position.travel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.zui.position.travel.model.TravelConfig;
import com.zui.position.travel.util.TravelIpcHelper;

/**
 * Restores active Position Travel state into system_server after device reboot.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PositionTravelBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return;
        }

        TravelConfig config = new TravelConfig(context);
        if (config.isMasterEnabled()) {
            Log.i(TAG, "Restoring position travel state on " + action);
            TravelIpcHelper.syncLocationToSystem(context, config);
        }
    }
}
