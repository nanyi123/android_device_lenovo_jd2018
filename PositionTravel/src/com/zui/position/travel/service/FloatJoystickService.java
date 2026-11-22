package com.zui.position.travel.service;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.zui.position.travel.R;
import com.zui.position.travel.model.TravelConfig;
import com.zui.position.travel.util.TravelIpcHelper;

public class FloatJoystickService extends Service {
    private static final String TAG = "FloatJoystickService";
    private static final String CHANNEL_ID = "channel_joystick";
    private static final int NOTIF_ID = 1002;

    private static final int STATE_EXPANDED = 1;
    private static final int STATE_COLLAPSED = 2;

    private WindowManager mWindowManager;
    private View mFloatingView;
    private WindowManager.LayoutParams mParams;
    private TravelConfig mConfig;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mHeartbeatRunnable;
    private Runnable mIdleFadeRunnable;

    private int mCurrentState = STATE_EXPANDED;
    private int mScreenWidth = 1080;
    private int mScreenHeight = 2246;
    private int mExpandedSizePx;
    private int mBubbleSizePx;

    @Override
    public void onCreate() {
        super.onCreate();
        mConfig = new TravelConfig(this);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(dm);
        mScreenWidth = dm.widthPixels;
        mScreenHeight = dm.heightPixels;

        float density = dm.density;
        mExpandedSizePx = Math.round(155 * density);
        mBubbleSizePx = Math.round(44 * density);

        createNotificationChannel();
        startForegroundService();
        initFloatingView();
        startHeartbeat();
        resetIdleTimer();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "位置摇杆悬浮窗",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("位置穿越快捷摇杆控制");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundService() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("位置穿越摇杆")
                .setContentText("悬浮窗运行中，点击可控制虚拟位置")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initFloatingView() {
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No overlay permission, stopping FloatJoystickService");
            stopSelf();
            return;
        }

        try {
            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            mParams = new WindowManager.LayoutParams(
                    mExpandedSizePx,
                    mExpandedSizePx,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );

            mParams.gravity = Gravity.TOP | Gravity.START;
            mParams.x = (mScreenWidth - mExpandedSizePx) / 2;
            mParams.y = mScreenHeight / 3;

            Context themeContext = new ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
            LayoutInflater inflater = LayoutInflater.from(themeContext);
            mFloatingView = inflater.inflate(R.layout.view_float_joystick, null);

            setupViews();
            mWindowManager.addView(mFloatingView, mParams);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize floating window", e);
            stopSelf();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupViews() {
        if (mFloatingView == null) return;

        View layoutBubble = mFloatingView.findViewById(R.id.layout_bubble);
        View layoutExpanded = mFloatingView.findViewById(R.id.layout_expanded_joystick);

        // 1. 折叠模式气泡交互（拖拽与吸附、点击展开）
        layoutBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchDownTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                wakeFromIdle();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchDownTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        updateLayout();
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchDownTime;
                        float dist = (float) Math.hypot(event.getRawX() - initialTouchX, event.getRawY() - initialTouchY);
                        if (duration < 250 && dist < 15) {
                            // 点击触发展开
                            switchState(STATE_EXPANDED);
                        } else {
                            // 拖动结束，贴边吸附到左或右边缘
                            if (mParams.x < mScreenWidth / 2) {
                                mParams.x = 8;
                            } else {
                                mParams.x = mScreenWidth - mBubbleSizePx - 8;
                            }
                            updateLayout();
                        }
                        resetIdleTimer();
                        return true;
                }
                return false;
            }
        });

        // 2. 展开模式中心圆盘交互（拖拽移动、轻触收起）
        View btnCenter = mFloatingView.findViewById(R.id.btn_center);
        btnCenter.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchDownTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                wakeFromIdle();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchDownTime = System.currentTimeMillis();
                        btnCenter.setPressed(true);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        updateLayout();
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        btnCenter.setPressed(false);
                        long duration = System.currentTimeMillis() - touchDownTime;
                        float dist = (float) Math.hypot(event.getRawX() - initialTouchX, event.getRawY() - initialTouchY);
                        if (duration < 250 && dist < 15) {
                            // 点击中心圆盘快速折叠
                            switchState(STATE_COLLAPSED);
                        }
                        resetIdleTimer();
                        return true;
                }
                return false;
            }
        });

        // 3. 最小化与关闭按钮
        View btnMin = mFloatingView.findViewById(R.id.btn_minimize);
        if (btnMin != null) {
            btnMin.setOnClickListener(v -> {
                wakeFromIdle();
                switchState(STATE_COLLAPSED);
            });
        }

        View btnClose = mFloatingView.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                mConfig.setJoystickEnabled(false);
                stopSelf();
            });
        }

        // 4. 四向十字键，步长与长按连发
        float step = mConfig.getMoveSpeed();
        setupDirectionButton(mFloatingView.findViewById(R.id.btn_up), step, 0);
        setupDirectionButton(mFloatingView.findViewById(R.id.btn_down), -step, 0);
        setupDirectionButton(mFloatingView.findViewById(R.id.btn_left), 0, -step);
        setupDirectionButton(mFloatingView.findViewById(R.id.btn_right), 0, step);
    }

    private void switchState(int newState) {
        if (mFloatingView == null || mParams == null) return;
        mCurrentState = newState;

        View layoutBubble = mFloatingView.findViewById(R.id.layout_bubble);
        View layoutExpanded = mFloatingView.findViewById(R.id.layout_expanded_joystick);

        if (newState == STATE_COLLAPSED) {
            layoutExpanded.setVisibility(View.GONE);
            layoutBubble.setVisibility(View.VISIBLE);
            mParams.width = mBubbleSizePx;
            mParams.height = mBubbleSizePx;

            // 贴边处理
            if (mParams.x < mScreenWidth / 2) {
                mParams.x = 8;
            } else {
                mParams.x = mScreenWidth - mBubbleSizePx - 8;
            }
        } else {
            layoutBubble.setVisibility(View.GONE);
            layoutExpanded.setVisibility(View.VISIBLE);
            mParams.width = mExpandedSizePx;
            mParams.height = mExpandedSizePx;

            // 防止展开后超出右边界
            if (mParams.x + mExpandedSizePx > mScreenWidth) {
                mParams.x = mScreenWidth - mExpandedSizePx - 16;
            }
            if (mParams.x < 16) {
                mParams.x = 16;
            }
        }
        updateLayout();
        resetIdleTimer();
    }

    private void updateLayout() {
        if (mFloatingView != null && mWindowManager != null && mParams != null) {
            try {
                mWindowManager.updateViewLayout(mFloatingView, mParams);
            } catch (Exception ignored) {}
        }
    }

    private void setupDirectionButton(View button, double dLat, double dLng) {
        if (button == null) return;

        Handler repeatHandler = new Handler(Looper.getMainLooper());
        Runnable repeatRunnable = new Runnable() {
            @Override
            public void run() {
                moveLocation(dLat, dLng);
                repeatHandler.postDelayed(this, 120);
            }
        };

        button.setOnTouchListener((v, event) -> {
            wakeFromIdle();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    moveLocation(dLat, dLng);
                    repeatHandler.postDelayed(repeatRunnable, 300);
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    repeatHandler.removeCallbacks(repeatRunnable);
                    v.setPressed(false);
                    resetIdleTimer();
                    return true;
            }
            return false;
        });
    }

    private void moveLocation(double dLat, double dLng) {
        double newLat = mConfig.getLatitude() + dLat;
        double newLng = mConfig.getLongitude() + dLng;
        mConfig.setCoordinates(newLat, newLng, "虚拟移动");
        float bearing = (float) Math.toDegrees(Math.atan2(dLng, dLat));
        if (bearing < 0) bearing += 360.0f;
        mConfig.setLastBearing(bearing);
        TravelIpcHelper.syncLocationToSystem(this, mConfig);
    }

    private void wakeFromIdle() {
        if (mFloatingView != null && mFloatingView.getAlpha() < 0.95f) {
            ObjectAnimator.ofFloat(mFloatingView, "alpha", mFloatingView.getAlpha(), 1.0f)
                    .setDuration(150)
                    .start();
        }
    }

    private void resetIdleTimer() {
        if (mIdleFadeRunnable != null) {
            mHandler.removeCallbacks(mIdleFadeRunnable);
        }
        mIdleFadeRunnable = () -> {
            if (mFloatingView != null) {
                // 展开状态闲置 3.5 秒后淡化为半透明（0.38），绝不遮挡游戏或地图操作
                float targetAlpha = (mCurrentState == STATE_EXPANDED) ? 0.38f : 0.6f;
                ObjectAnimator.ofFloat(mFloatingView, "alpha", mFloatingView.getAlpha(), targetAlpha)
                        .setDuration(400)
                        .start();
            }
        };
        mHandler.postDelayed(mIdleFadeRunnable, 3500);
    }

    // 1000ms 心跳保鲜，确保客户端无论何时调用都能拿到毫秒级保鲜的假位置
    private void startHeartbeat() {
        mHeartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (mConfig.isMasterEnabled()) {
                    TravelIpcHelper.syncLocationToSystem(FloatJoystickService.this, mConfig);
                }
                mHandler.postDelayed(this, 1000);
            }
        };
        mHandler.postDelayed(mHeartbeatRunnable, 1000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHandler != null) {
            if (mHeartbeatRunnable != null) mHandler.removeCallbacks(mHeartbeatRunnable);
            if (mIdleFadeRunnable != null) mHandler.removeCallbacks(mIdleFadeRunnable);
        }
        if (mFloatingView != null && mWindowManager != null) {
            try {
                if (mFloatingView.isAttachedToWindow()) {
                    mWindowManager.removeView(mFloatingView);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to remove floating view", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
