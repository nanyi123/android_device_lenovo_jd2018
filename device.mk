#
# Copyright (C) 2018 The Mokee Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# This file sets variables that control the way modules are built
# thorughout the system. It should not be used to conditionally
# disable makefiles (the proper mechanism to control what gets
# included in a build is to use PRODUCT_PACKAGES in a product
# definition file).
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/product_launched_with_p.mk)
$(call inherit-product, frameworks/native/build/phone-xhdpi-4096-dalvik-heap.mk)

# Cgroup and task_profiles
PRODUCT_COPY_FILES += \
    system/core/libprocessgroup/profiles/cgroups_28.json:$(TARGET_COPY_OUT_VENDOR)/etc/cgroups.json \
    system/core/libprocessgroup/profiles/task_profiles_28.json:$(TARGET_COPY_OUT_VENDOR)/etc/task_profiles.json

# Get non-open-source specific aspects
$(call inherit-product-if-exists, vendor/lenovo/jd2018/jd2018-vendor.mk)

# A/B
PRODUCT_PACKAGES += \
  update_engine \
  update_verifier

AB_OTA_POSTINSTALL_CONFIG += \
    FILESYSTEM_TYPE_system=ext4 \
    POSTINSTALL_OPTIONAL_system=true \
    POSTINSTALL_PATH_system=system/bin/otapreopt_script \
    RUN_POSTINSTALL_system=true

AB_OTA_PARTITIONS := \
  boot \
  system \
  vendor \
  vbmeta

AB_OTA_UPDATER := true

PRODUCT_PACKAGES_DEBUG += \
    bootctl

# Boot control HAL
PRODUCT_PACKAGES += \
    android.hardware.boot@1.0-impl.recovery \
    bootctrl.sdm660.recovery \
    libgptutils.jd2018.recovery

PRODUCT_PACKAGES += \
    android.hardware.boot@1.0-impl \
    android.hardware.boot@1.0-service \
    bootctrl.sdm660

# QRTR nameserver - modem EFS/rmtfs 依赖它注册服务，缺失会导致 modem 40s 后 err_fatal
# qrtr-cfg/qrtr-lookup: 调试工具，观察 WLFW(0x45) 等服务注册状态
PRODUCT_PACKAGES += \
    qrtr-ns \
    qrtr-cfg \
    qrtr-lookup

# Boot animation
TARGET_SCREEN_HEIGHT := 2340
TARGET_SCREEN_WIDTH := 1080

# Vendor properties handled by BoardConfig.mk TARGET_VENDOR_PROP
# -include $(LOCAL_PATH)/vendor_prop.mk

# Overlays
DEVICE_PACKAGE_OVERLAYS += \
    $(LOCAL_PATH)/overlay

# 不强制 framework-res 走 RRO：overlay 走 legacy static overlay，res/xml 与 res/values
# 一起直接编进 framework-res.apk，少一层运行时间接（RRO apk 会散在 vendor 分区，
# 改资源还得连带刷 vendor）。RRO 本身能覆盖 res/xml，这里只是选择更直接的路径。
# PRODUCT_ENFORCE_RRO_TARGETS := \
#     framework-res

