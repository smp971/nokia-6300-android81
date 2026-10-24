# Call audio calibration — 2026-10-24

## Report and scope

The user reported poor call audio in both directions. Earlier confirmations of
working calls establish connectivity and routing, not acceptable voice quality.
After explicitly selecting the Leo calibration, the user confirmed acceptable
audio in the requested two-way call comparison: **“merge, e ok”**.

## Findings

- The stock Qualcomm HAL was active, using the `msm8909sndcard` sound card.
- The stock dump includes seven device-specific ACDB files in
  `analysis/system/etc/acdbdata/MTP/Leo/`, alongside generic MTP calibration.
  SHA-256 comparison confirmed that all seven installed files match the dump.
- `ro.boot.project.name` and all `persist.audio.calfile0..19` properties were
  empty in the port. Automatic selection therefore needs scrutiny; we did not
  capture the earlier initialization and cannot prove which files it selected.
- Disassembly of the original `libacdbloader.so`, `acdb_loader_init_v2`, confirms
  explicit file overrides: it reads contiguous `persist.audio.calfile%d`
  properties starting at **0**, stopping at the first empty property (limit 20).
  At least one supplied file bypasses automatic directory discovery.
- Previous call logs contained AFE/AANC calibration query failures (`-19`).
  These alone do not prove the cause of audible distortion or low quality.

## Applied candidate correction

`select-leo-audio-calibration.py --apply` selects the original files explicitly:

| Property suffix | File in `/system/etc/acdbdata/MTP/Leo/` |
| --- | --- |
| 0 | `Leo_Bluetooth_cal.acdb` |
| 1 | `Leo_General_cal.acdb` |
| 2 | `Leo_Global_cal.acdb` |
| 3 | `Leo_Handset_cal.acdb` |
| 4 | `Leo_Hdmi_cal.acdb` |
| 5 | `Leo_Headset_cal.acdb` |
| 6 | `Leo_Speaker_cal.acdb` |

The script saves all 20 previous properties under ignored
`output/audio-calibration/<timestamp>/before.json`, verifies the device, idle
call state, active stock audio and file hashes, then restarts only Android audio
services. It changes no mixer gains, kernel modules, modem configuration or
global device identity. Properties persist across boots; a reboot with this
selection has not yet been tested.

Read-only inspection:

```powershell
python -X utf8 android81/select-leo-audio-calibration.py
```

Apply (interrupts media playback; both SIMs must be idle):

```powershell
python -X utf8 android81/select-leo-audio-calibration.py --apply
```

Rollback using the path printed by the installer (also restarts audio):

```powershell
python -X utf8 android81/select-leo-audio-calibration.py --rollback <path-to-before.json>
```

## Verification and remaining work

At 12:37 the restarted HAL logged the selected Leo files, `ACDB initialized`
and `LEO_AUDIO_QCOM_ACTIVE`. Both `audio-hal-2-0` and `audioserver` were running;
ADB remained connected. Optional topology, ANC, codec-calibration and metadata
queries still logged errors, so this is not an error-free initialization claim.
Missing `.acdbdelta` files are reported by the library as well; no fabricated
delta calibration was added.

The user completed the requested real call comparison, covering reception at
the earpiece and speech heard by the other participant, and reported
“merge, e ok”. The reported two-way quality problem is therefore resolved in
this tested scenario. Keep the selected calibration; no gain adjustment is
needed on this evidence. This confirmation does not establish quality on SIM 2,
other audio routes or every network/call type. Persistence across a reboot has
not yet been physically tested.
