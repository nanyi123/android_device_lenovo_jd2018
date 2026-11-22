package com.zui.position.travel.engine;

import android.location.Location;
import android.os.SystemClock;

import java.util.Random;

/**
 * 统一运动动力学物理引擎 (Unified Run Physics Engine)
 *
 * 核心特性：
 * 1. 轨迹平滑与操场 400m 标准跑道参数方程 (直道 84.39m + 弯道 R=36.5m)
 * 2. 运动学与动力学强耦合：加速度是速度的一阶导数，是轨迹横向抖动的二阶导数
 * 3. 真实裤兜 3D 旋转矩阵姿态模拟：
 *    - 倒立插兜基准角 (180° X-flip)
 *    - 大腿前倾角 (20°)
 *    - 迈步动态角摆动 (±8° 随步频同步震荡)
 * 4. 输出分秒不差、相互印证的 (GPS 坐标, 航向角, 瞬时速度, 海拔, 三轴加速度, 步数)
 */
public class UnifiedRunPhysicsEngine {

    // 标准 400 米田径场几何常数 (IAAF 国际标准)
    public static final double TRACK_STRAIGHT_LEN = 84.39; // 直道长度 (米)
    public static final double TRACK_RADIUS = 36.50;      // 弯道半径 (米)
    public static final double TRACK_CURVE_LEN = Math.PI * TRACK_RADIUS; // 单个半圆弯道弧长 (~114.67米)
    public static final double TRACK_LAP_LEN = 2.0 * TRACK_STRAIGHT_LEN + 2.0 * TRACK_CURVE_LEN; // ~398.12米 ≈ 400米

    // 默认运动学常数
    public static final double DEFAULT_STRIDE_LEN = 0.75; // 平均步幅 0.75 米
    public static final double STEP_FREQ = 2.70;          // 步频 ~2.7 Hz (约 162 步/分钟)

    private double mCenterLat = 39.907321;
    private double mCenterLng = 116.391255;
    private double mBaseAltitude = 45.0;
    private double mTrackHeading = 0.0; // 操场长轴朝向角 (0度为正北正南)

    private final Random mRandom = new Random();
    private double mGpsJitterLat = 0.0; // 真实 GPS 低通一阶自回归漂移 (米)
    private double mGpsJitterLng = 0.0;

    private double mBaseSpeed = 2.50;   // 基准配速 (m/s)，约 6分40秒/公里
    private double mTotalDistance = 0.0;
    private long mStartTimeMs = 0;
    private boolean mIsRunning = false;
    private boolean mIsPaused = false;
    private long mPausedDurationMs = 0;
    private long mLastPauseTimestamp = 0;

    public UnifiedRunPhysicsEngine() {}

    public void initTrack(double centerLat, double centerLng, double baseAlt, double speed) {
        this.mCenterLat = centerLat;
        this.mCenterLng = centerLng;
        this.mBaseAltitude = baseAlt;
        this.mBaseSpeed = speed > 0.5 ? speed : 2.50;
        this.mTotalDistance = 0.0;
        this.mStartTimeMs = SystemClock.elapsedRealtime();
        this.mIsRunning = true;
        this.mIsPaused = false;
        this.mPausedDurationMs = 0;
        this.mGpsJitterLat = 0.0;
        this.mGpsJitterLng = 0.0;
    }

    public void pause() {
        if (mIsRunning && !mIsPaused) {
            mIsPaused = true;
            mLastPauseTimestamp = SystemClock.elapsedRealtime();
        }
    }

    public void resume() {
        if (mIsRunning && mIsPaused) {
            mIsPaused = false;
            mPausedDurationMs += (SystemClock.elapsedRealtime() - mLastPauseTimestamp);
        }
    }

