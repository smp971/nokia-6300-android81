# Audio bring-up — 18 October 2026

Current state: the stock kernel sound card and PCM path are available in a
**temporary runtime trial**. The stock Qualcomm userspace HAL opens privately
and passes init_check. Android AudioFlinger still uses its original dummy HAL.
Audible output has not yet been confirmed by the user. No microphone recording,
phone-call audio test or permanent automatic module loading was performed.

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
route and volume controls were restored in finally. Physical audibility is still
pending, not inferred from tinyplay's exit status. No microphone samples saved.

## Private userspace HAL probe

Added leo_audio_probe opens an explicit path through dlopen, reads HMI, opens
AUDIO_HARDWARE_INTERFACE and calls init_check before closing the device.
It does not replace AudioFlinger's driver or open playback/capture streams.
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
- Before integrating into Android, obtain physical speaker confirmation, validate
  actual HAL playback and microphone routing, then add supervised module/init
  loading with a recoverable fallback and test a full boot.

Raw traces, binaries, calibration data and dumps remain ignored/private in output
or the original dump. Only source and this sanitized status are committed.
