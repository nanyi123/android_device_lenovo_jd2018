package com.zui.position.travel.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.zui.position.travel.MainActivity;
import com.zui.position.travel.R;
import com.zui.position.travel.engine.UnifiedRunPhysicsEngine;
import com.zui.position.travel.model.TravelConfig;
import com.zui.position.travel.util.TravelIpcHelper;

import java.util.Locale;

/**
 * 校园跑前台运动保活服务 (RunService)
 *
 * 特性：
 * 1. 前台常驻通知 (带实时百分比进度条、配速、步频、圈数更新)
 * 2. 通知栏快捷操作 [暂停/继续]、[结束]
 * 3. 1Hz 动力学循环，同步高拟真 Location 与传感器 Deepfake
 * 4. 提供静态监听器，与 RunFragment 实时 UI 双向绑定
 */
public class RunService extends Service {
    private static final String TAG = "RunService";
    private static final String CHANNEL_ID = "run_service_channel";
    private static final int NOTIF_ID = 2001;

    public static final String ACTION_START = "com.zui.position.travel.ACTION_START_RUN";
    public static final String ACTION_PAUSE = "com.zui.position.travel.ACTION_PAUSE_RUN";
    public static final String ACTION_RESUME = "com.zui.position.travel.ACTION_RESUME_RUN";
    public static final String ACTION_STOP = "com.zui.position.travel.ACTION_STOP_RUN";

    public static final String EXTRA_TARGET_KM = "target_km";
    public static final String EXTRA_PACE_SPEED = "pace_speed";

    public interface RunStateListener {
        void onRunFrame(UnifiedRunPhysicsEngine.PhysicsFrame frame, double totalDist, double targetDist, long elapsedMs, boolean isPaused);
        void onRunStateChanged(boolean isRunning, boolean isPaused);
        void onRunStopped();
    }

    private static volatile RunService sInstance = null;
    private static volatile RunStateListener sListener = null;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private UnifiedRunPhysicsEngine mEngine;
    private TravelConfig mConfig;
    private double mTargetDistanceMeters = 2400.0; // 默认 2.4 公里 (6 圈)
    private NotificationManager mNotificationManager;
    private Runnable mTickRunnable;

    public static void setListener(RunStateListener listener) {
        sListener = listener;
    }

    public static RunService getInstance() {
        return sInstance;
    }

    public static boolean isServiceRunning() {
        return sInstance != null && sInstance.mEngine != null && sInstance.mEngine.isRunning();
    }

    public static boolean isServicePaused() {
        return sInstance != null && sInstance.mEngine != null && sInstance.mEngine.isPaused();
    }

    public static double getCurrentDistance() {
        return (sInstance != null && sInstance.mEngine != null) ? sInstance.mEngine.getTotalDistance() : 0.0;
    }

    public static double getTargetDistance() {
        return sInstance != null ? sInstance.mTargetDistanceMeters : 2400.0;
    }

    public static long getElapsedTimeMs() {
        return (sInstance != null && sInstance.mEngine != null) ? sInstance.mEngine.getElapsedRunTimeMs() : 0;
    }

    public static int getLaps() {
        return (sInstance != null && sInstance.mEngine != null) ? sInstance.mEngine.getLapsCompleted() : 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mConfig = new TravelConfig(this);
        mEngine = new UnifiedRunPhysicsEngine();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            double targetKm = intent.getDoubleExtra(EXTRA_TARGET_KM, 2.4);
            double speed = intent.getDoubleExtra(EXTRA_PACE_SPEED, 2.50);
            mTargetDistanceMeters = targetKm * 1000.0;

            mEngine.initTrack(mConfig.getLatitude(), mConfig.getLongitude(), mConfig.getAltitude(), speed);
            startForegroundServiceNotification();
            startRunningLoop();
            if (sListener != null) {
                sListener.onRunStateChanged(true, false);
            }
            Log.i(TAG, "Started campus run: target=" + targetKm + "km, speed=" + speed + "m/s");
        } else if (ACTION_PAUSE.equals(action)) {
            mEngine.pause();
            updateNotification();
            if (sListener != null) {
                sListener.onRunStateChanged(true, true);
            }
        } else if (ACTION_RESUME.equals(action)) {
            mEngine.resume();
            updateNotification();
            if (sListener != null) {
                sListener.onRunStateChanged(true, false);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopRunning();
        }

        return START_STICKY;
    }