    public void stop() {
        mIsRunning = false;
        mIsPaused = false;
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public double getTotalDistance() {
        return mTotalDistance;
    }

    public int getLapsCompleted() {
        return (int) (mTotalDistance / TRACK_LAP_LEN);
    }

    public long getElapsedRunTimeMs() {
        if (!mIsRunning) return 0;
        long now = SystemClock.elapsedRealtime();
        long currentPause = mIsPaused ? (now - mLastPauseTimestamp) : 0;
        return now - mStartTimeMs - mPausedDurationMs - currentPause;
    }

    /**
     * 步态动力学状态数据包
     */
    public static class PhysicsFrame {
        public Location location;
        public float[] deviceAccel = new float[3]; // 裤兜本地三轴加速度 (X, Y, Z)
        public int stepCount;
        public double currentSpeed;
        public double currentBearing;
        public double distanceCovered;
    }

    /**
     * 以时间戳推进物理引擎，生成完全强耦合的一帧数据
     */
    public PhysicsFrame nextFrame() {
        PhysicsFrame frame = new PhysicsFrame();
        if (!mIsRunning) {
            return frame;
        }

        double t = getElapsedRunTimeMs() / 1000.0; // 累计有效运动秒数
        double omega = 2.0 * Math.PI * STEP_FREQ;  // 步态角频率

        // 1. 瞬时前进速度 v(t)：基础速度 + 步态单步蹬地起伏 (±0.25 m/s) + 宏观体能呼吸慢漂移 (±0.20 m/s)
        double stepPacing = 0.25 * Math.cos(omega * t);
        double macroPacingDrift = 0.16 * Math.sin(0.04 * t) + 0.08 * Math.sin(0.015 * t);
        double v = mBaseSpeed + stepPacing + macroPacingDrift;
        if (v < 0.5) v = 0.5;
        frame.currentSpeed = v;

        // 累计路程与步数
        mTotalDistance += v * 1.0; // 假设采样步长 1.0 秒
        frame.distanceCovered = mTotalDistance;
        frame.stepCount = (int) (mTotalDistance / DEFAULT_STRIDE_LEN);

        // 2. 操场 400m 环形跑道位置解算 (距离映射到跑道局部坐标 X, Y)
        double lapPos = mTotalDistance % TRACK_LAP_LEN;
        double localX, localY, tangentBearing;
        double centripetalAcc = 0.0; // 弯道向心力

        if (lapPos < TRACK_STRAIGHT_LEN) {
            // 东直道 (从南向北)
            localX = TRACK_RADIUS;
            localY = -TRACK_STRAIGHT_LEN / 2.0 + lapPos;
            tangentBearing = 0.0; // 正北
        } else if (lapPos < TRACK_STRAIGHT_LEN + TRACK_CURVE_LEN) {
            // 北半圆弯道 (顺时针转弯)
            double curveS = lapPos - TRACK_STRAIGHT_LEN;
            double alpha = curveS / TRACK_RADIUS; // 0 到 PI
            localX = TRACK_RADIUS * Math.cos(alpha);
            localY = TRACK_STRAIGHT_LEN / 2.0 + TRACK_RADIUS * Math.sin(alpha);
            tangentBearing = Math.toDegrees(alpha); // 朝向从正北旋转至正南
            centripetalAcc = (v * v) / TRACK_RADIUS; // 向心加速度 v^2 / R
        } else if (lapPos < 2.0 * TRACK_STRAIGHT_LEN + TRACK_CURVE_LEN) {
            // 西直道 (从北向南)
            double straightS = lapPos - (TRACK_STRAIGHT_LEN + TRACK_CURVE_LEN);
            localX = -TRACK_RADIUS;
            localY = TRACK_STRAIGHT_LEN / 2.0 - straightS;
            tangentBearing = 180.0; // 正南
        } else {
            // 南半圆弯道 (顺时针转弯)
            double curveS = lapPos - (2.0 * TRACK_STRAIGHT_LEN + TRACK_CURVE_LEN);
            double alpha = curveS / TRACK_RADIUS; // 0 到 PI
            localX = -TRACK_RADIUS * Math.cos(alpha);
            localY = -TRACK_STRAIGHT_LEN / 2.0 - TRACK_RADIUS * Math.sin(alpha);
            tangentBearing = 180.0 + Math.toDegrees(alpha);
            centripetalAcc = (v * v) / TRACK_RADIUS;
        }

        // 3. 人体横向步态微摆动 (±0.35m，左右脚交替踏地) + 分道自然游离慢漂移 (±0.45m)
        double swayFreq = STEP_FREQ / 2.0;
        double lateralSway = 0.35 * Math.sin(2.0 * Math.PI * swayFreq * t);
        double laneWander = 0.32 * Math.sin(0.06 * t) + 0.15 * Math.sin(0.019 * t) + (mRandom.nextGaussian() * 0.06);
        double totalLateralOffset = lateralSway + laneWander;

        // 沿法线方向叠加横向位移
        double radBearing = Math.toRadians(tangentBearing);
        double normX = Math.cos(radBearing);
        double normY = -Math.sin(radBearing);
        localX += normX * totalLateralOffset;
        localY += normY * totalLateralOffset;

        // 4. 真实 GPS 芯片测距多径随机抖动 (AR(1) Gauss-Markov 过程，低通高斯漂移，标准差约 0.7m)
        mGpsJitterLat = 0.65 * mGpsJitterLat + 0.35 * (mRandom.nextGaussian() * 0.75);
        mGpsJitterLng = 0.65 * mGpsJitterLng + 0.35 * (mRandom.nextGaussian() * 0.75);

        // 经纬度米制投影变换 (1度纬度约 111132米) + 硬件级 GPS 随机毛刺
        double deltaLat = (localY + mGpsJitterLat) / 111132.0;
        double deltaLng = (localX + mGpsJitterLng) / (111132.0 * Math.cos(Math.toRadians(mCenterLat)));
        double finalLat = mCenterLat + deltaLat;
        double finalLng = mCenterLng + deltaLng;
        frame.currentBearing = (tangentBearing + 360.0) % 360.0;

        // 5. GPS Location 封装 (包含真实海拔慢游走、朝向角微颤动与自然精度起伏)
        Location loc = new Location("gps");
        loc.setLatitude(finalLat);
        loc.setLongitude(finalLng);
        double altDrift = 0.8 * Math.sin(0.02 * t) + 0.05 * Math.sin(omega * t) + (mRandom.nextGaussian() * 0.10);
        loc.setAltitude(mBaseAltitude + altDrift);
        loc.setSpeed((float) v);
        loc.setSpeedAccuracyMetersPerSecond((float) (0.12 + Math.abs(mRandom.nextGaussian() * 0.05)));
        loc.setBearing((float) ((frame.currentBearing + mRandom.nextGaussian() * 2.8 + 360.0) % 360.0));
        loc.setBearingAccuracyDegrees((float) (3.5 + Math.abs(mRandom.nextGaussian() * 0.8)));
        loc.setAccuracy((float) (3.0 + Math.abs(mRandom.nextGaussian() * 0.8)));
        loc.setVerticalAccuracyMeters((float) (2.2 + Math.abs(mRandom.nextGaussian() * 0.5)));
        loc.setTime(System.currentTimeMillis());
        loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        loc.setIsFromMockProvider(false);
        frame.location = loc;

        // 6. 世界动力学力向量 a_world(t) [横向, 纵向, 垂直]
        // 纵向 a_y: 速度一阶导数 dv/dt = -0.25 * omega * sin(omega * t)
        double aForward = -0.25 * omega * Math.sin(omega * t);
        // 横向 a_x: 摆动二阶导数 d^2x/dt^2 = -0.35 * (2*pi*swayFreq)^2 * sin(...) + 弯道向心力
        double aLateral = -0.35 * Math.pow(2.0 * Math.PI * swayFreq, 2) * Math.sin(2.0 * Math.PI * swayFreq * t) + centripetalAcc;
        // 垂直 a_z: 地球重力 9.81 + 脚掌触地冲击波峰 (约 13.5 m/s^2 脉冲)
        double aVertical = 9.81 + 3.2 * Math.abs(Math.sin(omega * t));

        // 7. 裤兜三维动态旋转矩阵投影 (Pocket 3D Rotation Matrix)
        // 假设手机倒立插入口袋：倒立旋转 180° + 大腿前倾角 20° + 迈步动态角摆动 (±8° 随步频同步震荡)
        double thighPitch = Math.toRadians(20.0 + 8.0 * Math.cos(omega * t));
        double thighRoll = Math.toRadians(4.0 * Math.sin(omega * t)); // 微小侧摇

        // 计算旋转矩阵 R = Rx(thighPitch) * Ry(thighRoll) 并考虑倒插 (Y轴反转)
        // 投影后手机本地各轴受力：
        double devAx = aLateral * Math.cos(thighRoll) - aForward * Math.sin(thighRoll);
        double devAy = -(aForward * Math.cos(thighPitch) + aVertical * Math.sin(thighPitch)); // 倒插主受力在负 Y 轴
        double devAz = aVertical * Math.cos(thighPitch) - aForward * Math.sin(thighPitch);

        // 叠加真实传感器物理高斯白噪声
        frame.deviceAccel[0] = (float) (devAx + mRandom.nextGaussian() * 0.22);
        frame.deviceAccel[1] = (float) (devAy + mRandom.nextGaussian() * 0.32);
        frame.deviceAccel[2] = (float) (devAz + mRandom.nextGaussian() * 0.22);

        return frame;
    }
}
