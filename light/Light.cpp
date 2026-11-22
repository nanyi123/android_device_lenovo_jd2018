/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "LightService"

#include "Light.h"

#include <android-base/logging.h>
#include <android-base/properties.h>

#include <chrono>
#include <cstdio>
#include <thread>
#include <unistd.h>

namespace {
using android::hardware::light::V2_0::LightState;

static constexpr int RAMP_SIZE = 15;
static constexpr int RAMP_STEP_DURATION = 150;

static constexpr int BRIGHTNESS_RAMP[RAMP_SIZE] = {0, 12, 25, 37, 50, 72, 85, 100, 85, 72, 50, 37, 25, 12, 0};
static constexpr int DEFAULT_MAX_BRIGHTNESS = 255;

// ---- 激发亮度（HBM / BE）：对齐原厂 ZuiPowerController ------------------------
// 从原厂 boot-framework.oat 反编译 android/app/ZuiPowerController：
//   ZUI_BOOST_LUX=0x59d8(23000)、ZUI_BOOST_DEBOUNCE_TIME=0xbb8(3000ms)、
//   ZUI_BOOST_TOTAL_TIME=0xea60(60000ms)；触发 = lux>=23000 && 自动亮度满档
//   && 内部开关开，3s 防抖，单次最长 60s，息屏重置；写私有节点 panel0-hbm/brightness。
// 我们的 4.19 华硕内核没有 panel0-hbm / hdr_en，只有标准满量程电流节点：
//   /sys/class/leds/wled/fs_curr_ua   (uA, 0..30000, step 2500)
// DTS 写的是 21000，但寄存器按 2500 取整 → 实际 20000uA，即原厂手动最大 568nit。
// 761nit / 568nit = 1.34×，对应约 26800uA，寄存器取 11 档 = 27500uA。
static constexpr const char* kBoostNode = "/sys/class/leds/wled/fs_curr_ua";
static constexpr const char* kLuxNode = "/sys/class/sensors/lsensor/lux";
static constexpr int kNormalCurrentUa = 21000;  // == DTS 默认，写回即恢复
static constexpr int kBoostCurrentUa = 27500;
static constexpr float kBoostEnterFraction = 0.95f;   // 背光达到 ≥95% (≥242) 即算进入满档（容差 AOSP 自动亮度插值上限 252 及滑块微差）
static constexpr float kBoostExitFraction = 0.80f;    // 亮度掉到 ≤80% 立即退出（滞回）
static constexpr int kBoostDefaultEnterLux = 23000;   // 原厂 ZUI_BOOST_LUX (0x59d8) 严格保持原厂照度门槛
static constexpr int64_t kBoostDebounceMs = 3000;     // 原厂 ZUI_BOOST_DEBOUNCE_TIME
static constexpr int64_t kBoostMaxDurationMs = 60000; // 原厂 ZUI_BOOST_TOTAL_TIME
static constexpr int64_t kLuxThrottleMs = 500;        // lux 读取节流


static uint32_t rgbToBrightness(const LightState& state) {
    uint32_t color = state.color & 0x00ffffff;
    return ((77 * ((color >> 16) & 0xff)) + (150 * ((color >> 8) & 0xff)) +
            (29 * (color & 0xff))) >> 8;
}

static bool isLit(const LightState& state) {
    return (state.color & 0x00ffffff);
}

static std::string getScaledDutyPcts(int brightness) {
    std::string buf, pad;

    for (auto i : BRIGHTNESS_RAMP) {
        buf += pad;
        buf += std::to_string(i * brightness / 255);
        pad = ",";
    }

    return buf;
}
}  // anonymous namespace