# Permissions
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.audio.low_latency.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.audio.low_latency.xml \
    frameworks/native/data/etc/android.hardware.audio.pro.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.audio.pro.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.camera.full.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.full.xml \
    frameworks/native/data/etc/android.hardware.camera.raw.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.raw.xml \
    frameworks/native/data/etc/android.hardware.location.gps.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.location.gps.xml \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.opengles.aep.xml \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.accelerometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.ambient_temperature.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.ambient_temperature.xml \
    frameworks/native/data/etc/android.hardware.sensor.barometer.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.barometer.xml \
    frameworks/native/data/etc/android.hardware.sensor.compass.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.compass.xml \
    frameworks/native/data/etc/android.hardware.sensor.gyroscope.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.gyroscope.xml \
    frameworks/native/data/etc/android.hardware.sensor.hifi_sensors.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.hifi_sensors.xml \
    frameworks/native/data/etc/android.hardware.sensor.light.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.light.xml \
    frameworks/native/data/etc/android.hardware.sensor.proximity.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.proximity.xml \
    frameworks/native/data/etc/android.hardware.sensor.relative_humidity.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.relative_humidity.xml \
    frameworks/native/data/etc/android.hardware.sensor.stepcounter.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.stepcounter.xml \
    frameworks/native/data/etc/android.hardware.sensor.stepdetector.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.stepdetector.xml \
    frameworks/native/data/etc/android.hardware.telephony.cdma.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.cdma.xml \
    frameworks/native/data/etc/android.hardware.telephony.gsm.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.gsm.xml \
    frameworks/native/data/etc/android.hardware.telephony.ims.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.ims.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.jazzhand.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml \
    frameworks/native/data/etc/android.hardware.vulkan.compute-0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.compute-0.xml \
    frameworks/native/data/etc/android.hardware.vulkan.level-0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.level-0.xml \
    frameworks/native/data/etc/android.hardware.vulkan.version-1_1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.version-1_1.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.wifi.passpoint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.passpoint.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.software.midi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.midi.xml \
    frameworks/native/data/etc/android.software.sip.voip.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.sip.voip.xml \
    frameworks/native/data/etc/handheld_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/handheld_core_hardware.xml

# Device uses high-density artwork where available
PRODUCT_AAPT_CONFIG := normal
PRODUCT_AAPT_PREF_CONFIG := xxhdpi

# AliPay
PRODUCT_PACKAGES += \
    org.ifaa.android.manager

PRODUCT_BOOT_JARS += \
    org.ifaa.android.manager

# Audio
PRODUCT_PACKAGES += \
    android.hardware.audio@6.0-impl \
    android.hardware.audio.effect@6.0-impl \
    android.hardware.audio.service \
    android.hardware.soundtrigger@2.2-impl:32 \
    audio.a2dp.default.jd2018 \
    audio.primary.sdm660 \
    audio.r_submix.default \
    audio.usb.default \
    libaudio-resampler \
    libqcompostprocbundle \
    libqcomvisualizer \
    libqcomvoiceprocessing \
    libvolumelistener \
    tinymix

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/audio/audio_effects.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects.xml \
    $(LOCAL_PATH)/audio/audio_output_policy.conf:$(TARGET_COPY_OUT_VENDOR)/etc/audio_output_policy.conf \
    $(LOCAL_PATH)/audio/audio_platform_info_intcodec.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_platform_info_intcodec.xml \
    $(LOCAL_PATH)/audio/audio_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_platform_info.xml \
    $(LOCAL_PATH)/audio/audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    $(LOCAL_PATH)/audio/audio_tuning_mixer.txt:$(TARGET_COPY_OUT_VENDOR)/etc/audio_tuning_mixer.txt \
    $(LOCAL_PATH)/audio/graphite_ipc_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/graphite_ipc_platform_info.xml \
    $(LOCAL_PATH)/audio/listen_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/listen_platform_info.xml \
    $(LOCAL_PATH)/audio/mixer_paths.xml:$(TARGET_COPY_OUT_VENDOR)/etc/mixer_paths.xml \
    $(LOCAL_PATH)/audio/sound_trigger_mixer_paths.xml:$(TARGET_COPY_OUT_VENDOR)/etc/sound_trigger_mixer_paths.xml \
    $(LOCAL_PATH)/audio/sound_trigger_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/sound_trigger_platform_info.xml

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/audio/a2dp_audio_policy_configuration.xml:/$(TARGET_COPY_OUT_VENDOR)/etc/a2dp_audio_policy_configuration.xml \
    $(TOPDIR)frameworks/av/services/audiopolicy/config/audio_policy_volumes.xml:/$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml \
    $(TOPDIR)frameworks/av/services/audiopolicy/config/default_volume_tables.xml:/$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml \
    $(TOPDIR)frameworks/av/services/audiopolicy/config/r_submix_audio_policy_configuration.xml:/$(TARGET_COPY_OUT_VENDOR)/etc/r_submix_audio_policy_configuration.xml \
    $(TOPDIR)frameworks/av/services/audiopolicy/config/usb_audio_policy_configuration.xml:/$(TARGET_COPY_OUT_VENDOR)/etc/usb_audio_policy_configuration.xml

