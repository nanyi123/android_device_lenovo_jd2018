# Device Tree for Lenovo Z5 (`jd2018` / `L78011`)

This repository contains the unified device tree for the **Lenovo Z5** (`jd2018` / `L78011`), supporting both **Android 14** (crDroid 10 / LineageOS 21) and **Android 16** (crDroid 12 / LineageOS 23) powered by Qualcomm Snapdragon 636 (SDM636) and Linux Kernel 4.19.

---

## Device Specifications

| Feature | Specification |
|:---|:---|
| **SoC** | Qualcomm SDM636 Snapdragon 636 (14 nm) |
| **CPU** | Octa-core Kryo 260 (4x 1.84 GHz Gold + 4x 1.61 GHz Silver) |
| **GPU** | Adreno 509 |
| **RAM** | 6 GB LPDDR4X |
| **Storage** | 64 GB / 128 GB eMMC 5.1 |
| **Battery** | 3300 mAh Li-Po, PM660 charging subsystem |
| **Display** | 6.2 inches IPS LCD, 1080 x 2246 pixels, 19:9 ratio, DCI-P3 |
| **Rear Camera** | Dual: 16 MP (f/2.0, PDAF) + 8 MP depth sensor, Dual-LED flash |
| **Front Camera** | 8 MP (f/2.0) |
| **Fingerprint** | Rear-mounted SPI fingerprint sensor (Goodix / Silead) |
| **Audio** | NXP TFA9874 smart amplifier, 3.5mm jack |
| **Connectivity** | Dual-band Wi-Fi (802.11 a/b/g/n/ac), Bluetooth 5.0 (A2DP, LE), GPS/GLONASS/BDS |
| **USB** | Type-C 2.0 (Host OTG 5V, AOA Google Accessory, ADB/MTP/MIDI) |

---

## Branches & Compatibility

| Branch | Android Version | Recommended ROM | Status |
|:---|:---|:---|:---|
| **`jd2018-a16`** | Android 16 (V/Baklava) | crDroid 12.x / LineageOS 23 | **Active (Daily Driver, SELinux Enforcing)** |
| **`jd2018-a14`** | Android 14 (UpsideDownCake) | crDroid 10.x / LineageOS 21 | Frozen (Stable Release) |

---

## Working Hardware & Features

- [x] **Bootloader**: Dynamic `force_normal_boot` inject (resolves legacy 2018 bootloader recovery loops)
- [x] **SELinux**: Strict **Enforcing** mode across all vendor domains
- [x] **Display & Graphics**: SurfaceFlinger, HWC, dynamic frame rates, DCI-P3 color gamut, LiveDisplay
- [x] **Audio Subsystem**: In-call earpiece, speaker, mic, wired headphones, Bluetooth A2DP
- [x] **Cameras**: Dual rear cameras, front camera, video recording (MPEG2/H.264/HEVC)
- [x] **RIL**: Dual SIM standby, 4G LTE, VoLTE, mobile data, SMS
- [x] **Wi-Fi**: Dual-band 2.4GHz / 5GHz, WPA3 SAE, hotspot
- [x] **Bluetooth**: BT 5.0, BLE, HID, A2DP socket routing
- [x] **Sensors**: Light sensor, proximity sensor, accelerometer, gyroscope, thermal zones
- [x] **Biometrics**: Fingerprint enrollment and unlock (SPI bus with PM660 LDO support)
- [x] **USB Subsystem**: Fast charge, MTP, PTP, ADB, MIDI, AOA 2.0 (CarPlay/CarLink), Host OTG (5V VBUS)
- [x] **Navigation**: GPS, GLONASS, Beidou + PositionTravel mock location support

---

## Related Repositories

- **Kernel Source (4.19)**: [android_kernel_lenovo_jd2018](https://github.com/nanyi123/android_kernel_lenovo_jd2018)
- **Vendor Configurations**: [android_vendor_lenovo_jd2018](https://github.com/nanyi123/android_vendor_lenovo_jd2018)

---

## Proprietary Vendor Files

Proprietary blobs are maintained separately following open-source conventions:
1. **From Stock Firmware**: Connect your device or provide stock ZUI 11 dump, then run:
   ```bash
   ./extract-files.sh /path/to/extracted/vendor
   ```
2. **From GitHub Releases**: Pre-packaged blob archives are available in the [android_vendor_lenovo_jd2018 Releases](https://github.com/nanyi123/android_vendor_lenovo_jd2018/releases). Extract the tarball into `vendor/lenovo/jd2018/proprietary/`.

---

## How to Build

1. Initialize the crDroid / LineageOS build environment:
   ```bash
   source build/envsetup.sh
   ```

2. Select the build target:
   ```bash
   # For Android 16 (BP4A target release):
   lunch lineage_jd2018-bp4a-userdebug
   # or
   lunch crdroid_jd2018-bp4a-userdebug
   ```

3. Build the target image:
   ```bash
   m bacon -j$(nproc)
   ```

---

## Credits & License

- Copyright (C) 2018-2024 The LineageOS Project
- Copyright (C) 2018-2026 crDroid Android Project
- Adapted and modernized for Android 16 by **motoheaven / nanyi123**
- Licensed under the Apache License, Version 2.0.
