# SIM 2 LTE / IMS investigation — 24 October 2026

**A supervised incoming VoLTE call on SIM 2 passed with two-way audio and
simultaneous LTE data.** The selected-SIM client remains a controlled prototype;
automatic startup, lifecycle recovery, outgoing IMS calls and handover are not
yet validated.

## Observations

- Active records: physical slot 0 / sub 1 (Vodafone), slot 1 / sub 3 (Orange).
  Subscription IDs are runtime records, not slot numbers.
- The guarded engineering activity selected sub 3 for data. Both data-enable
  flags were already true. SIM 2 initially reported modem mode 0; it accepted
  mode 9 (LTE/GSM/WCDMA automatic), persisted as preferred_network_mode3.
- The Orange APN list was empty. Added a separate Leo-Orange-Internet row:
  APN `net`, type `default`, IPv4. Existing rows were retained. The insertion
  checks the active SIM's physical slot and MCC/MNC, and is idempotent.
  Reference: [Orange Internet settings](https://www.orange.ro/help/setari-internet-pe-mobil-144).
- MOBILE[EDGE] then became CONNECTED and VALIDATED. Physical phone 1 reported
  data state 2. Wi-Fi remained enabled; the separate validated MOBILE agent
  provides the mobile-data evidence.
- Temporarily setting SIM 1 to mode 0 did not move SIM 2 to LTE. SIM 1 mode 9
  was restored. Both SIMs were READY, idle and voice registered afterwards.
- Cached Android radio-family values were 81928 for both slots. These do not
  prove live modem allocation: AOSP 8.1 synthesizes a capability when the vendor
  request fails or is unsupported.
- The read-only radio-nas-probe.c confirmed modem default data subscription 1
  (physical second subscription). Both client-bound subscription queries gave
  mode mask 0x1c (GSM/UMTS/LTE). It binds only its own NAS client and reads
  preferences. It does not change global data selection or radio preferences.
- The user confirmed the same Orange SIM has/had working 4G outside this port.
  It initially remained on EDGE; after one full reboot with the corrected
  settings, physical phone 1 registered LTE for voice/data and Internet validated.

## Registration and real incoming call

With SIM 2 selected for data and genuinely registered LTE, staged stock QMI/data
daemons under the controlled wrapper. A temporary availability override allowed
Android's gate; it did not synthesize modem registration. The modem reported
state 3 (registering), then state 1 (registered), error 0, LTE RAT 14. The client
reported registration=1 and voice_available=true.

The first registration-only trial was stopped and restored. Restarting the
client exposed a lifecycle issue: Android retained its old IMS phone after the
force-stop, so the new client could register while phone=-1. Sending selected
phone IMS_SERVICE_DOWN then IMS_SERVICE_UP to com.android.phone recreated the
framework binding; phone=1 and registrationConnectedWithRadioTech(14) followed.
This recovery needs integration before automatic activation.

The user then called the Orange SIM from their other phone, answered and ended
from Nokia's keys, and explicitly confirmed audio in both directions. The
monitor saw physical phone 1 transition IDLE → RINGING → OFFHOOK → IDLE while
data state stayed CONNECTED (2) and data RAT LTE (14). The IMS transport/session
log recorded the incoming session. A ping bound explicitly to rmnet_data0
received 2/2 replies during the call observation; Wi-Fi stayed enabled but was
not the interface used by that probe.

| Monitor sample | SIM 2 call | SIM 2 data | Data RAT |
| --- | --- | --- | --- |
| 0–1 | IDLE | CONNECTED | LTE |
| 2 | RINGING | CONNECTED | LTE |
| 3–5 | OFFHOOK | CONNECTED | LTE |
| 6 onwards | IDLE | CONNECTED | LTE |

The supervised-call daemon window was explicitly 900 seconds; the default
wrapper duration remains 20 seconds. The trial was manually stopped well before
expiry, after checking both calls idle. The availability override, socket mode
and selected-slot property were restored, the selected framework IMS phone was
taken down, the client force-stopped and only the staged daemons terminated.
Final verification: both SIMs idle, SIM 2 MOBILE[LTE] CONNECTED and VALIDATED.
SIM 2 remains selected for mobile data. No automatic IMS startup was installed.

## Selected-SIM client

port-ims-client.py uses sys.leo.ims.slot (0 or 1, default 0) for the process
lifetime. The receiver selects its socket before starting. Listener, open,
configuration and IMS control guards use the selected physical slot.
Reconfiguration refuses a changed slot. This remains a single selected IMS
subscription; simultaneous dual IMS and automatic stack remapping are absent.

Fresh AOSP 8.1 build succeeded. Version 1.1-selected-slot APK SHA-256:
`97a01d697894ca9f8a63f2e2b259bc3f4adfa3ee498818e1373aa634d59eefac`.
The isolated bind-only trial connected to qmux_radio/rild_ims1 and reported:

```
LEO_IMS slot=1 phone=1 registration=2 voice_available=false
```

The phone process stayed alive. No availability override or native IMS daemon
was started. Afterwards the APK was force-stopped, the socket restored to 0700
and the previously empty slot property restored. No IMS boot activation exists.

## Engineering tools and next gate

Build radio-test.apk with build-radio-test.sh and grant READ_PHONE_STATE.
The exported diagnostic activity requires MODIFY_PHONE_STATE. Use am start -S
to run onCreate again; merely bringing the existing activity forward does not
repeat an action. The sub argument must identify the verified active record.
selectData=true with dataSlot=1 selects physical SIM 2; installOrangeApn=true
inserts the guarded Orange Internet row. Query-only runs omit mutation extras.
Returning to the original data SIM uses sub 1 and dataSlot=0 on this device.

SimDataTrial instrumentation is read-only. An earlier write attempt failed on
TelephonyProvider APN permissions; data selection therefore uses the separately
platform-signed engineering activity. Android 8.1 getDataState can describe the
default phone, so per-slot verification uses telephony.registry instead.

Next: integrate selected-slot startup/teardown and stale-framework-binding
recovery, then validate an outgoing IMS call and startup persistence. Incoming
call audio and simultaneous LTE data passed this one supervised test; fallback,
handover, emergency calling and long-term reliability remain unverified.
stop-ims-trial.py currently restores slot 0 only; do not use it for slot 1.
