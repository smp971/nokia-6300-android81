# Audio bring-up — 18 October 2026

Current result: **speaker output works through Android's AudioFlinger and the
stock Qualcomm HAL, including after a full reboot.** The user confirmed both
the private HAL tones and Android AudioTrack tones, and confirmed them again
after the reboot. Microphone, earpiece, headset and voice-call audio remain
untested. The initial raw tinyplay test was silent despite returning success.

The following discovery sections describe the initial trials; the installed
integration, boot guard and final validation are detailed below.

## Hardware/kernel discovery

Initially /proc/asound/cards reported no soundcards and /dev/snd contained only
timer. Stock init.target.rc lines 154–167 load 12 audio modules and write 1 to
/sys/kernel/boot_adsp/boot after loading audio_adsp_loader. Every stock module
reports vermagic 4.9.240-perf-g4f9e270fd77f, matching the running stock kernel.

The first rapid sequence lost ADB while audio_machine_int_msm8909 was loading;
Windows enumerated Qualcomm 05c6:900e. There was no kernel tail captured, so the
precise cause is unknown. Do not present a timing/race hypothesis as established.
After software reset, the core 11 modules were loaded with 0.5-second gaps and
per-stage snapshots. The machine module was then loaded separately under a live
host-side dmesg capture; it returned success and registered msm8909-snd-card.
This staged retry is successful evidence, not proof of reliable cold-boot order.

The kernel exposes card0, MultiMedia1/2 and other playback/capture nodes and
2803 mixer controls. Some regulator/route warnings remain in the kernel log.
The card name is msm8909sndcard. The machine module internally uses the
msm8952_asoc_machine_probe symbol despite its msm8909 filename.

## Direct speaker probe

Stock tinymix/tinyplay run from /tmp using the Android libraries. The stock
mixer_paths.xml speaker route uses RX3 MIX1 INP1=RX1 and SPK=Switch; the
MultiMedia1 frontend is connected through PRI_MI2S_RX Audio Mixer MultiMedia1.
A two-second stereo 48 kHz/16-bit WAV contained two 700 Hz half-second tones
with short fade ramps and digital amplitude 0.10. RX3 Digital Volume was reduced
from 84 to 76 for the test. tinyplay reported successful playback. The touched
route and volume controls were restored in finally. The user reported no audible sound from this initial raw PCM probe, despite
tinyplay's successful exit status. No microphone samples saved.

## Private userspace HAL probe

Added leo_audio_probe opens an explicit path through dlopen, reads HMI, opens
AUDIO_HARDWARE_INTERFACE and calls init_check before closing the device.
Its default open-only mode does not replace AudioFlinger's driver or open
streams. The added --play mode validates a generated PCM16 stereo 48 kHz WAV
and writes it through a speaker output stream before standby/close.
Original audio.primary.msm8909.so and audio/calibration dependencies are staged
under /tmp/leo-audio-hal, with the existing isolated radio libraries supplying
libperipheral_client and its dependencies. System libc/libbinder are retained.

The first open failed with -22 because /system/etc/mixer_paths.xml was missing.
prepare-stock-audio-config.py prepared and installed 82 stock mixer/platform/
ACDB files, refusing to replace different existing files and verifying hashes.
These are additive configuration files; /system was remounted read-only and
Android's audio policy and selected HAL were not changed.

After installing this data, the private probe returned:

    module=QCOM Audio HAL api=0x1
    open=0
    device_api=0x200 init_check=0

There are still missing-calibration warnings from the earlier raw PCM probe;
opening the HAL is not a completed media/call/microphone integration test.

## Crash-mode software reset (verified on this phone)

reset-900e.py checks the explicitly requested port (default COM32) is USB
05c6:900e, sends only Sahara handshake/reset packets and then checks that the
Nokia's ADB serial 695d actually returns. It never uploads a programmer or sends
flash/NV write commands. During the successful trial, the handshake returned an
end-transfer error (0x1b); the subsequent reset dropped the serial transport,
900E disappeared and serial 695d reappeared as Leo init diagnostic. Therefore the
reboot is established by USB/ADB re-enumeration, not a claimed reset response.

