# IMS / VoLTE investigation — 18 October 2026

Follow-up: the Android 8.1 client port now compiles and has registered real IMS
voice on the primary SIM in a controlled device trial. The unmodified-client
failures below are historical. See IMS-PORT.md for changes, proof and limitations.

Initial investigation state: LTE data validated; IMS voice was **not registered
or integrated**. The controlled port trial is documented separately above.

## Direct modem observations

Both running RILs already expose their legacy IMS socket under
`/dev/socket/qmux_radio/rild_ims{0,1}`. `ims-socket-probe.c` sends only two
read-only protobuf requests: registration state (1) and service status (29).
It bounds frame sizes/timeouts and does not print phone numbers, call lists,
opaque strings or authentication data. Build with `build-ims-probe.sh`.

Slot 0 returned successful responses, registration state 2 (NOT_REGISTERED),
error code 0 and radio technology 14 (LTE). Initially the service list contained
only call type 11 (UT); it did not establish voice registration. Slot 1 returned
generic failure (2) for both queries; this does not prove a hardware limitation.

## Bounded native daemon trials

The existing private radio libraries already include libidl and libwpa_client.
Both original stock daemons were staged in `/tmp/leo-ims` and tested with
`ims-daemon-trial.c`. The root parent creates the expected init-style inherited
control socket, refuses to replace an existing socket, and runs the daemon as
UID 1000 with the relevant supplemental groups. It terminates/reaps the child
after 20 seconds and removes the temporary socket. The original radio IPC shim
and private libraries are process-local; no daemon was installed for boot.

Both imsqmidaemon and imsdatadaemon survived the 20-second window and published
their readiness properties. The properties were cleared after stopping the
trials so they do not incorrectly advertise running services.

The subsequent service query listed voice (call type 0), SMS (10) and video
types with access status 0 (DISABLED), while UT (11) had status 2 (ENABLED).
Registration remained NOT_REGISTERED. Native daemon startup is therefore
demonstrated; it is not a successful VoLTE activation or call test.

## Android client compatibility study

The missing component is an Android IMS service that implements the Android
telephony interfaces and talks the validated legacy socket protocol. A legacy
Qualcomm client source snapshot has the exact `qmux_radio/rild_ims0` transport,
but also depends on vendor framework classes such as ModemStackController,
which the current AOSP tree does not contain. It cannot simply be installed
unchanged as a proven compatible service.

`fetch-ims-reference.py` fetches 71 reference files at pinned revision
`f242858a8cbb2c9ad9b5b2e53d75380ae775727a` into ignored output and records hashes.
`build-ims-candidate.sh` stages a separately named compatibility module and only
builds its intermediate APK. It does not install the service on the phone or
add it to PRODUCT_PACKAGES. Downloaded vendor sources/binaries are not committed.

The compatibility build failed against the real Android 8.1 interfaces. Besides
ModemStackController, it uses removed PhoneBase/PhoneProxy classes, obsolete
VideoProfile/CameraCapabilities APIs and vendor CallForwardInfo extensions.
Its Binder implementations lack newer registration, multi-endpoint and RTT
methods. These are source/interface porting requirements, not missing permissions
that can be repaired by installing an arbitrary APK. The failed candidate was
not installed; its Android.mk is disabled after the compatibility build so it
does not participate in normal image builds.

After the trials, LTE verification still passed (validated connection, two of
two packets, both SIMs READY/voice registered, system read-only). No persistent
IMS daemon activation or carrier feature override was installed.

Protocol/source reference:
[Qualcomm legacy IMS client snapshot](https://github.com/bcyj/android_tools_leeco_msm8996/tree/f242858a8cbb2c9ad9b5b2e53d75380ae775727a/telephony-apps/ims).

Next integration requires resolving Android 8.1 interface differences, then
testing IMS registration and call audio. Wi-Fi remains a separate prerequisite
for VoWiFi. No call, SMS, IMS enable/disable request or modem NV write was made
by these diagnostic probes. The native daemon trials may initialize modem IMS
services; they do not guarantee carrier provisioning or register IMS voice.
