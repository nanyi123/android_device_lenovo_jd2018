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

# This contains the module build definitions for the hardware-specific
# components for this device.
#
# As much as possible, those components should be built unconditionally,
# with device-specific names to avoid collisions, to avoid device-specific
# bitrot and build breakages. Building a component unconditionally does
# *not* include it on all devices, so it is safe even with hardware-specific
# components.

LOCAL_PATH := $(call my-dir)

ifeq ($(TARGET_DEVICE),jd2018)
include $(call all-makefiles-under,$(LOCAL_PATH))

# ---------------------------------------------------------------------------
# A16: AOSP 删掉了 build/make/core/tasks/kernel.mk，因此
#   $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ/usr
# 再没有任何规则去生成它。但 camera（QCamera2/common.mk）和
# audio_amplifier 仍然把它当 include 目录 + LOCAL_ADDITIONAL_DEPENDENCIES，
# 于是 ninja 报 "missing and no known rule to make it"。
# 这里自己补一份 kernel UAPI headers（headers_install）+ techpack/audio 头。
# ---------------------------------------------------------------------------
KERNEL_SRC := $(abspath $(TARGET_KERNEL_SOURCE))
KERNEL_OBJ := $(TARGET_OUT_INTERMEDIATES)/KERNEL_OBJ

# A16 禁止构建期从 PATH 调 make（Changes.md#PATH_Tools，报 "Disallowed PATH tool"），
# 必须用绝对路径的预编译 make，和 vendor/lineage 的 KERNEL_MAKE_CMD 一致。
ifeq ($(KERNEL_MAKE_CMD),)
KERNEL_MAKE_CMD := $(BUILD_TOP)/prebuilts/build-tools/$(HOST_PREBUILT_TAG)/bin/make
endif
KERNEL_ARCH ?= arm64

# ⚠️ 不能直接以 $(KERNEL_OBJ)/usr 目录为 ninja 目标：内核 kbuild 自己也会往
# usr/ 写 initramfs 产物（built-in.a/gen_init_cpio）。若先编了内核（如 m bootimage）
# 目录已存在，ninja 会认为本规则"已完成"而跳过 headers_install，camera 32 位
# 编译就会报 media/msmb_camera.h not found。因此用目录内的戳文件做目标。
KERNEL_HDR_STAMP := $(KERNEL_OBJ)/usr/.headers_stamp

$(KERNEL_HDR_STAMP):
	@echo "Installing kernel headers: $(KERNEL_OBJ)/usr"
	$(hide) mkdir -p $(KERNEL_OBJ)
	$(hide) $(KERNEL_MAKE_CMD) $(KERNEL_MAKE_FLAGS) -C $(KERNEL_SRC) \
	    O=$(abspath $(KERNEL_OBJ))/hdr ARCH=$(KERNEL_ARCH) \
	    headers_install INSTALL_HDR_PATH=$(abspath $(KERNEL_OBJ)/usr)
