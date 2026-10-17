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
