# Radio bring-up: work in progress

Target: Nokia 6300 TA-1286, serial `695d`, internal Android 8.1.

The installed AOSP reference RIL does not publish a working radio interface.
The kernel initially reported modem OFFLINING. The original modem firmware remains on
mmcblk0p25; its partition identity was checked through sysfs. A temporary
read-only mount at `/tmp/leo-firmware` exposes `image/modem.mdt` and `mba.mbn`.
No modem, NV or persist partition was written by these diagnostics.

## Reproduce the library compatibility probe

1. Run `python android81/audit-radio.py` on Windows. This inventories stock
   ELF32 dependencies and captures read-only diagnostics for serial 695d.
2. In WSL kaios, run `bash android81/build-radio-probe.sh` using the absolute
   project path. It compiles a small Android dlopen probe with the local AOSP
   toolchain. It never calls RIL_Init.
3. Build AOSP `libxml2` for `aosp_leo_go-eng` if testing that compatibility
   replacement, then run `python android81/stage-radio-probe.py` on Windows.
   This stages private copies in `/tmp/leo-radio-probe`, not `/system`.

Reports and binaries stay under ignored `android81/output`. Stock proprietary
libraries are read from the local `analysis/system` extraction and are not
committed. Existing AOSP system/vendor libraries take precedence over stock
copies when staging.

## Results through 17 October 2026

- Qualcomm libraries including the full stock RIL load against AOSP after
  replacing the private stock libxml2 with a newly built AOSP libxml2 (ICU 58).
- Backed up live modemst1, modemst2, fsc, fsg and persist, verified with device
  MD5 and local SHA256. These backups are excluded from Git.
- Mounted firmware read-only at `/firmware`, the path searched by ueventd.
  A bounded open of `/dev/subsys_modem` brought the modem ONLINE.
- The stock RIL v12 can register AOSP radio 1.0/1.1 and OEM hook interfaces.
- The modem exposes voice, UIM and NAS services through IPC router instance 0.
  Instance 128 (legacy QMUX) does not resolve. The modem does not expose the
  DATA5_CNTL SMD channel awaited by qmuxd in these trials.
- `patch-radio-ipc.py` creates a hash-guarded experimental private libqmi_cci
  adaptation disabling legacy QMUX registration; `radio-ipc-shim.c` maps the
  observed legacy instance 128 to IPC instance 0 in RIL only.
- Running the stock `irsc_util` with stock `sec_config` resolves the driver's
  `wait_for_irsc_completion` block. No security rules were removed or invented.
- With that combination, Android reads the baseband version and receives
  CARDSTATE_PRESENT / APPTYPE_USIM. SIM state remains NOT_READY, with no network.

The hardware reports hwid 0x0 (stock dual-SIM configuration). A private AOSP
SIM_COUNT=2 trial compiled successfully but crashed handling an OEM unsolicited
message: the stock vendor's callback ABI does not supply the extra socket_id.
The SIM_COUNT=2 hypothesis was rejected and the BoardConfig change reverted.
Stock init instead uses separate rild processes (`-c 2` for the second one).
Keep the installed single-SIM-ABI AOSP libril; do not stage the rejected private
multi-SIM libril.so in the library search path.

With the original AOSP RIL ABI, the IPC adaptation and
`persist.radio.multisim.config=dsds`, Android reported SIM READY. Voice/data
registration remained POWER_OFF / Unknown, with radio-power requests pending.
The second rild trial could not start because USB/ADB serial 695d disappeared.
Its cause (USB disconnect, reboot or fault) has not yet been established.

`radio-start-probe.sh --ipc` starts bounded temporary trials in PID 1's mount
namespace. Stage the binaries and libraries first; run backup-radio-state.py
before the first modem activation. It requires the privately generated IPC
library and shim under `/tmp/leo-radio-ipc`. It deliberately uses the installed
single-SIM-ABI rild and refuses an experimental libril.so in that directory.
Do not run overlapping trials. The dsds property persists in userdata; service
and library staging remains temporary. Legacy qmuxd is skipped in IPC mode.

No boot/system/recovery image was flashed for these radio trials. The temporary
mounts and `/tmp` binaries do not survive a reboot. Calls, SMS, network
registration and mobile data are not validated; no test calls or SMS were sent.

## Radio-power queue diagnosis

A repeated trial again reached SIM READY / modem ONLINE. Toggling airplane
mode through Android (ending with airplane_mode_on=0) confirmed the framework's
mDesiredPowerState=true and a new RADIO_POWER on=true request arriving in RIL.
Vendor logging then reported `defer exec FC cmd 23`: an older RADIO_POWER
request still owns that flow-control queue. This establishes where the new
request is held, not why the older request is stuck. One older power-off request
eventually completed; a general radio-power fix has not been established.

ADB disappeared again before the worker stack could be captured. No second
rild was launched and no radio-power compatibility patch was installed.
`diagnose-radio-power.py` captures the queue logs and native worker stacks of
the staged RIL once serial 695d is available. Its output stays excluded from Git
because radio diagnostics may contain SIM and subscriber details.

After USB reconnection, native stacks captured the pending power worker in
qcril_qmi_nas_request_power -> internal helpers ->
qcril_qmi_nas_radio_power_process_condition_wait_helper -> pthread_cond_timedwait.
Starting the stock-style second rild (`-c 2`) and restarting com.android.phone
initialized two Android phone slots but did not establish working registration.
The user confirmed the phone stayed powered on across USB disconnects.

`radio-dms-probe.c` queries DMS directly, without publishing a fake Android
radio state. It observed operating mode 1 (LOW_POWER). An explicit --online
request returned a successful QMI result; a later query confirmed mode 0
(ONLINE), while Android still reported POWER_OFF. This narrows the remaining
fault to state/event integration; it does not establish a working network.

The DMS message identifiers and TLVs follow the upstream
[libqmi DMS definitions](https://github.com/linux-mobile-broadband/libqmi/blob/main/data/qmi-service-dms.json);
QCCI function signatures are described by
[Qualcomm's QMI framework](https://github.com/qualcomm/qmi-framework/blob/main/include/qmi_cci.h).
The probe prints operating-mode/result fields only, never SIM identifiers.

`radio-collect-trial.sh` prepares an on-device capture independent of the ADB
connection. Stage it together with the compiled DMS probe and existing radio
trial prerequisites, then launch it detached with stdin/stdout/stderr redirected.
It must run in PID 1's mount namespace, without overlapping previous trials.
Evidence under `/tmp/leo-radio-evidence` is private and disappears on reboot.
The private bundle is prepared with `python android81/stage-radio-collection.py`.
Add `--start` to transfer it in one ADB push and launch the detached collector.
The launcher verified RUNNING on the phone; completion/evidence retrieval still
needs validation. ADB disconnected again after launch, so no result is assumed.

The Windows USB device was at one point bound to generic winusb.inf rather
than the Android ADB interface class. Restarting the ADB server with the
process-local ADB_LIBUSB=1 setting made it enumerate as offline, and a later
reconnection restored a usable transport. This has not established a durable
USB fix. A PnP interface restart was denied by Windows without administrator
rights; no driver was replaced.