# 4.19 的 UAPI 会把 libc 私有的 struct sched_param / struct sigaction 暴露给用户态，
# 而 A16 bionic 的 sched.h / bits/signal_types.h 里已经有这两份定义，导致
# "redefinition of 'sched_param' / 'sigaction'"。上游 bionic 生成 sanitized UAPI 时
# 就是把这两段删掉的，这里做同样处理（用户态本来就该用 libc 的版本）。
	$(hide) sed -i '/^struct sched_param {/,/^};$$/d' $(abspath $(KERNEL_OBJ)/usr)/include/linux/sched/types.h
	$(hide) sed -i '/^struct sigaction {/,/^};$$/d' $(abspath $(KERNEL_OBJ)/usr)/include/asm-generic/signal.h
	$(hide) mkdir -p $(KERNEL_OBJ)/usr/techpack/audio/include
	$(hide) cp -rf $(KERNEL_SRC)/techpack/audio/include/* $(KERNEL_OBJ)/usr/techpack/audio/include/
	$(hide) touch $@

include $(CLEAR_VARS)

# A/B builds require us to create the mount points at compile time.
# Just creating it for all cases since it does not hurt.
FIRMWARE_MOUNT_POINT := $(TARGET_OUT_VENDOR)/firmware_mnt
BT_FIRMWARE_MOUNT_POINT := $(TARGET_OUT_VENDOR)/bt_firmware
DSP_MOUNT_POINT := $(TARGET_OUT_VENDOR)/dsp

$(FIRMWARE_MOUNT_POINT):
	@echo "Creating $(FIRMWARE_MOUNT_POINT)"
	@mkdir -p $(TARGET_OUT_VENDOR)/firmware_mnt

$(BT_FIRMWARE_MOUNT_POINT):
	@echo "Creating $(BT_FIRMWARE_MOUNT_POINT)"
	@mkdir -p $(TARGET_OUT_VENDOR)/bt_firmware

$(DSP_MOUNT_POINT):
	@echo "Creating $(DSP_MOUNT_POINT)"
	@mkdir -p $(TARGET_OUT_VENDOR)/dsp

ALL_DEFAULT_INSTALLED_MODULES += $(FIRMWARE_MOUNT_POINT) $(BT_FIRMWARE_MOUNT_POINT) $(DSP_MOUNT_POINT)

RFS_MSM_ADSP_SYMLINKS := $(TARGET_OUT_VENDOR)/rfs/msm/adsp/
$(RFS_MSM_ADSP_SYMLINKS): $(LOCAL_INSTALLED_MODULE)
	@echo "Creating RFS MSM ADSP folder structure: $@"
	@rm -rf $@/*
	@mkdir -p $(dir $@)/readonly/vendor
	$(hide) ln -sf /data/vendor/tombstones/rfs/lpass $@/ramdumps
	$(hide) ln -sf /mnt/vendor/persist/rfs/msm/adsp $@/readwrite
	$(hide) ln -sf /mnt/vendor/persist/rfs/shared $@/shared
	$(hide) ln -sf /mnt/vendor/persist/hlos_rfs/shared $@/hlos
	$(hide) ln -sf /vendor/firmware_mnt $@/readonly/firmware
	$(hide) ln -sf /vendor/firmware $@/readonly/vendor/firmware

RFS_MSM_MPSS_SYMLINKS := $(TARGET_OUT_VENDOR)/rfs/msm/mpss/
$(RFS_MSM_MPSS_SYMLINKS): $(LOCAL_INSTALLED_MODULE)
	@echo "Creating RFS MSM MPSS folder structure: $@"
	@rm -rf $@/*
	@mkdir -p $(dir $@)/readonly/vendor
	$(hide) ln -sf /data/vendor/tombstones/rfs/modem $@/ramdumps
	$(hide) ln -sf /mnt/vendor/persist/rfs/msm/mpss $@/readwrite
	$(hide) ln -sf /mnt/vendor/persist/rfs/shared $@/shared
	$(hide) ln -sf /mnt/vendor/persist/hlos_rfs/shared $@/hlos
	$(hide) ln -sf /vendor/firmware_mnt $@/readonly/firmware
	$(hide) ln -sf /vendor/firmware $@/readonly/vendor/firmware

RFS_MSM_SLPI_SYMLINKS := $(TARGET_OUT_VENDOR)/rfs/msm/slpi/
$(RFS_MSM_SLPI_SYMLINKS): $(LOCAL_INSTALLED_MODULE)
	@echo "Creating RFS MSM SLPI folder structure: $@"
	@rm -rf $@/*
	@mkdir -p $(dir $@)/readonly/vendor
	$(hide) ln -sf /data/vendor/tombstones/rfs/slpi $@/ramdumps
	$(hide) ln -sf /mnt/vendor/persist/rfs/msm/slpi $@/readwrite
	$(hide) ln -sf /mnt/vendor/persist/rfs/shared $@/shared
	$(hide) ln -sf /mnt/vendor/persist/hlos_rfs/shared $@/hlos
	$(hide) ln -sf /vendor/firmware_mnt $@/readonly/firmware
	$(hide) ln -sf /vendor/firmware $@/readonly/vendor/firmware

ALL_DEFAULT_INSTALLED_MODULES += $(RFS_MSM_ADSP_SYMLINKS) $(RFS_MSM_MPSS_SYMLINKS) $(RFS_MSM_SLPI_SYMLINKS)

WCNSS_INI_SYMLINK := $(TARGET_OUT_VENDOR)/firmware/wlan/qca_cld/WCNSS_qcom_cfg.ini
$(WCNSS_INI_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@echo "WCNSS config ini link: $@"
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) ln -sf /vendor/etc/wifi/$(notdir $@) $@

WCNSS_MAC_SYMLINK := $(TARGET_OUT_VENDOR)/firmware/wlan/qca_cld/wlan_mac.bin
$(WCNSS_MAC_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@echo "WCNSS MAC bin link: $@"
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) ln -sf /mnt/vendor/persist/$(notdir $@) $@

ALL_DEFAULT_INSTALLED_MODULES += $(WCNSS_INI_SYMLINK) $(WCNSS_MAC_SYMLINK)

endif
