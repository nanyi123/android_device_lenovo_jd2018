#
# Copyright (C) 2018-2024 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/product_launched_with_p.mk)

# Enable ADB without authentication for bringup
WITH_ADB_INSECURE := true
# A16 的 blueprint 不允许 PRODUCT_ADB_KEYS 指到源码树外面（会 panic：
# removeSrcDirPrefix ... ../.android/adbkey.pub），所以把公钥放进了设备树。
PRODUCT_ADB_KEYS := device/lenovo/jd2018/security/adbkey.pub
# A16 的 gen_build_prop.py 会无条件往 system/build.prop 写 ro.adb.secure=1
# （它只区分 eng 与 user，userdebug 也算 user），post_process_props 遇到
# 同名不同值直接报错 "found duplicate sysprop assignments"。
# 所以这里不能再往 system 分区写 ro.adb.secure，免认证由 WITH_ADB_INSECURE
# 落到 product_system_ext 的 build.prop（加载顺序在 system 之后，生效）。

# Inherit some common Lineage stuff.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

# Inherit from jd2018 device
$(call inherit-product, $(LOCAL_PATH)/device.mk)

# 注意：PRODUCT_ENFORCE_VINTF_MANIFEST 不能在这里设。它是 config.mk 里
# .KATI_READONLY 的强制变量（且 _OVERRIDE 版已 obsolete，用了直接报错），
# 产品 mk 里赋值会被覆盖。A16 只发 FCM>=5 框架矩阵，本机 target-level=2、
# 老 HAL 在 5/6/7/8 各级矩阵里分别"未声明"或"已 deprecated"，检查天生过不了，
# 所以关它的补丁落在 build/make/core/config.mk 的 TARGET_DEVICE=jd2018 分支里。


# Bootanimation
TARGET_BOOT_ANIMATION_RES := 1080

# Device identifier. This must come after all inclusions.
PRODUCT_BRAND := Lenovo
PRODUCT_DEVICE := jd2018
PRODUCT_MANUFACTURER := Lenovo
PRODUCT_MODEL := Lenovo L78011
PRODUCT_NAME := lineage_jd2018
PRODUCT_GMS_CLIENTID_BASE := android-lenovo

TARGET_VENDOR_PRODUCT_NAME := jd2018
TARGET_VENDOR_DEVICE_NAME := jd2018

# A16 起 gen_build_prop.py 只认 product_config.json 里存在的键，且 BuildDesc 改为
# 自己用 DeviceName/BuildVariant/版本号/BuildId 拼出来（TARGET_DEVICE、PRODUCT_NAME、
# PRIVATE_BUILD_DESC 三个 override 键都已从 config 里移除），继续写会直接失败：
#   Key "TARGET_DEVICE" isn't a valid prop override
# 所以这里只保留能生效的 BUILD_FINGERPRINT（走 --build-fingerprint-file）。
# ro.product.device 本就等于 PRODUCT_DEVICE=jd2018，不需要再 override。
BUILD_FINGERPRINT := Lenovo/jd2018/jd2018:9/PKQ1.181218.001/10.5.254_190404:user/release-keys
