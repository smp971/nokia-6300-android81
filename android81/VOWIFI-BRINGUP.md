# VoWiFi bring-up — 29 October 2026

**Modem configuration works; Wi-Fi IMS registration does not yet work.**
The bounded SIM 2 trial accepted and read back WFC enabled, but IMS registration
and voice remained on WWAN in all three samples over 36 seconds. The WFC flag
was restored to disabled. No call, SMS, airplane-mode or LTE/DDS change occurred.

## Evidence

| Check | Result |
| --- | --- |
| Wi-Fi | Connected, Internet validated by Android |
| Selected IMS / data subscription | Physical SIM 2 / subscription 2, Orange |
| Baseline IMS settings | Voice enabled, registration enabled, WFC disabled |
| IMS settings Bind `0x98`, binding 1 | Accepted on a separate diagnostic client |
| Set services `0x8f`, WFC-enable TLV `0x14=1` | Accepted; GET `0x90` confirmed WFC=1 |
| IMSA registration / voice | Registered / full service, technology 1 (WWAN) throughout |
| Wi-Fi registration | Neither WLAN (0) nor IWLAN (2) observed |
| Restoration | GET confirmed WFC=0; live selected-SIM IMS/LTE verifier passed |

This test did not change the modem's call-mode preference. A cellular-preferred
policy could keep service on WWAN even with WFC enabled. Enabling a setting
does not establish carrier provisioning, an ePDG tunnel, handover or call audio.

The old socket GET_WIFI_CALLING_STATUS request timed out on the selected client.
The stock RIL's exported v02 legacy handler calls its unsupported-request helper;
the older default-scope IMS-settings GET_CLIENT_PROVISIONING_CONFIG (`0x54`)
returned QMI error 3. Neither outcome proves that the modem lacks VoWiFi.
Modern, explicitly bound GET/SET service-enable requests succeeded instead.

A temporary Java query client was removed after diagnosis. The active APK was
restored and hash-verified as
`338bc91f21f1f640a8fcca9441d661a31910cb90e9e22cb90b5ee19298c0639d`,
matching the existing system base. System remained read-only. No experimental
WFC controller, availability override or CNE daemon was installed at boot.

## Missing integration to investigate

The original KaiOS system contains `cnd`, `libcne.so`, `libcneqmiutils.so` and
`libcneapiclient.so`, with `persist.cne.feature=4`, an init-created `cnd` socket,
and `/data/connectivity`. Leo currently has the client library for IMS data,
but no running `cnd`, no CND socket and no matching framework network-event bridge.
That is an integration gap, not yet a proven sole cause of this trial result.

The stock CNE daemon has a dependency closure of 24 binaries/libraries including
private Binder/utils/netutils interfaces. The KaiOS framework plug-in depends on
`libxul.so`, `libmozglue.so` and `libnss3.so`; copying that plug-in into Android
would not provide an Android connectivity service. Its JavaScript helper
observes KaiOS `wifi.enabled` / tethering settings through XPCOM.

Next work:

1. Establish a readable, reversible call-mode preference path on the selected
   subscription; compare cellular-preferred versus Wi-Fi-preferred policies.
2. Audit the CND ABI and host network-event protocol, then adapt the bridge to
   Android's validated Wi-Fi state, addresses, DNS, routes and disconnect events.
3. Observe WLAN/IWLAN IMS registration before exposing a functional VoWiFi switch.
4. Only then test an ordinary call and LTE/Wi-Fi handover separately.

Do not solve this by forcing a registration property, enabling unknown NV bits,
or treating a WFC-enabled flag as successful Wi-Fi calling.

## Reproduce

```sh
bash android81/build-wfc-control.sh
python android81/inspect-wfc-port.py
python android81/trial-wfc.py --run
python android81/verify-ims-runtime.py
```

The trial wrapper targets Nokia `695d` only, checks idle calls, selected SIM 2,
the expected DDS, validated Wi-Fi and a WFC-disabled baseline. It stages an
independent 75-second on-device restore before enabling the one setting, samples
three times and restores it on host completion. Restoration waits for idle calls
and retries after a command failure. It needs the existing rooted Android
namespace helper and QMI runtime; it is a development probe, not a user setting.
Never run the native enable command directly without the restore wrapper.

The local dependency inventory and trial reports stay under ignored `output/`.
No subscriber identities, network credentials or proprietary blobs are committed.

Wire layouts were checked against upstream libqmi revision
`3454c2c619d516fd46a08fe16c6eebdee061857d`:

- [IMS Bind / service settings](https://github.com/linux-mobile-broadband/libqmi/blob/3454c2c619d516fd46a08fe16c6eebdee061857d/data/qmi-service-ims.json), SHA-256 `bed137b7ea75de70e93cae6a42e58a52c1f49dbcc0df70e5f234cd754dd3f115`.
- [IMSA Bind / registration and voice technology](https://github.com/linux-mobile-broadband/libqmi/blob/3454c2c619d516fd46a08fe16c6eebdee061857d/data/qmi-service-imsa.json), SHA-256 `53788452b34f31372664973ccb83a65f475e700f79db827b6cf4d1cca4f7f485`.

Orange describes [Wi-Fi calling eligibility and activation](https://www.orange.ro/servicii/apel-wifi/).
That is operator guidance; it does not certify this experimental Android port.
The published ROM ZIP is unchanged.