namespace android {
namespace hardware {
namespace light {
namespace V2_0 {
namespace implementation {

Light::Light(std::pair<std::ofstream, uint32_t>&& lcd_backlight,
             std::ofstream&& red_led, std::ofstream&& green_led, std::ofstream&& blue_led,
             std::ofstream&& red_duty_pcts, std::ofstream&& green_duty_pcts, std::ofstream&& blue_duty_pcts,
             std::ofstream&& red_start_idx, std::ofstream&& green_start_idx, std::ofstream&& blue_start_idx,
             std::ofstream&& red_pause_lo, std::ofstream&& green_pause_lo, std::ofstream&& blue_pause_lo,
             std::ofstream&& red_pause_hi, std::ofstream&& green_pause_hi, std::ofstream&& blue_pause_hi,
             std::ofstream&& red_ramp_step_ms, std::ofstream&& green_ramp_step_ms, std::ofstream&& blue_ramp_step_ms,
             std::ofstream&& red_blink, std::ofstream&& green_blink, std::ofstream&& blue_blink)
    : mLcdBacklight(std::move(lcd_backlight)),
      mRedLed(std::move(red_led)),
      mGreenLed(std::move(green_led)),
      mBlueLed(std::move(blue_led)),
      mRedDutyPcts(std::move(red_duty_pcts)),
      mGreenDutyPcts(std::move(green_duty_pcts)),
      mBlueDutyPcts(std::move(blue_duty_pcts)),
      mRedStartIdx(std::move(red_start_idx)),
      mGreenStartIdx(std::move(green_start_idx)),
      mBlueStartIdx(std::move(blue_start_idx)),
      mRedPauseLo(std::move(red_pause_lo)),
      mGreenPauseLo(std::move(green_pause_lo)),
      mBluePauseLo(std::move(blue_pause_lo)),
      mRedPauseHi(std::move(red_pause_hi)),
      mGreenPauseHi(std::move(green_pause_hi)),
      mBluePauseHi(std::move(blue_pause_hi)),
      mRedRampStepMs(std::move(red_ramp_step_ms)),
      mGreenRampStepMs(std::move(green_ramp_step_ms)),
      mBlueRampStepMs(std::move(blue_ramp_step_ms)),
      mRedBlink(std::move(red_blink)),
      mGreenBlink(std::move(green_blink)),
      mBlueBlink(std::move(blue_blink)) {
    auto attnFn(std::bind(&Light::setAttentionLight, this, std::placeholders::_1));
    auto backlightFn(std::bind(&Light::setLcdBacklight, this, std::placeholders::_1));
    auto batteryFn(std::bind(&Light::setBatteryLight, this, std::placeholders::_1));
    auto notifFn(std::bind(&Light::setNotificationLight, this, std::placeholders::_1));
    mLights.emplace(std::make_pair(Type::ATTENTION, attnFn));
    mLights.emplace(std::make_pair(Type::BACKLIGHT, backlightFn));
    mLights.emplace(std::make_pair(Type::BATTERY, batteryFn));
    mLights.emplace(std::make_pair(Type::NOTIFICATIONS, notifFn));

    // 只有在节点存在且可写时才启用，避免老内核/其它机型上无谓的 write 失败。
    mBoostWritable = ::access(kBoostNode, W_OK) == 0;
    mBoostSupported = mBoostWritable &&
            android::base::GetBoolProperty("persist.vendor.display.hbm", true);
    mBoostForce = android::base::GetBoolProperty("persist.vendor.display.hbm.force", false);
    mBoostEnterLux = android::base::GetIntProperty("persist.vendor.display.hbm.lux",
                                                   kBoostDefaultEnterLux);
    if (mBoostEnterLux < 100)
        mBoostEnterLux = 100;
    mBoostExitLux = mBoostEnterLux * 5 / 8;  // 滞回：低于 enter*5/8 才算真正退出
    LOG(INFO) << "brightness boost " << (mBoostSupported ? "available" : "disabled")
              << (mBoostForce ? " (force, lux ignored)" : "");

    // HAL 重启时硬件可能还停在上次的 boost 电流，先复位到 DTS 基线。
    // 否则 mBoostOn=false 与硬件 27500 不一致，之后不会再写回 21000。
    if (mBoostWritable)
        setBoostCurrent(kNormalCurrentUa);
}

// Methods from ::android::hardware::light::V2_0::ILight follow.
Return<Status> Light::setLight(Type type, const LightState& state) {
    auto it = mLights.find(type);

    if (it == mLights.end()) {
        return Status::LIGHT_NOT_SUPPORTED;
    }

    it->second(state);

    return Status::SUCCESS;
}

Return<void> Light::getSupportedTypes(getSupportedTypes_cb _hidl_cb) {
    std::vector<Type> types;

    for (auto const& light : mLights) {
        types.push_back(light.first);
    }

    _hidl_cb(types);

    return Void();
}

void Light::setAttentionLight(const LightState& state) {
    std::lock_guard<std::mutex> lock(mLock);
    mAttentionState = state;
    setSpeakerBatteryLightLocked();
}

void Light::setLcdBacklight(const LightState& state) {
    std::lock_guard<std::mutex> lock(mLock);

    uint32_t brightness = rgbToBrightness(state);

    // If max panel brightness is not the default (255),
    // apply linear scaling across the accepted range.
    if (mLcdBacklight.second != DEFAULT_MAX_BRIGHTNESS) {
        int old_brightness = brightness;
        	brightness = brightness * mLcdBacklight.second / DEFAULT_MAX_BRIGHTNESS;
            LOG(VERBOSE) << "scaling brightness " << old_brightness << " => " << brightness;
		}

    mLcdBacklight.first << brightness << std::endl;

    mLastBacklight.store(brightness);
    updateBrightnessBoost(brightness);

    // 亮度停在满档时环境光还会变（进出户外），交给常驻线程每 2s 复判
    if (mBoostSupported && !mBoostWorkerStarted.exchange(true))
        std::thread(&Light::boostWorker, this).detach();
}

bool Light::readAmbientLux(int* lux) {
    std::ifstream f(kLuxNode);
    int value;

    if (!f.is_open() || !(f >> value))
        return false;

    *lux = value;
    return true;
}

void Light::setBoostCurrent(int ua) {
    std::ofstream f(kBoostNode, std::ios::out | std::ios::trunc);

    if (!f.is_open()) {
        LOG(WARNING) << "boost: cannot open " << kBoostNode;
        return;
    }

    f << ua;
    if (!f.good()) {
        LOG(WARNING) << "boost: failed to write " << ua;
        return;
    }

    LOG(INFO) << "boost " << (ua > kNormalCurrentUa ? "ON" : "off")
              << " fs_curr_ua=" << ua;
}

// 对齐原厂 resetZuiBoostState()：息屏时退出并重置「本次亮屏已用时长」，
// 让下一次亮屏重新获得完整的 60 秒激发额度。
void Light::resetBoostSession() {
    mBoostOnMs = 0;
    mBoostEnterMs = 0;
    mBoostSessionSpent = false;
}

void Light::updateBrightnessBoost(uint32_t backlight) {
    if (!mBoostSupported)
        return;

    const uint32_t max = mLcdBacklight.second ? mLcdBacklight.second : DEFAULT_MAX_BRIGHTNESS;
    const auto now = std::chrono::steady_clock::now().time_since_epoch();
    const int64_t ms = std::chrono::duration_cast<std::chrono::milliseconds>(now).count();

    // 息屏（backlight==0）：等价于原厂息屏回调，退出并重置时长额度
    if (backlight == 0) {
        if (mBoostOn) {
            mBoostOn = false;
            setBoostCurrent(kNormalCurrentUa);
            LOG(INFO) << "boost exit (screen off), session reset";
        }
        resetBoostSession();
        return;
    }

    // 亮度掉出激发区（≤80%）：原厂是「自动亮度不再满档」即退出，无条件回落
    if (backlight <= static_cast<uint32_t>(max * kBoostExitFraction)) {
        if (mBoostOn) {
            mBoostOn = false;
            setBoostCurrent(kNormalCurrentUa);
            LOG(INFO) << "boost exit (backlight dropped)";
        }
        mBoostEnterMs = 0;
        return;
    }

    // 已进入激发：检查光线回落 / 单次时长上限
    if (mBoostOn) {
        // 原厂 lux<阈值就回落；加 enter*5/8 滞回避免 23000 边界反复横跳
        if (!mBoostForce) {
            int lux = -1;
            if (readAmbientLux(&lux) && lux < mBoostExitLux) {
                mBoostOn = false;
                setBoostCurrent(kNormalCurrentUa);
                LOG(INFO) << "boost exit (lux dropped to " << lux << ")";
                return;
            }
        }
        if (mBoostOnMs && ms - mBoostOnMs >= kBoostMaxDurationMs) {
            mBoostOn = false;
            mBoostSessionSpent = true;
            setBoostCurrent(kNormalCurrentUa);
            LOG(INFO) << "boost exit (60s timeout, session spent)";
        }
        return;
    }

    // 60s 额度已用尽：本次亮屏不再重新激发，等息屏 resetBoostSession()
    if (mBoostSessionSpent)
        return;

    // 满亮度区：原厂要求自动亮度已把背光推到满档。
    // 在 AOSP 自动亮度映射下，插值最高上限通常为 252 (~98.8%)，且手动拉滑块也有微小容差。
    // 用「backlight >= max * kBoostEnterFraction (95%)」判定，避免 252 < 255 导致永远无法触发。
    if (backlight < static_cast<uint32_t>(max * kBoostEnterFraction)) {
        mBoostEnterMs = 0;
        return;
    }

    // 环境光判断（带节流），enter/exit 两个阈值避免边界抖动
    if (ms - mLastLuxCheckMs < kLuxThrottleMs)
        return;
    mLastLuxCheckMs = ms;

    int lux = -1;
    bool want = false;
    if (mBoostForce) {
        want = true;
    } else if (readAmbientLux(&lux)) {
        want = lux >= mBoostEnterLux;
    }

    if (!want) {
        mBoostEnterMs = 0;  // 条件中断，防抖计时清零
        return;
    }

    // 3 秒防抖（原厂 ZUI_BOOST_DEBOUNCE_TIME）：条件需连续满足
    if (!mBoostEnterMs) {
        mBoostEnterMs = ms;
        return;
    }
    if (ms - mBoostEnterMs < kBoostDebounceMs)
        return;

    mBoostOn = true;
    mBoostOnMs = ms;
    setBoostCurrent(kBoostCurrentUa);
    LOG(INFO) << "boost ON (lux=" << lux << ", debounced " << kBoostDebounceMs << "ms)";
}

void Light::boostWorker() {
    while (true) {
        std::this_thread::sleep_for(std::chrono::seconds(2));
        std::lock_guard<std::mutex> lock(mLock);
        updateBrightnessBoost(mLastBacklight.load());
    }
}

void Light::setBatteryLight(const LightState& state) {
    std::lock_guard<std::mutex> lock(mLock);
    mBatteryState = state;
    setSpeakerBatteryLightLocked();
}

void Light::setNotificationLight(const LightState& state) {
    std::lock_guard<std::mutex> lock(mLock);

    uint32_t brightness, color, rgb[3];
    LightState localState = state;

    // If a brightness has been applied by the user
    brightness = (localState.color & 0xff000000) >> 24;
    if (brightness > 0 && brightness < 255) {
        // Retrieve each of the RGB colors
        color = localState.color & 0x00ffffff;
        rgb[0] = (color >> 16) & 0xff;
        rgb[1] = (color >> 8) & 0xff;
        rgb[2] = color & 0xff;

        // Apply the brightness level
        if (rgb[0] > 0) {
            rgb[0] = (rgb[0] * brightness) / 0xff;
        }
        if (rgb[1] > 0) {
            rgb[1] = (rgb[1] * brightness) / 0xff;
        }
        if (rgb[2] > 0) {
            rgb[2] = (rgb[2] * brightness) / 0xff;
        }

        // Update with the new color
        localState.color = (rgb[0] << 16) + (rgb[1] << 8) + rgb[2];
    }

    mNotificationState = localState;
    setSpeakerBatteryLightLocked();
}

void Light::setSpeakerBatteryLightLocked() {
    if (isLit(mNotificationState)) {
        setSpeakerLightLocked(mNotificationState);
    } else if (isLit(mAttentionState)) {
        setSpeakerLightLocked(mAttentionState);
    } else if (isLit(mBatteryState)) {
        setSpeakerLightLocked(mBatteryState);
    } else {
        // Lights off
        mRedLed << 0 << std::endl;
        mGreenLed << 0 << std::endl;
        mBlueLed << 0 << std::endl;
        mRedBlink << 0 << std::endl;
        mGreenBlink << 0 << std::endl;
        mBlueBlink << 0 << std::endl;
    }
}

void Light::setSpeakerLightLocked(const LightState& state) {
    int red, green, blue, blink;
    int onMs, offMs, stepDuration, pauseHi;
    uint32_t colorRGB = state.color;

    switch (state.flashMode) {
        case Flash::TIMED:
            onMs = state.flashOnMs;
            offMs = state.flashOffMs;
            break;
        case Flash::NONE:
        default:
            onMs = 0;
            offMs = 0;
            break;
    }

    red = (colorRGB >> 16) & 0xff;
    green = (colorRGB >> 8) & 0xff;
    blue = colorRGB & 0xff;
    blink = onMs > 0 && offMs > 0;

    // Disable all blinking to start
    mRedBlink << 0 << std::endl;
    mGreenBlink << 0 << std::endl;
    mBlueBlink << 0 << std::endl;

    if (blink) {
        stepDuration = RAMP_STEP_DURATION;
        pauseHi = onMs - (stepDuration * RAMP_SIZE * 2);

        if (stepDuration * RAMP_SIZE * 2 > onMs) {
            stepDuration = onMs / (RAMP_SIZE * 2);
            pauseHi = 0;
        }

        // Red
        mRedStartIdx << 0 << std::endl;
        mRedDutyPcts << getScaledDutyPcts(red) << std::endl;
        mRedPauseLo << offMs << std::endl;
        mRedPauseHi << pauseHi << std::endl;
        mRedRampStepMs << stepDuration << std::endl;

        // Green
        mGreenStartIdx << RAMP_SIZE << std::endl;
        mGreenDutyPcts << getScaledDutyPcts(green) << std::endl;
        mGreenPauseLo << offMs << std::endl;
        mGreenPauseHi << pauseHi << std::endl;
        mGreenRampStepMs << stepDuration << std::endl;

        // Blue
        mBlueStartIdx << RAMP_SIZE * 2 << std::endl;
        mBlueDutyPcts << getScaledDutyPcts(blue) << std::endl;
        mBluePauseLo << offMs << std::endl;
        mBluePauseHi << pauseHi << std::endl;
        mBlueRampStepMs << stepDuration << std::endl;

        // Start the party
        //mRedBlink << 1 << std::endl;
        mGreenBlink << 1 << std::endl;
        //mBlueBlink << 1 << std::endl;
    } else {
        mRedLed << red << std::endl;
        mGreenLed << green << std::endl;
        mBlueLed << blue << std::endl;
    }
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace light
}  // namespace hardware
}  // namespace android
