# Wi-Fi and Bluetooth bring-up — 19 October 2026

## Current results

- A normal boot with early initialization reached `ready`, Wi-Fi association
  `COMPLETED`, an IP address and 2/2 interface-bound Internet pings.
- Bluetooth reached ON without entries in that boot's crash buffer. The bonded
  BLE mouse was connected from Leo through the HID host profile, appeared in
  Android's input service, and the user confirmed movement and clicking.
- Hotspot originally lacked both hostapd and the WLAN tethering resource
  declaration. With both installed, Leo started WPA2 AP, DHCP and mobile
  tethering without error. The user connected another phone, disabled its mobile
  data and confirmed web access through Nokia. Stopping AP from Leo succeeded.
- A second boot rearmed connectivity automatically and again restored Wi-Fi
  association and Bluetooth ON. Both post-fix boots had empty crash buffers.
- Saving the existing hotspot configuration from the keypad form succeeded;
  its private binary config hash remained identical. Test credentials and client
  identities remain excluded from Git.
- ADB still sometimes needs USB reconnection while the same boot continues.
  This is separate from the previous Qualcomm 900E episodes; long-term stability
  and accessory audio remain unverified.

## Baseline and recovered components

The previous image had no WLAN interface, no loaded WLAN module and no registered
Wi-Fi or Bluetooth hardware HAL. Bluetooth activation aborted at `btHci != nullptr`.
The board had a working stock WCNSS kernel platform driver and firmware in the
read-only modem partition, but its userspace bootstrap was missing.

Original inputs recovered from this device's dump:

- `lib/modules/pronto/pronto_wlan.ko`, matching the live kernel vermagic exactly.
  The top-level extracted `lib/modules/wlan.ko` is an empty symlink placeholder.
- WCNSS firmware and configuration; the extracted INI/NV symlink placeholders are
  also empty. The real INI is under `etc/wifi/`.
- Factory WLAN NV (31,723 bytes), read from the connected phone's persist partition
  mounted **read-only** for extraction, then unmounted. Its contents remain private.
- Stock `libbt-vendor.so`, `libbtnv.so`, and the original `wcnss_service` for study.

No boot/recovery image or original persist data is rewritten by this work.
The system partition is remounted read-only after staging/install operations.

## Initial trials (historical)

1. Stock wcnss_service loads with the radio compatibility library path, but blocks
   in its modem/QMI initialization before sending the runtime-calibration status.
2. Opening the WCNSS device triggers the stock platform firmware. WCNSS reaches
   ONLINE and loads the factory NV. A malformed one-byte trial write returned
   EFAULT; the maintained helper uses an open/close without that write.
3. The documented control message reporting absent **runtime** calibration sets
   `has_calibrated_data=0`. This does not replace or suppress the factory NV.
   This kernel returns ENOTTY after processing the write; the helper accepts the
   return only when the flag reads back as zero, and refuses to ignore an existing
   runtime-calibration file.
4. Loading the matching WLAN module after platform initialization succeeds and
   creates `wlan0` and `p2p0`. Loading it before bootstrap fails with ENODEV and
   `WCNSS driver not ready`.
5. AOSP Bluetooth HCI and Wi-Fi 1.0/1.1 services compile and register on the device.
   Missing dependencies are collected separately; existing core libraries are not
   replaced. The original Bluetooth library reports successful firmware setup.
6. Bluetooth reaches BLE_ON without crashing, but the old product omits the
   Bluetooth LE feature declaration. Oreo's manager consequently never binds GATT
   to continue enabling classic Bluetooth. Wi-Fi's feature declaration was absent
   too. New declarations are installed for a rebooted trial.
7. Wi-Fi framework additionally needs a default supplicant configuration and an
   init-managed supplicant with its Android control socket. Running the daemon
   manually does not supply the `init.svc.wpa_supplicant` lifecycle expected by
   wificond. Disabled init trial services and the minimal empty configuration are
   installed for the next trial.

Those initial staging steps alone did not establish association or accessory
operation. The current results above record the subsequent successful tests.

## Source and reproduction

- `stage-connectivity-trial.py`: stage private stock binaries/module and verified
  factory NV; add missing firmware/configuration paths, retaining system RO.
- `build-connectivity-hal.sh`: compile Bluetooth HAL, Qualcomm Wi-Fi HAL and
  supplicant with the device's NL80211/qcwcn configuration.
- Before `collect-connectivity-hal.py` in WSL, save the connected device's
  `/system/lib` and `/vendor/lib` listing to ignored
  `output/connectivity-existing-libs.txt`. WSL Windows executable interop is not
  required by the collector.
- `stage-connectivity-hal-trial.py`: stage native trial artifacts and the missing
  Bluetooth passthrough implementation. Root invocation is used during trials.
- `install-connectivity-trial.py`: install disabled trial services and supporting
  files; replacement of existing paths is limited to matching or previously
  hash-recorded files belonging to this installation.
- `init.leo.connectivity.rc`, `connectivity-start.sh`, `wcnss-no-cal-trial.sh`:
  init-managed trial lifecycle. Setup waits for stock firmware, bootstraps WCNSS,
  loads WLAN, and starts HAL services. Supplicant starts on framework request.

### Boot sequencing and bounded activation

`init.leo.connectivity-auto.rc` executes setup during **early-boot**, after Oreo
loads persistent properties and before the main boot classes. Post-fs-data was
too early to see the saved opt-in. Waiting for the radio service to mount
firmware at this stage also blocked boot; setup now verifies the modem partition
identity and mounts its firmware read-only before starting WCNSS. The radio
service reuses that mount. Original modem/NV contents are not modified.

`install-connectivity-autostart.py --prepare-trial` stages the action and keeps
the opt-in off. Arm `persist.leo.connectivity.auto=1` immediately before a
controlled reboot, avoiding a currently loaded legacy late trigger. Setup
disarms the opt-in before touching hardware. The first trial does not rearm it;
after verifying that boot, `--accept-boot` sets the validated flag and rearms it.
Subsequent successful boots rearm themselves; failed setup does not retry on the
next boot. First setup preserves saved Bluetooth state only when hardware setup
reached `ready`.

`hostapd` is an init service with the name expected by Oreo wificond; its config
is generated by the framework. The resource overlay declares `wlan[0-9]+` as a
tetherable interface. See [Leo Settings](LEO-CONNECTIVITY-SETTINGS.md) for the
privileged hotspot config permission and resource-ID-preserving installation.

Bluetooth HAL and Wi-Fi HAL retain root/init context during this engineering
trial; production permission hardening is pending. Bluetooth's generic HAL also
generated its fallback persistent address in the first trial; factory-address
integration still needs review before claiming a finished Bluetooth port.

Protocol reference: [AOSP WCNSS service, pinned revision 0e91137](https://android.googlesource.com/platform/hardware/qcom/wlan/+/0e91137/qcwcn/wcnss-service/wcnss_service.c).
The in-tree Oreo framework and HAL sources are used to diagnose GATT feature
gating, HIDL transports, and supplicant lifecycle requirements.

Raw scans, MAC addresses, calibration, binaries and detailed logs stay in ignored
local output. Never publish them as screenshots or commit them with source.
