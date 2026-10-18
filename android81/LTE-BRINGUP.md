# LTE bring-up — 18 October 2026

Initial state: all preferred network settings were 0 (GSM/WCDMA), the modem
reported mode 0 directly, SIM 1 registered on GPRS, and TeleService's
`config_enabled_lte` resource was false. `patch-phone-lte.py` enables the menu
for this LTE-capable target and is applied by both TeleService and full builds.

The platform-signed temporary `ro.leo.radiotest` diagnostic queries the modem
through TelephonyManager on a worker thread. Only explicit mode 0 (rollback)
or 9 (LTE/GSM/WCDMA automatic) can be requested; query mode changes nothing.
It never requests LTE-only, modifies modem NV, or enables IMS. Access is
intended for a local temporary engineering test, not a shipping application.

SIM subscription 1 accepted mode 9, immediately read back 9, and registered
voice/data technology LTE with both registration states IN_SERVICE. This is
LTE registration, not proof of working IMS calls or Internet access.

Data setup failed before reaching the modem because the Android APN list for
22601 was empty. The installed sample apns-conf.xml has no suitable Vodafone
profile. A separate trial APN was inserted using the public stock Vodafone Live
profile (live.vodafone.com, default/supl, IPv4), without its legacy HTTP proxy.
It is only inserted if the active subscription is verified as slot 0,
MCC 226/MNC 01, and no trial row already exists. Existing profiles are retained.
The pre-change Telephony database and radio/settings snapshots are kept in
ignored output/lte-investigation, not Git.

After the corrected system-app installation and reboot, both SIMs reached READY
and both voice slots IN_SERVICE, and SIM 1 automatically returned to LTE/mode 9.
APN insertion succeeded after verifying the actual active subscription. Data
setup now reaches Qualcomm RIL but initially fails before a DSI handle can be
allocated. The data support was not integrated in the earlier voice-only port:
`/system/etc/data/{dsi_config,netmgr_config}.xml`, the netmgrd process and its
`/dev/socket/netmgr` directory were missing. Installed the two original local
configuration files and tested stock netmgrd temporarily with the existing
private radio libraries/IPC shim. It creates rmnet0, rmnet_data0–7 and reverse
rmnet interfaces. This alone does not confirm a connected data session.

With netmgr running before restarting the RILs, data connected on rmnet_data0.
Android reported MOBILE[LTE] CONNECTED, INTERNET and VALIDATED, with an assigned
address, gateway and DNS. Two ICMP probes to 1.1.1.1 succeeded (0% loss).
The initial netmgr process was time-limited; it was replaced by the init service
`leo_netmgrd` for the persistent test, gated by `persist.leo.data.enabled=1`.
`install-data-runtime.py --install` adds the five matching local runtime files,
retains backups, checks hashes and restores the read-only system mount.
`verify-lte-runtime.py --wait` checks an automatic LTE connection, two packets,
both SIMs/voice registrations and the read-only mount after a reboot.

Final reboot verification passed: netmgr auto-started, both SIMs READY and both
voice slots registered, MOBILE[LTE] VALIDATED, two successful packets, system
read-only. The first immediate ping check during reconnection was incomplete;
the subsequent direct probe and full verifier both received two of two packets.
The menu visibly shows “Preferred network type: 4G (recommended)” and the
status bar displays 4G. The temporary radio diagnostic app was uninstalled.
No calls, VoLTE registration or VoWiFi were tested.

![LTE setting on the phone](screenshots/mobile-network-lte.png)

To disable automatic packet data support, set `persist.leo.data.enabled=0`
and reboot. This leaves the established voice-only radio boot path available.
It is separate from the preferred-radio-mode rollback documented above.

Rollback: launch the diagnostic with integer extras `sub=1, mode=0`.
The public TelephonyManager setter persists preferred_network_mode1 as well as
sending the modem request. Changing only the Settings database is insufficient.

IMS inventory: the KaiOS dump contains imsqmidaemon, imsdatadaemon,
ims_rtp_daemon and Qualcomm IMS libraries. Android has no installed IMS package
and its current carrier configuration marks VoLTE/VoWiFi unavailable. Merely
changing those booleans does not supply an Android IMS implementation. Wi-Fi,
microphone and voice-call audio are additional unverified dependencies.

## TeleService installation correction

Installing TeleService with `pm install -r` creates a data-partition update that
loses the effective PERSISTENT flag on this build. It can then crash during
background initialization (`PhoneGlobals.onCreate`, starting
TelephonyDebugService not allowed), leaving SIM subscription records unavailable.
Do not work around this by removing the SIM identity checks or disabling Android
background restrictions globally.

Saved the original system APK locally, removed the data-partition update using
`pm uninstall -k com.android.phone` (keep data), then atomically replaced
`/system/priv-app/TeleService/TeleService.apk` with the rebuilt platform-signed
APK, restored mode 0644, root ownership and SELinux context, remounted system
read-only and rebooted. The resource/null-statistics patches must be installed
as the actual persistent system app, not as a user APK update.
