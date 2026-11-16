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
PRODUCT_ADB_KEYS := /home/motoheaven/.android/adbkey.pub
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += ro.adb.secure=0

# Inherit some common Lineage stuff.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)

# Inherit from jd2018 device
$(call inherit-product, $(LOCAL_PATH)/device.mk)

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

PRODUCT_BUILD_PROP_OVERRIDES += \
    TARGET_DEVICE="jd2018" \
    PRODUCT_NAME="jd2018" \
    PRIVATE_BUILD_DESC="jd2018-user 9 PKQ1.181218.001 10.5.254_190404 release-keys"

BUILD_FINGERPRINT := Lenovo/jd2018/jd2018:9/PKQ1.181218.001/10.5.254_190404:user/release-keys