# Bluetooth
PRODUCT_PACKAGES += \
    android.hardware.bluetooth@1.0.vendor \
    com.dsi.ant@1.0.vendor \
    vendor.qti.hardware.btconfigstore@1.0.vendor \
    vendor.qti.hardware.btconfigstore@2.0.vendor

# Sensor HAL conf file
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/sensors/hals.conf:$(TARGET_COPY_OUT_VENDOR)/etc/sensors/hals.conf \
    $(LOCAL_PATH)/sensors/sensor_def_qcomdev.conf:$(TARGET_COPY_OUT_VENDOR)/etc/sensors/sensor_def_qcomdev.conf

# 光感/接近（cm36656）：sensors.ALSPS.so 以 system 用户运行，需要 /dev/{lsensor,psensor}
# 与 /sys/class/sensors/*/switch 的可写权限，否则 light/proximity 注册了也读不到值
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/init/init.jd2018.sensors.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.jd2018.sensors.rc

# Camera
# 注意: camera.sdm660 / libmmcamera_interface / libmmjpeg_interface / libqomx_core
# 必须用 ZUI 原厂 blob（proprietary-files.txt 登记），绝不能用源码编版：
# 1) 源码编 HAL 的 scaler 流配置损坏（height=1），CameraX 初始化崩溃
# 2) blob 之间(含 libqomx_jpegenc_pipe.so)互相按原厂符号匹配，混搭必炸
# 3) blob 的 JPEG 管线按编译期偏移裸读 gralloc handle，
#    依赖 commonsys-intf/display/gralloc/gr_priv_handle.h 的 ZUI 兼容布局（已重排，勿改回！）
PRODUCT_PACKAGES += \
    android.hardware.camera.common@1.0 \
    android.hardware.camera.device@3.4 \
    android.hardware.camera.provider@2.4-impl \
    android.hardware.camera.provider@2.4-service \
    android.hardware.camera.provider@2.6 \
    vendor.qti.hardware.camera.device@1.0 \
    android.frameworks.displayservice@1.0_32 \
    libxml2

# CNE
PRODUCT_PACKAGES += \
    cneapiclient \
    com.quicinc.cne

# Display
PRODUCT_PACKAGES += \
    android.hardware.graphics.composer@2.1-service \
    android.hardware.graphics.mapper@3.0-impl-qti-display \
    android.hardware.graphics.mapper@4.0-impl-qti-display \
    vendor.qti.hardware.memtrack-service \
    android.frameworks.displayservice@1.0 \
    android.frameworks.displayservice@1.0.vendor \
    vendor.display.config@1.0 \
    vendor.display.config@1.0.vendor \
    vendor.display.config@2.0 \
    vendor.display.config@2.0.vendor \
    vendor.qti.hardware.display.allocator-service \
    vendor.qti.hardware.display.mapper@1.1.vendor \
    vendor.qti.hardware.display.mapper@2.0.vendor \
    gralloc.sdm660 \
    hwcomposer.qcom \
    libdisplayconfig \
    libgralloc.qti \
    libqdMetaData \
    libqdMetaData.system \
    libtinyxml

# Display Device Config
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/displayconfig/display_id_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_id_0.xml

PRODUCT_PACKAGES += \
    android.hardware.broadcastradio@1.0-impl

# Doze
PRODUCT_PACKAGES += \
    MoKeeActions \
    PositionTravel

# DRM
PRODUCT_PACKAGES += \
    android.hardware.drm@1.0-impl \
    android.hardware.drm@1.0-service \
    android.hardware.drm@1.1.vendor \
    android.hardware.drm-service.clearkey

# Fingerprint
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.fingerprint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.fingerprint.xml

# FM
PRODUCT_PACKAGES += \
    FM2 \
    libqcomfm_jni \
    qcom.fmradio

