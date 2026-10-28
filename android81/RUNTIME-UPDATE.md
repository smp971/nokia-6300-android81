# Local Leo runtime updates

`runtime-bundle.py` creates a verifiable ZIP for an **existing internal Leo port**
with kernel `4.9.240-perf-g4f9e270fd77f` and the existing root wrapper. This is
neither an Android-over-KaiOS installer nor a recovery-flashable ZIP.

## Contents and provenance

- Locally rebuilt Leo Visual/Keypad, versioned by `release-version.json`.
- Private radio, audio, camera, IMS and GPS runtimes installed on the phone.
- Explicitly allowlisted Leo init files and audio/GPS configuration.
- Manifest with sizes, SHA-256, commit, source hashes and Git tree state.

Capture reads only allowed system files, excluding userdata, SD, modem/NV,
accounts and keys. Proprietary dump libraries are included; the ZIP is not
committed automatically.

This is a snapshot of the installed configuration with repeatable packaging,
not proof of a clean-host rebuild or bit-identical results. Boot, modified
framework, FM/MTP services, audio calibration and base Leo configuration remain
prerequisites.

## Prepare

Rebuild apps with `build-keypad-ime.sh` and `build-visual-shell.sh` using the
[build guide](REBUILD.md). GNSS uses `build-gps-runtime.sh`; other runtimes have
their own recipes/reports. Retain APK signers for updates.

```text
python android81/runtime-bundle.py OUTPUT.zip --capture --serial SERIAL --adb PATH_TO_ADB
python android81/runtime-bundle.py OUTPUT.zip
python android81/runtime-bundle.py OUTPUT.zip --check-device --serial SERIAL --adb PATH_TO_ADB
```

No-option verification is offline. `--check-device` compares every hash with
the phone without writing. Extra files, bad hashes, duplicate destinations,
non-allowlisted paths and `..` traversal are rejected.

## Install and roll back

```text
python android81/runtime-bundle.py OUTPUT.zip --install --backup NEW_BACKUP_FOLDER --serial SERIAL --adb PATH_TO_ADB
python android81/runtime-bundle.py OUTPUT.zip --rollback NEW_BACKUP_FOLDER --serial SERIAL --adb PATH_TO_ADB
```

The installer requires an explicit serial, checks the platform and absence of
calls, backs originals up to a new PC folder, then transfers/verifies all files
before writing. System files are replaced by rename and system returns read-only.
APK updates retain app data. Home/IME/dialer roles remain unchanged; there is no
format or automatic reboot.

Multi-file installation is not atomic. USB loss or an APK failure can leave a
partial update. The backup folder enables explicit rollback after reconnecting;
automatic recovery is not guaranteed. Retain it until a full boot is checked.

Run offline tests with `python android81/test-runtime-bundle.py`. Installing and
rolling back a complete runtime bundle require a separate test. Hash checks and
host tests are not a full-flash validation.
