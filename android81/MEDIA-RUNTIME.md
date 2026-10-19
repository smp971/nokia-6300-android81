# Local music and USB transfer — 19 October 2026

## Audio formats

| Format | Implemented path | Evidence |
| --- | --- | --- |
| MP3 | Android MediaPlayer / AOSP codec | Synthetic file prepared and played; position 2936 ms in a per-process log. |
| FLAC | Android MediaPlayer / AOSP codec | Synthetic file prepared and played; position 2797 ms. |
| AAC / ADTS | Android MediaPlayer / AOSP codec | Synthetic file prepared and played; position 2807 ms. Other AAC profiles/containers need separate samples. |
| OGG Vorbis | Android MediaPlayer / AOSP codec | Audible music confirmed, including after reboot. |
| WMA v2 / ASF | Leo's local preparation, then Android WAV playback | ARM decoder generated non-silent PCM; a 12-second WMA was audible through `PlaybackService`, confirmed by the user. |
| WMA v1 / Pro / Lossless | Included in the minimal decoder build | No representative files tested; DRM is unsupported. |

The first WMA fixture was too quiet for the user to hear. Its ARM-decoded PCM
contained nonzero samples, and a subsequent clearer fixture was confirmed
audible. A completed decoder process alone is not treated as audible success.

WMA support belongs to **Muzică Leo**, not every Android media app. Android's
system-wide extractor/codec registry is unchanged. Native MP3/FLAC/AAC playback
uses the existing audio codec configuration; new codec names were not invented
in XML. Video codecs are outside these audio checks.

`build-wma-decoder.sh` builds FFmpeg 4.4.8 from the official release, with a
pinned source SHA-256. It uses the retained ARM Android toolchain. Network
protocols, unrelated demuxers/codecs, hardware codecs and external libraries are
disabled. Only local ASF/WMA decoding and PCM output are enabled. The installed
LGPL notice is `/system/etc/NOTICE.leo-wma`; the release source and build recipe
are available at https://ffmpeg.org/releases/ffmpeg-4.4.8.tar.xz and in this repo.
Built binaries and the source archive remain local and ignored.

WMA preparation runs off the UI thread, requests audio focus again before
playback, and is cancelled when playback stops or another track is selected.
The original is copied to private cache and never overwritten or uploaded.
Limits: input 32 MiB, prepared PCM approximately 128 MiB, 90-second decoding
timeout, at least 180 MiB free cache space. Tracks exceeding these limits fail
with a message rather than silently playing a truncated result. Temporary files
are removed on stop/completion; leftovers from an interrupted app process are
removed on the next playback-service start. First playback requires preparation.

The library also looks for indexed `.wma` files in MediaStore.Files, because the
stock Android audio scanner may not classify them in its audio collection.
Play/pause, seeking and volume use the same MediaPlayer path after preparation.

## MTP experiment

The running kernel enables `USB_F_MTP` and `USB_CONFIGFS_F_MTP`. The native
diagnostic bootstrap owns USB; Android's USB manager is absent. Setting
`sys.usb.config=mtp` alone cannot add file transfer to this build.

Two initial shell-based composite-gadget trials disconnected USB and did not
complete their automatic rollback. A full power cycle restored diagnostic ADB.
They did not validate MTP and are not enabled on boot.

The next implementation uses independent init services, persistent local logs,
and a separate 60-second reboot watchdog. No transfer is enabled merely by
installing it. `install-media-runtime.py` installs the WMA decoder, notice and
these experimental hooks; `--restore` restores the original files. Init hooks
require reboot. Only request MTP during an attended diagnostic with idle calls.
The default next boot uses the bootstrap's ADB-only configuration.

Controller runtime power was set temporarily from `auto` to `on` during the
successful codec tests; ADB stayed connected through those checks. This is an
investigation result, not proof that runtime suspend causes every USB failure
or a validated permanent battery-friendly fix.