# For android_filesystem_config.h
PRODUCT_PACKAGES += \
    fs_config_files

# GPS
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/gps/apdr.conf:$(TARGET_COPY_OUT_VENDOR)/etc/apdr.conf \
    $(LOCAL_PATH)/configs/gps/flp.conf:$(TARGET_COPY_OUT_VENDOR)/etc/flp.conf \
    $(LOCAL_PATH)/configs/gps/gps.conf:$(TARGET_COPY_OUT_VENDOR)/etc/gps.conf \
    $(LOCAL_PATH)/configs/gps/izat.conf:$(TARGET_COPY_OUT_VENDOR)/etc/izat.conf \
    $(LOCAL_PATH)/configs/gps/lowi.conf:$(TARGET_COPY_OUT_VENDOR)/etc/lowi.conf \
    $(LOCAL_PATH)/configs/gps/sap.conf:$(TARGET_COPY_OUT_VENDOR)/etc/sap.conf \
    $(LOCAL_PATH)/configs/gps/xtwifi.conf:$(TARGET_COPY_OUT_VENDOR)/etc/xtwifi.conf

PRODUCT_PACKAGES += \
    android.hardware.gnss@2.1.vendor \
    android.system.net.netd@1.1.vendor \
    libstagefright_omx.vendor \
    libgnss \
    libgnsspps \
    libsensorndkbridge

# Health
PRODUCT_PACKAGES += \
    android.hardware.health-service.example \
    android.hardware.health@2.1-service \
    android.hardware.health@2.1-impl

# HIDL
# android.hidl.base@1.0 / manager@1.0 在 A14 里由 hardware/lineage/compat 以空壳
# cc_library_shared 提供（system_ext_specific）。goodix 指纹 blob 的 DT_NEEDED
# 需要 /vendor/lib64 下也有同名 soname，否则 dlopen 直接 "cannot find"。
# .vendor 后缀显式选 vendor 变体安装（不回退到头文件/静态链接），见 a14-fixes-checklist §16
PRODUCT_PACKAGES += \
    android.hidl.base@1.0 \
    android.hidl.base@1.0.vendor \
    android.hidl.manager@1.0 \
    libhidltransport \
    libhidltransport.vendor \
    libhwbinder \
    libhwbinder.vendor

# IMS
PRODUCT_PACKAGES += \
    ims-ext-common \
    ims_ext_common.xml \
    qti-telephony-hidl-wrapper \
    qti_telephony_hidl_wrapper.xml \
    qti-telephony-utils \
    qti_telephony_utils.xml

# IPACM
PRODUCT_PACKAGES += \
    ipacm \
    IPACM_cfg.xml \
    libipanat \
    liboffloadhal

# IRQ
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/msm_irqbalance.conf:$(TARGET_COPY_OUT_VENDOR)/etc/msm_irqbalance.conf

# IRSC
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/sec_config:$(TARGET_COPY_OUT_VENDOR)/etc/sec_config

# Input
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/keylayout/sdm660-mtp-snd-card_Button_Jack.kl:system/usr/keylayout/sdm660-mtp-snd-card_Button_Jack.kl \
    $(LOCAL_PATH)/keylayout/fts_ts.kl:system/usr/keylayout/fts_ts.kl \
    $(LOCAL_PATH)/keylayout/slip-cover.kl:system/usr/keylayout/slip-cover.kl
# Gatekeeper
PRODUCT_PACKAGES += \
    android.hardware.gatekeeper@1.0.vendor

# Keymaster HAL
# 不要装 AOSP 的 android.hardware.keymaster@3.0-impl / @3.0-service：
# 它们会注册 IKeymasterDevice/default（passthrough 到 keystore.<board>.so -> keymaster0），
# 与 TEE 的 -service-qti 抢同一个 HIDL 实例名，超级密钥落到软件实现后重启即失效
PRODUCT_PACKAGES += \
    android.hardware.keymaster@3.0.vendor

# librpmb：必须用华硕 A14 版（配 4.19 内核的 chardev /dev/mmcblk0rpmb）
# ZUI 的 A9 版只认 /dev/block/mmcblk0rpmb，会让 qseecomd 的 RPMB 服务起不来
PRODUCT_PACKAGES += \
    librpmb

