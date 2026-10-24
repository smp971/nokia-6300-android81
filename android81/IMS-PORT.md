# Android 8.1 legacy IMS port — 18 October 2026

Update, 24 October: [SIM 2 investigation](IMS-SIM2.md) adds an explicitly
selected physical-slot client. A supervised SIM 2 incoming VoLTE call passed
with two-way audio and simultaneous LTE data. [Automatic activation and client
recovery are now integrated and verified](IMS-RUNTIME.md). The primary-SIM
registration below is historical.

## Historical primary-SIM trial, 18 October

**Result: the port compiles, runs on the phone, binds to Android telephony and
successfully registers IMS voice on the primary Vodafone SIM. No call was made;
call audio, incoming calls, emergency calling and handover remain unverified.**

This is a controlled prototype, not an automatically enabled daily-use service.
The signed APK remains installed but force-stopped; the temporary availability
override and socket permissions were restored after the trial. No IMS boot
activation was installed. LTE data and both SIM registrations passed afterwards.

## Implementation

`port-ims-client.py` checks the hashes and revision of the pinned reference
snapshot, then applies the source port in the dedicated local study tree.
Downloaded vendor code and compiled APKs remain ignored; this repository stores
the port recipe and original diagnostic tools.

- Android 8.1 registration listeners, Binder methods and callback forwarding.
- Subscription-aware SRVCC events through PhoneStateListener, replacing the
  removed PhoneBase/PhoneProxy and vendor ModemStackController dependencies.
- A separate `org.codeaurora.ims:leo_ims` process, with phone-control permission
  required on the exported service and Binder operations.
- Explicit main-thread Loopers for handlers/listeners created through Binder.
- Correct `ImsManager.EXTRA_PHONE_ID` routing and primary-slot guards. The
  secondary SIM cannot change the primary IMS configuration through this port.
- Voice-only capabilities: no video JNI initialization/provider, no RTT or
  multi-endpoint support. Unsupported provisioning setters fail; provisioning
  is not falsely reported as successful.
- Feature reads query actual modem service status asynchronously. `isConnected`
  requires real registration and an enabled LTE voice service; `isOpened` and
  close use actual session records instead of unconditional legacy success.

The older source's optional-null config-listener logging can still emit a
misleading error when AOSP requests a feature change without a callback. The
modem acknowledgments and subsequent registration were independently observed.
The obsolete vendor timer metadata stays internal; Android's ordinary parcels
do not expose those vendor-only timer fields.

## Build and observed test

Run `fetch-ims-reference.py` if the pinned reference is not present, then run
`build-ims-candidate.sh` in WSL. It builds the signed intermediate APK and copies
it to `output/ims-reference/ported-ims.apk`, without adding it to system images.
The study Android.mk is disabled afterwards. Final trial APK SHA-256:
`5088474506094ba74117873b6011d67afd83bff383de54da6bea98eef13b185c`.

The first device start exposed a missing Looper in a Binder-created listener and
restarted com.android.phone. The client was stopped and rebuilt with explicit
main Loopers, including session/UT handlers. The corrected version then ran in
its own process, connected to `qmux_radio/rild_ims0`, and Android opened service
ID 1. Readiness initially reported registration 2 / voice unavailable.

The stock QMI/data daemons were started under bounded trial wrappers (180/160 s).
The original root-owned IMS socket mode was saved, temporarily changed from
0700 to 0660 for the shared radio UID, then restored. A temporary
`persist.dbg.volte_avail_ovr=1` allowed AOSP's availability gate while the client
sent the real voice-enable and registration requests. No provisioning status
was fabricated, no phone number was dialed and no SMS was sent.

The modem acknowledged the requests, then reported:

1. `registration=3` (registering), error 0, LTE radio technology 14.
2. `registration=1` (registered), error 0, LTE radio technology 14.
3. The client dump showed `registration=1 voice_available=true`.
4. Android's primary phone dump showed `mImsRegistered=true`; the secondary
   IMS phone remained unregistered.

The trial override was restored, the client reconfigured and registration 2 /
voice unavailable observed before stopping it. `stop-ims-trial.py` stops only
the staged daemons, checks idle call state, restores saved settings and leaves
the installed APK inactive. Normal packet data needed native RIL reinitialization
after this test sequence; both RILs were restarted. Final verification passed:
MOBILE[LTE] validated, two external packets received, both SIMs READY, both voice
slots registered and system read-only.

## Remaining integration

Before enabling this automatically: integrate controlled native-service startup,
socket ownership/permissions and carrier/device availability configuration;
verify lifecycle and LTE data coexistence; then test ordinary outgoing/incoming
calls and audio. RTP/media setup and call audio have not been established by
the registration test. Emergency calling, CS fallback and handover require
separate validation. VoWiFi additionally needs working Wi-Fi and corresponding
IMS support; it is not advertised by this voice-over-LTE prototype.