Protocol reference: [linux-msm/qdl Sahara reset implementation](https://github.com/linux-msm/qdl/blob/master/src/sahara.c).
The original kernel module set disappeared after reset as expected. No recovery
or boot partition was rewritten. The battery-removal request became unnecessary.

## Reproduction and next work

- build-audio-probe.sh builds only the standalone private HAL probe.
- probe-stock-audio-modules.py defaults to matching-kernel preflight without
  loading modules. --load-core performs the 11-module diagnostic in RAM.
  --load-machine additionally reaches the formerly crashing stage; do not run
  it unattended or mistake this test for a production boot service.
- prepare-stock-audio-config.py defaults to prepare-only; --install adds the
  verified stock mixer/calibration data, preserving existing different files.
- reset-900e.py --port COM32 is the verified software recovery command for this
  observed crash-mode device. It refuses ports not matching the crash USB ID.
- Speaker playback and guarded boot integration are now completed below.
  Microphone routing and voice-call audio need separate tests.

Raw traces, binaries, calibration data and dumps remain ignored/private in output
or the original dump. Only source and this sanitized status are committed.

## Installed Android integration and final verification

- audio-bridge.c replaces only audio.primary.default.so, preserving the verified
  original generic HAL under /vendor/leo-audio/lib/audio-primary-generic.so.
  The bridge opens the Qualcomm module only when persist.leo.audio.enabled=1
  and the exact msm8909sndcard is present. It preloads the stock dependencies
  from its private library directory; Android system libraries remain in use.
  Missing hardware, dependency/open failure or a disabled flag selects generic
  fallback. LEO_AUDIO_QCOM_ACTIVE identifies a successful stock module open.
- install-audio-runtime.py installed 27 hash-verified files: bridge, preserved
  generic HAL, private stock libraries, 12 matching modules, runtime script and
  init RC. The 82 mixer/ACDB files from prepare-stock-audio-config.py remain
  additive. No system audio policy replacement, boot/recovery rewrite or data
  format was performed. Native audio services are stopped before replacing
  the active library so /system can be remounted read-only without a busy inode.
- The root oneshot leo_audio_setup waits for boot completion and modem ONLINE.
  It loads the stock modules with one-second gaps, additional ADSP/codec settling
  delays, and verifies card0 before switching native audio. /dev/msm_audio_cal
  permissions are restored to the stock 0660 system:audio setting; this late
  module-created device was previously root-only. /dev/snd permissions are
  restored too. /dev/ion permissions and the graphics stack are not changed.
- Before kernel module loading, the setup clears persist.leo.audio.autoload.
  If the formerly crashing load fails before successful setup, a subsequent
  boot leaves audio in generic fallback until manually rearmed. The validated
  successful setup rearms ordinary later boots. This limits the observed early
  module-load boot-loop risk; it is not a guarantee against every later crash.
- A temporary APK generated two quiet 700 Hz tones using Android AudioTrack,
  STREAM_MUSIC, stereo PCM16 at 48 kHz. Both before and after the full reboot,
  AudioTrack reported 192000 shorts written and 96000 playback frames. The user
  physically confirmed the tones both times. This tests the application →
  AudioFlinger → HIDL audio service → stock HAL → DSP/codec → speaker path.
- After the full reboot, card0 appeared automatically, leo_audio_setup exited 0,
  state was active, both native audio services were running and the stock HAL
  was mapped without the generic fallback. Setup finished around uptime 86 s
  in this trial, after the existing NetworkPolicy startup wait and modem boot.
  Sound is therefore available late in startup, not immediately at power-on.
- verify-audio-runtime.py --accept passed and armed normal subsequent boots.
  Adreno verification and dual-SIM voice registration verification also passed.
  The test APK was uninstalled and focus returned to Leo Shell. /system is RO.

Optional ANC/MAD calibration and some DAPM-route warnings remain. One AFE
ADSP_EBADPARAM was logged in the post-boot trial, despite confirmed audible
speaker playback. Do not treat this as validated microphone/call processing,
optimal calibration, universal media compatibility or long-term stability.
The existing NetworkPolicy startup ANR is separate and remains unfixed.

### Build, install and rollback

Build build-audio-probe.sh (private probe and bridge) and
build-audio-track-test.sh (temporary Android playback APK). First prepare/install
stock mixer data with prepare-stock-audio-config.py --install. Then use
install-audio-runtime.py --install --activate on the pinned matching target.
The installer refuses an unexpected original HAL, kernel or hardware ID. It
keeps original binaries/calibration data and detailed hash manifests private.

verify-audio-runtime.py --wait checks service/card/driver readiness; --accept
sets the validation flag and rearms autoload only after the checks pass. Its
checks are software-only: audible confirmation is recorded separately here.

install-audio-runtime.py --rollback verifies and restores the original generic
HAL, clears enable/autoload/validation flags, restarts only native audio and
keeps the private files inactive for later investigation. It does not unload
live kernel modules; a reboot clears those. Boot/recovery are untouched.