# A9 时代位于 /system 的三个库：A9 的 vendor blob 直接依赖它们，
# A16 的 vendor 命名空间不再可见（不在 LLNDK 名单），必须随 /vendor 提供。
# 用 PRODUCT_COPY_FILES 直接摆到 /vendor/lib{,64}：
#   - cc_prebuilt_library_shared 会撞 AOSP vendor 变体的 elf symbols 规则
#     （multiple rules generate .../symbols/vendor/lib{,64}/libpng.so）
#   - prebuilt_etc 的 sub_dir 相对 /vendor/etc，装不到 /vendor/lib
# 文件来源与失败清单见 prebuilts/a9-system-libs/ 目录。
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/prebuilts/a9-system-libs/lib64/libsqlite.so:$(TARGET_COPY_OUT_VENDOR)/lib64/libsqlite.so \
    $(LOCAL_PATH)/prebuilts/a9-system-libs/lib/libsqlite.so:$(TARGET_COPY_OUT_VENDOR)/lib/libsqlite.so \
    $(LOCAL_PATH)/prebuilts/a9-system-libs/lib64/libpower.so:$(TARGET_COPY_OUT_VENDOR)/lib64/libpower.so \
    $(LOCAL_PATH)/prebuilts/a9-system-libs/lib/libpower.so:$(TARGET_COPY_OUT_VENDOR)/lib/libpower.so \
    $(LOCAL_PATH)/prebuilts/a9-system-libs/lib/libpng.so:$(TARGET_COPY_OUT_VENDOR)/lib/libpng.so

# Lights
PRODUCT_PACKAGES += \
    android.hardware.light@2.0-service.lenovo_jd2018

# LiveDisplay native
PRODUCT_PACKAGES += \
    vendor.lineage.livedisplay-service.sdm

# Media
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/media/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    $(LOCAL_PATH)/media/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml \
    $(LOCAL_PATH)/media/media_profiles_V1_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_audio.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_telephony.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_telephony.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_video.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_video.xml

# Net
PRODUCT_PACKAGES += \
    android.system.net.netd@1.0 \
    libandroid_net \
    netutils-wrapper-1.0

# OMX
PRODUCT_PACKAGES += \
    libc2dcolorconvert \
    libmm-omxcore \
    libOmxCore \
    libOmxVdec \
    libOmxVenc \
    libstagefrighthw

# Power
PRODUCT_PACKAGES += \
    android.hardware.power-service.example \
    android.hardware.power.stats@1.0-service.mock

# Public Libraries
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/public.libraries.txt:$(TARGET_COPY_OUT_VENDOR)/etc/public.libraries.txt

# QCOM
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/privapp-permissions-qti.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/privapp-permissions-qti.xml \
    $(LOCAL_PATH)/configs/privapp-permissions-qti.xml:system/etc/permissions/privapp-permissions-qti.xml \
    $(LOCAL_PATH)/configs/qti_whitelist.xml:system/etc/sysconfig/qti_whitelist.xml


# QMI
PRODUCT_PACKAGES += \
    libjson \
    libqti_vndfwk_detect \
    libqti_vndfwk_detect.vendor

# Vibrator (A16 只有 AIDL HAL；HIDL vibrator 已从 AOSP 移除)
# example HAL 是 no-op；换成 Lineage legacy 包装，通过 hw_get_module 加载
# vendor 的 vibrator.default.so，转发到 timed_output（实机震动已验证）
PRODUCT_PACKAGES += \
    android.hardware.vibrator-service.legacy

# Ramdisk
PRODUCT_PACKAGES += \
    init.class_main.sh \
    pstore2logdump.sh \
    init.msm.usb.configfs.rc \
    init.qcom.early_boot.sh \
    init.qcom.post_boot.sh \
    init.qcom.rc \
    init.lenovo.common.rc \
    init.lenovo.rc \
    init.lenovo.usb.configfs.rc \
    init.qcom.sensors.sh \
    init.qcom.sh \
    init.qcom.usb.rc \
    init.qcom.usb.sh \
    init.target.rc \
    ueventd.qcom.rc \
    fstab.qcom

