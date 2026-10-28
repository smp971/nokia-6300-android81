# Leo app bundle · 0.14.0-dev

**Clean-ROM runtime compatibility:** current FM sources use
`/system/vendor/leo-fm`; the root broker uses a UID generated at boot under
`/data/system`. Pair these apps with the matching system image. Rebuilding an
APK over the older `/data` FM runtime does not migrate that runtime.
The older app-only bundle described below predates the clean ROM preparation;
[the complete-package guide](FLASH-FROM-STOCK.md) records the later installation.

Current version: `0.14.0-dev`, code 14. In that app-bundle stage, both apps were
built individually; `--package-only` collected the exact checked APKs and recorded
`rebuilt=false`. The [0.14 development report](LEO-14-DAILY-EXPERIENCE.md) remains
in Romanian. Use `--build` after `--check` for an actual rebuild.

This bundle contains two Leo apps. The three adapted Android APKs are built
separately with `build-framework-keypad.sh` and installed through
`install-system-keypad.py --only all`. Boot/system have a separate
[private recovery bundle](RECOVERY-BUNDLE.md).

## Historical context · 0.4–0.5

The app bundle updates **Leo Visual and Leo Keypad** over an existing Leo Android
8.1 port. It does not include boot/recovery, modem, a dump, proprietary firmware
or user data.

0.4.0-dev incorporated the earlier 0.1/0.2/0.3 stages. BF20A1 camera integration
and UI/pointer refinements were later added while retaining code 4. The manifest
commit/hashes identify the exact snapshot; the version label alone does not.
The 23 October snapshot used `--package-only`, with `rebuilt=false`, and passed
offline installer checks. Proprietary camera runtime was installed separately.

0.5.0-dev/code 5 added categorized menus, recents, SMS folders and flash controls.
Both APKs were rebuilt/versioned; functional keyboard code was retained.
Hardware runtimes remain separate from app-only bundles.

## Rebuild

Supply the pinned AOSP tree, Java 8, SDK 27, AOSP host tools and built
`com.android.vcard` library described in [REBUILD](REBUILD.md). The script does
not silently download dependencies or build all of AOSP. From the repository
root in Linux/WSL:

```bash
python3 android81/release-apps.py --check
python3 android81/release-apps.py --build
```

For other locations (replace the example path placeholders):

```bash
python3 android81/release-apps.py --build \
  --aosp-root /cale/aosp --java-home /cale/jdk8 \
  --output /cale/pachete
```

Individual build scripts also accept `LEO_AOSP_ROOT` and `LEO_JAVA_HOME`.
An existing version directory is not overwritten; choose another `--output`
or preserve/move the previous bundle first.

`release-version.json` and both Android manifests must agree on version/name
and numeric code. Increment the code and update all three for a new release.
Keep the existing AOSP development signer for update compatibility.

## Output and verification

`leo-apps-0.14.0-dev/` contains:

- `leo-visual.apk` and `leo-keypad-ime.apk`;
- `manifest.json`: version, target, sizes/hashes, commit, tree state and source/build-input hashes;
- `install-app-bundle.py` and `README.txt`.

The generator also creates `leo-apps-0.14.0-dev.zip` and prints its SHA-256.
From the extracted folder on Windows or Linux:

```bash
python install-app-bundle.py
```

The default command checks only the four manifest files, without ADB.
`--package-only` collects existing APKs and marks `rebuilt=false`; it is not a
verified rebuild. `--build` rebuilds both APKs and records SDK/tool/certificate/
vCard hashes. SHA-256 checks integrity; it is not a release signature.

## Explicit update

```bash
python install-app-bundle.py --install --serial SERIA_TELEFONULUI --adb CALE_ADB
```

`SERIA_TELEFONULUI` and `CALE_ADB` are placeholders for the phone serial and ADB
path. The installer requires an explicit serial, API 27 and both Leo packages.
Default `leo` mode uses `/sbin/leo-framework-exec` and reads Java exports from
`/init.environ.rc`, without local archives or fixed Windows paths. Conventional
Android ADB namespaces use `--mode android`.

Keyboard updates first, then shell. The installer does not change Home/dialer/
IME roles, format data or write OS partitions. It is not a stock-KaiOS migration
or a base-system privilege installer. If the second APK fails, the first can
remain updated; there is no automatic rollback.

## Reproducibility limits

The existing development environment has built the apps. Full clean-host rebuild
and bit-identical APK output are unvalidated, including ZIP metadata effects.
The new app-bundle installer was not executed on the phone during that recorded
stage because ADB was disconnected. Offline tests:

```bash
python -m unittest discover -s android81/tests -p test_app_bundle.py
bash android81/run-host-project-checks.sh
```

The second check requires a shell build first. Device tests for new flows are
built and run separately.

## On-phone updater

Leo 0.4.0 introduced ZIP checks and Android sessions for both APKs, retaining the
previous versions. An end-to-end update/rollback through that app still needs a
phone test; see [the historical Pocket Suite report](LEO-POCKET-SUITE.md)
(Romanian). A first update can still use the explicit ADB command above.