    private void startForegroundServiceNotification() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void startRunningLoop() {
        if (mTickRunnable != null) {
            mHandler.removeCallbacks(mTickRunnable);
        }

        mTickRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mEngine.isRunning()) {
                    return;
                }

                if (!mEngine.isPaused()) {
                    UnifiedRunPhysicsEngine.PhysicsFrame frame = mEngine.nextFrame();

                    // 1. 同步 Location 给系统 SystemServer
                    if (frame.location != null) {
                        TravelIpcHelper.syncRunFrameToSystem(RunService.this, mConfig, frame);
                    }

                    // 2. 检查是否达到目标距离
                    if (mEngine.getTotalDistance() >= mTargetDistanceMeters) {
                        Log.i(TAG, "Campus run completed target distance: " + mTargetDistanceMeters + "m");
                        stopRunning();
                        return;
                    }

                    // 3. 动态刷新通知栏进度看板
                    updateNotification();

                    // 4. 回调给 UI (RunFragment)
                    if (sListener != null) {
                        sListener.onRunFrame(frame, mEngine.getTotalDistance(), mTargetDistanceMeters,
                                mEngine.getElapsedRunTimeMs(), mEngine.isPaused());
                    }
                }

                mHandler.postDelayed(this, 1000); // 1Hz 精准推进
            }
        };

        mHandler.post(mTickRunnable);
    }

    private void stopRunning() {
        if (mEngine != null) {
            mEngine.stop();
        }
        if (mTickRunnable != null) {
            mHandler.removeCallbacks(mTickRunnable);
        }
        // 恢复静态坐标
        TravelIpcHelper.syncLocationToSystem(this, mConfig);
        if (sListener != null) {
            sListener.onRunStopped();
        }
        stopForeground(true);
        stopSelf();
        sInstance = null;
        Log.i(TAG, "Stopped campus run service");
    }

    private void updateNotification() {
        if (mNotificationManager != null && mEngine.isRunning()) {
            mNotificationManager.notify(NOTIF_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        double distKm = mEngine.getTotalDistance() / 1000.0;
        double targetKm = mTargetDistanceMeters / 1000.0;
        int progress = (int) Math.min(100, Math.max(0, (mEngine.getTotalDistance() / mTargetDistanceMeters) * 100));

        long elapsedSec = mEngine.getElapsedRunTimeMs() / 1000;
        long mins = elapsedSec / 60;
        long secs = elapsedSec % 60;
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", mins, secs);

        // 配速计算 (分'秒"/km)
        double speed = mEngine.isRunning() && !mEngine.isPaused() ? 2.50 : 0.0;
        int paceSecPerKm = speed > 0.5 ? (int) (1000.0 / speed) : 400;
        String paceStr = String.format(Locale.getDefault(), "%d'%02d\"", paceSecPerKm / 60, paceSecPerKm % 60);

        int laps = mEngine.getLapsCompleted();
        int totalLaps = (int) Math.ceil(mTargetDistanceMeters / UnifiedRunPhysicsEngine.TRACK_LAP_LEN);

        String title = mEngine.isPaused() ? "⏸ 校园跑已暂停" : "🏃 校园跑进行中 · 操场 400m 模拟";
        String contentText = String.format(Locale.getDefault(),
                "已跑 %.2f / %.2f km (第 %d/%d 圈) | 配速 %s | 用时 %s",
                distKm, targetKm, laps + 1, totalLaps, paceStr, timeStr);

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.putExtra("tab", "run");
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setSmallIcon(R.drawable.ic_run)
                .setContentTitle(title)
                .setContentText(contentText)
                .setContentIntent(piOpen)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, false);

        // 快捷控制按钮
        if (mEngine.isPaused()) {
            Intent resumeIntent = new Intent(this, RunService.class).setAction(ACTION_RESUME);
            PendingIntent piResume = PendingIntent.getService(this, 1, resumeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_media_play, "继续", piResume);
        } else {
            Intent pauseIntent = new Intent(this, RunService.class).setAction(ACTION_PAUSE);
            PendingIntent piPause = PendingIntent.getService(this, 2, pauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_media_pause, "暂停", piPause);
        }

        Intent stopIntent = new Intent(this, RunService.class).setAction(ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(this, 3, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "结束", piStop);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "校园跑模拟服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("实时展示校园跑配速、已跑距离与进度控制");
            if (mNotificationManager != null) {
                mNotificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRunning();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
