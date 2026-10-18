# Ordinary Android audio and Mobile networks — 18 October 2026

The earlier AudioTrack speaker test bypassed compressed audio decoding. Ordinary
OGG ringtones failed with `Unable to instantiate a decoder for audio/vorbis`,
`MEDIA_ERROR what=1 extra=-2147483648`, and playback position zero. Both
`/system/etc/media_codecs.xml` and its audio include were absent, although the
AOSP Vorbis decoder library was installed.

Installed the device's `media_codecs.xml` and the matching Android 8.1 AOSP
`frameworks/av/media/libstagefright/data/media_codecs_google_audio.xml`.
Restarted `mediacodec` and `media`, because their codec lists were cached.
The same Andromeda.ogg test then instantiated OMX.google.vorbis.decoder,
advanced to 2540 ms, and the user confirmed it was audible. This validates
OGG playback through MediaPlayer, not microphone or call audio.
Both configurations are included in the device product for future images.

Opening `com.android.phone/.MobileNetworkSettings` originally crashed at
`DataUsagePreference.initialize:59`: `DataUsageController.getDataUsageInfo`
returned null when traffic accounting was unavailable. The patch handles this
documented failure path, displays “Data usage unavailable”, and disables only
that statistics preference. It does not fabricate traffic figures or change
mobile network configuration. `build-phone-settings.sh` builds TeleService;
the full framework build also applies the patch. Compilation and APK update
succeeded. A full reboot cleared mixed old/new classes in the persistent shared
phone process. After reboot the activity opened successfully, displayed the
unavailable summary, and remained open when switching SIM tabs. The crash buffer
was empty; both SIMs reported READY. The separate system boot ANR remained and
was dismissed with Wait; this patch does not repair that issue.

After that reboot, MediaPlayer also played `content://settings/system/ringtone`
(3683 ms asset, position 2624 ms after the three-second probe) without decoder
errors. Audio runtime checks passed with the Qualcomm HAL and read-only system.
The temporary test app was removed after verification.

Private diagnostic logs and built APKs remain in ignored `output/`.
