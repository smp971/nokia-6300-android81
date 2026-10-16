# Radio bring-up: work in progress

Target: Nokia 6300 TA-1286, serial `695d`, internal Android 8.1.

The current AOSP reference RIL does not publish a working radio interface.
The kernel reports modem OFFLINING. The original modem firmware remains on
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

Initial result: Qualcomm libqmi_csi and libqmi load against AOSP. Loading the
full stock RIL fails in stock libxml2 because it expects ICU 55 symbols absent
from Android 8.1. The AOSP XML replacement is being built; radio operation is
not yet validated. Loading libraries does not imply a working modem or calls.