# RFS 固件映射树（modem user PD 加载 wlanmdsp.mbn 等依赖）
PRODUCT_PACKAGES += \
    vendor.rfs.rc

# RenderScript HAL
PRODUCT_PACKAGES += \
    android.hardware.renderscript@1.0-impl


# RIL
PRODUCT_PACKAGES += \
    android.hardware.radio@1.0.vendor \
    android.hardware.radio@1.1.vendor \
    android.hardware.radio@1.2.vendor \
    android.hardware.radio@1.3.vendor \
    android.hardware.radio@1.4.vendor \
    android.hardware.radio@1.5.vendor \
    android.hardware.radio.config@1.0.vendor \
    android.hardware.radio.config@1.1.vendor \
    android.hardware.radio.config@1.2.vendor \
    android.hardware.radio.deprecated@1.0.vendor \
    android.hardware.secure_element@1.0.vendor \
    android.hardware.secure_element@1.1.vendor \
    android.hardware.secure_element@1.2.vendor \
    libprotobuf-cpp-full-3.9.1-vendorcompat \
    android.hardware.radio@1.2 \
    android.hardware.radio.config@1.0 \
    android.hardware.radio.config@1.1 \
    android.hardware.radio.config@1.2 \
    android.hardware.secure_element@1.0 \
    librmnetctl \
    libxml2 \
    libprotobuf-cpp-full

# Seccomp
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/seccomp/mediacodec-seccomp.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediacodec.policy \
    $(LOCAL_PATH)/seccomp/mediaextractor-seccomp.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediaextractor.policy

# Sensors
PRODUCT_PACKAGES += \
    android.hardware.sensors@1.0-impl \
    android.hardware.sensors@1.0-service \
    android.frameworks.sensorservice@1.0 \
    android.frameworks.sensorservice@1.0.vendor

# Telephony
PRODUCT_PACKAGES += \
    telephony-ext

PRODUCT_BOOT_JARS += \
    telephony-ext

# Thermal
# 和老高通平台保持一致：不写设备专用 HAL，直接用 AOSP 的通用实现
# (hardware/libhardware/modules/thermal)，它以 thermal.default 的名字装进
# /vendor/lib64/hw/，由 android.hardware.thermal@1.0-service 通过
# hw_get_module("thermal") 加载。ZUI(A9) 的 thermal.sdm660.so 硬编码
# tsens_tz_sensorN，与本内核的 zone 名（cpu-1-0-usr/pm660-tz/...）零交集，
# 已于 2026-11-12 从 vendor 中移除。
#
# thermal-engine 的配置也改由设备树提供（不再用 ZUI 那份）：ZUI 是 A9/4.9 内核的产物，
# 里面 cluster/ss/gpu/battery/vdd_restriction 这几类动作依赖 msm_thermal 驱动，
# 本内核没有，实测全部初始化失败。详见 configs/thermal-engine.conf 注释。
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/thermal-engine.conf:$(TARGET_COPY_OUT_VENDOR)/etc/thermal-engine.conf

# ZUI 的 qcrilmsgtunnel 用 @1.0-java 名字声明 uses-library，AOSP 只注册了 -V1.0-java。
# 补一个别名，否则开机 QcrilMsgTunnelIfaceManager 抛 NoClassDefFoundError。
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/hidl-manager-java-alias.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/hidl-manager-java-alias.xml

PRODUCT_PACKAGES += \
    android.hardware.thermal@1.0-impl \
    android.hardware.thermal@1.0-service \
    thermal.default

# Touchscreen
PRODUCT_PACKAGES += \
    libtinyxml2

# Update Engine
PRODUCT_PACKAGES += \
    otapreopt_script \
    update_engine \
    update_engine_sideload \
    update_verifier

PRODUCT_PACKAGES_DEBUG += \
    update_engine_client

