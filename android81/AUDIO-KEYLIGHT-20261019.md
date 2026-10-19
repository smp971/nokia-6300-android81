# Audio initialization and input feedback — 19 October 2026

## Confirmed on the Nokia

The user confirmed speaker PCM tones, receiver PCM tones, and OGG music through
MediaPlayer. Receiver playback also worked after restarting the HAL with the
saved mixer configuration, without forcing mixer controls from a test command.
After the repaired startup, OGG music was audible after reboot. On a subsequent
real incoming call, the user confirmed the ringtone and conversation in both
directions. Outgoing calls, speakerphone switching, wired accessories and
standalone microphone recording need separate tests.

Keypad light now follows physical key presses and turns off after eight seconds,
or when the LCD backlight turns off. The user confirmed illumination. The helper
observes `matrix_keypad` without grabbing input or recording key codes. It runs
as system/input, writes only button brightness, and sleeps indefinitely outside
its active window. Default brightness is 20/40; `persist.leo.keylight.level` can
select 0–40. It does not add a periodic idle poll.

The missing AOSP vibrator passthrough implementation was built and installed.
`hasVibrator()` changed from false to true, Android's standard 200 ms shell test
reached the driver, and sysfs activation reported an active timer. **The user
felt no vibration**, including three 1.2-second direct tests. This is an unresolved
hardware-driver path, not a functional vibration feature. No raw PMIC register
writes or voltage changes were attempted. The DT requests 3100 mV.

## Causes and changes

* Android's legacy audio policy file was absent. Its fallback exposed Speaker
  only. The new policy exposes Earpiece, Speaker, built-in microphone and wired
  routes; it does not claim validated wired playback or Bluetooth audio.
* The stock handset mixer path omitted `EAR PA Boost`. Enabling it in `handset`
  and disabling it by default restored audible receiver output. The reproducible
  transformation checks the original dump's hash; proprietary XML stays local.
* Late Q6 loading previously logged an order-6 allocation failure in `rtac_init`.
  Early core loading avoids that observed warning in the successful trials.
  This is evidence about initialization, not proof of the cause of every 900E.
* Oreo queues `zygote-start` before `early-boot`; that hook is too late.
  Synchronous `post-fs-data` now preloads the eleven core modules before zygote.
* Persistent properties load after that hook. A root-only **consumed arm file**,
  `/data/leo-audio-trace/early-arm`, controls one preload attempt. Clearing only
  `persist.leo.audio.autoload` is not sufficient to disarm it.
* The original DSP loader skips work once APR reports modem STARTING. Radio setup
  therefore calls `boot-dsp` after firmware and rmt_storage preparation, before
  starting RIL. The deferred digital codec is rebound, without reloading Q6.
  Late audio setup then loads the machine module, waits for the soundcard, fixes
  permissions, and activates the original private Qualcomm HAL.

The successful startup was checked with `msm8909sndcard`, both native services,
the Qualcomm HAL mapping, absence of the generic fallback, and read-only system.
Setup rearms the next boot only after validation. A consumed arm is not recreated
on failure. A later runtime crash can still occur; this is not a general watchdog.
USB interruptions also occur with Windows still showing the normal Leo USB ID;
those observations must not be reported as Qualcomm 900E crashes.

## Build and install

For the existing retained runtime, run `prepare-earpiece-mixer.py`, followed by
`install-audio-fix.py`. It checks the target and idle call state, saves originals
and hashes privately, updates policy, mixer, audio init and radio hook, and leaves
system read-only. It deliberately removes the arm and clears validation.

To try startup, create `early-arm` with mode 0600 in its root-only directory,
sync and reboot. After software checks **and physical confirmation**, run
`verify-audio-runtime.py --accept`. Use `start.sh disable` to disarm normal audio;
`install-audio-fix.py --restore` restores the saved files and disarms startup.
The original full runtime rollback also removes the new arm file.

Build feedback with `build-keylight.sh` and `build-vibrator-hal.sh`, then install
with `install-input-feedback.py`. Its `--restore` restores saved originals; reboot
removes active init hooks. Built binaries and diagnostic logs remain ignored.
No boot or recovery partition was written for these changes.

`trace-audio-modules.py --load` is now core-only. Machine loading requires the
explicit `--load-machine`; `--quiesce-framework` and unique per-trial watchdog
markers prevent an earlier trial's timer from restarting a later probe. Kernel
warning checks use the current trial's uptime rather than historical warnings.
