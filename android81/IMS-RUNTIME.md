# Selected-SIM IMS runtime — 24 October 2026

**Installed and verified:** automatic IMS voice registration on Orange SIM 2
after a complete reboot, with validated LTE Internet. Disable/re-enable and
client-process recovery also passed. This integrates the earlier supervised
[incoming VoLTE call with two-way audio and simultaneous data](IMS-SIM2.md).
No additional call was placed during these runtime checks.

## Startup and selection

The platform-signed IMS package is installed as a system app. Init starts its
lightweight watchdog after Android finishes booting and the radio setup is
ready, gated by `persist.leo.ims.enabled=1`. The watchdog starts the controller
if its separate process is missing; it does not restart the framework or RIL.

`LeoImsController` resolves the active default-data subscription to physical
slot 0 or 1. It waits for real LTE on that subscription before its first start.
The original QMI/data daemons run as init-managed system-UID services with the
same private libraries, IPC shim, supplementary groups and inherited control
sockets used by the successful trial. They have no trial-duration expiry.

Only the selected RIL IMS socket is made accessible to the shared radio UID.
The native socket watcher reapplies its mode if a RIL recreates the socket.
Before the first change in a boot, the original mode is saved under /dev.
Native teardown restores saved modes. The other SIM socket stays untouched.

The controller uses one physical slot at a time. It checks Telecom call state
before switching, stopping, starting or reconfiguring the client. A requested
switch or shutdown waits while a call exists. It first requests deregistration,
then waits for the modem's NOT_REGISTERED result before stopping the selected
client and native services. There is no forced timeout that tears down a call.
Airplane mode also stops IMS through this path.

The engineering availability override enables AOSP's device gate while this
runtime is active. Its previous value is saved and restored on disable. It
does not fabricate provisioning, registration or voice availability. User
Enhanced 4G preferences still pass through ImsManager's ordinary configuration.

## Lifecycle recovery

The client runs in :leo_ims and the controller in :leo_controller. On creation,
the client sends a selected-phone IMS_SERVICE_DOWN before publishing its new
Binder and IMS_SERVICE_UP. This disposes the stale Android IMS phone seen when
restarting the earlier prototype. Listeners query actual registration again.

During controlled destruction, the client announces DOWN and exits only its
own dedicated process. This clears legacy transport/session singletons before
a physical-slot change. The controller checks for a dead Binder and restarts
the client when calls are idle. A recreated controller adopts the existing
slot/client rather than tearing down a surviving call. Native daemon process
restart is handled by init; socket reconnection remains in the legacy client.

Registration and voice status properties are written from actual modem
callbacks. The verifier additionally requires live processes and a matching
service dump, rather than trusting possibly stale properties alone.

## Rebuild and install

Use the existing AOSP 8.1 environment and pinned IMS reference:

```
fetch-ims-reference.py
build-ims-candidate.sh
install-ims-runtime.py --install
```

The build does not itself activate IMS or add the study module to normal image
builds. The installer stages seven files, checks hashes, backs up existing
files and the previous package/properties under ignored output, installs the
APK and init extension, restores /system read-only and enables the runtime.
It refuses an active call or a still-running older IMS trial/runtime. A reboot
is needed for newly added init service definitions. Boot/recovery partitions
and modem calibration are unchanged.

Before a later update, set persist.leo.ims.enabled to 0 and wait for state
disabled with native services stopped. After installation, re-enable with 1.
Use framework-command.py when running Android framework commands from the
engineering ADB namespace.

APK version 1.2-runtime / code 3, SHA-256:
`3b1edeffdfbe049411127e0a44a9f6f410b2e62ff6364f35b9161104c9954576`.

## Validation

| Check | Observed result |
| --- | --- |
| Complete reboot | Controller and native services started automatically; selected physical slot 1 |
| Actual voice registration | registration=1, voice_available=true, client phone=1 |
| Mobile Internet | Physical SIM 2 data CONNECTED on LTE; MOBILE[LTE] VALIDATED |
| Disable | Registration became 2; client and native daemons stopped; selected socket restored to 0700; previous empty override restored |
| Re-enable | IMS voice registered again; LTE data remained validated |
| Unexpected client death, calls idle | A new IMS client process registered automatically; controller, com.android.phone and Leo process IDs stayed unchanged |

Run `verify-ims-runtime.py --wait` for a bounded registration/data readiness
check. It reports only service states and physical slots, and stores a sanitized
local report under ignored output. It does not place calls or change settings.

This session did not repeat call audio, outgoing IMS calling, SIM 1 automatic
selection, switching data SIM during a live call, emergency calling, handover,
VoWiFi or long-term reliability tests. The implementation defers changes during
calls, but that branch has not been exercised with an actual live-call switch.
Those limits are separate from the confirmed SIM 2 incoming VoLTE call.

The historical stop-ims-trial.py is for its old slot-0 temporary prototype.
Disable this runtime through its enabled property instead.