# USB AIDL HAL (Type-C / Host / Port Role switching)
PRODUCT_PACKAGES += \
    android.hardware.usb-service.example

# A16 起 adb root 由 system_ext 的 adb_root 服务（IADBRootService）仲裁：
# adbd 只读 service.adb.root 属性，而属性写入必须先经该服务 getEnabled() 放行。
# 不装它 → adb root 静默失败（属性都没被写）。lineage 的 common.mk 只在
# WITH_SU=true 时才带，本设备不用 su，需要自己装。
ifneq ($(TARGET_BUILD_VARIANT),user)
PRODUCT_PACKAGES += \
    adb_root
endif

# VNDK (A16 已移除 vndk_package)
PRODUCT_PACKAGES += \
    libgui_vendor

# Wifi
PRODUCT_PACKAGES += \
    android.hardware.wifi-service \
    hostapd \
    hostapd_cli \
    libwifi-hal-qcom \
    libwpa_client \
    wificond \
    wpa_supplicant \
    wpa_supplicant.conf

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/wifi/p2p_supplicant_overlay.conf:$(TARGET_COPY_OUT_VENDOR)/etc/wifi/p2p_supplicant_overlay.conf \
    $(LOCAL_PATH)/wifi/wpa_supplicant_overlay.conf:$(TARGET_COPY_OUT_VENDOR)/etc/wifi/wpa_supplicant_overlay.conf \
    $(LOCAL_PATH)/wifi/WCNSS_qcom_cfg.ini:$(TARGET_COPY_OUT_VENDOR)/etc/wifi/WCNSS_qcom_cfg.ini

# First-stage Ramdisk Fstab
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_RAMDISK_OUT)/first_stage_ramdisk/fstab.qcom \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_RAMDISK_OUT)/fstab.qcom \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_RAMDISK_OUT)/first_stage_ramdisk/system/etc/fstab.qcom \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_RAMDISK_OUT)/system/etc/fstab.qcom \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_RAMDISK_OUT)/first_stage_ramdisk/fstab.jd2018 \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_RAMDISK_OUT)/fstab.jd2018 \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_RAMDISK_OUT)/first_stage_ramdisk/system/etc/fstab.jd2018 \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_RAMDISK_OUT)/system/etc/fstab.jd2018 \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_RECOVERY)/root/first_stage_ramdisk/fstab.qcom \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_RECOVERY)/root/fstab.qcom \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_RECOVERY)/root/first_stage_ramdisk/system/etc/fstab.qcom \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_RECOVERY)/root/system/etc/fstab.qcom \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_RECOVERY)/root/first_stage_ramdisk/fstab.jd2018 \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_RECOVERY)/root/fstab.jd2018 \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_RECOVERY)/root/first_stage_ramdisk/system/etc/fstab.jd2018 \
    $(LOCAL_PATH)/rootdir/etc/fstab.qcom:$(TARGET_COPY_OUT_RECOVERY)/root/system/etc/fstab.jd2018 \
    $(LOCAL_PATH)/rootdir/etc/init.recovery.qcom.rc:$(TARGET_COPY_OUT_RECOVERY)/root/init.recovery.qcom.rc




# 关闭 APEX 压缩 (capex)
# 依据: EDL 取证实锤 - 解压到 /data 的 capex 必须走 dm-verity 挂载 (apexd.cpp:
# IsDecompressedApex -> mount_on_verity), 而直挂 .apex 跳过 verity。
# 高通 4.19 dm-verity 的 bufio prefetch (dm-verity-target.c, 含 QCOM 魔改
# "bigger read for emmc" 逻辑) 在解压 apex 激活时越界读 loop 设备尾部,
# 触发 handle_bad_sector 风暴 (loop4/22/31/34: rw=0, want=limit+8~88,
# culprit=kworker/u16:*)。关闭压缩后全部 apex 由 /system 直挂, 无 verity 路径。
PRODUCT_COMPRESSED_APEX := false

# 案件五：TFA9874 功放 HAL 模块（华硕X00QD audio_amplifier 方案）
PRODUCT_PACKAGES += \
    audio_amplifier.sdm660
