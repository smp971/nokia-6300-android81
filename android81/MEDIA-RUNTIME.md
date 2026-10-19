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

## USB MTP transfer

**Validated on this phone with Windows:** internal shared storage and the
Transcend microSD volume both appear in Explorer. A synthetic UTF-8 file was
copied from the PC to each volume and back. All five copies had SHA-256
`9951ab8636467f3b488d1299717458864d4135de9946fbb9a08460b738ce641f`.
The user also confirmed normal file copying works.

Open **Setări Leo → Rețele → Transfer USB**. Select the first row with OK to
start or stop transfer. The page reports the resulting service state; it does
not measure an individual file-copy operation. Finish copying before switching
off. D-pad/OK toggles were tested in both directions; ADB returned in both modes.
Active calls block switching. The next boot uses diagnostic ADB only; transfer
is opt-in for the current boot, and no raw block device is exported.

The bootstrap owns USB; Android's UsbManager is absent. Independent init services
configure configfs and notify MediaProvider's MTP receiver explicitly. On this
kernel plain `ln -s` works where toybox's forced form fails with EPERM. Link order
must put MTP at MI_00 and ADB at MI_01 for Windows VID 18d1 / PID 4ee2.
`leo-adbd-renew` retains the original FunctionFS mount namespace while renewing
only the verified diagnostic adbd. System libraries are unchanged.

Normal Settings commands never arm a reboot watchdog. The separate watchdog is
restricted to host diagnostics explicitly setting `sys.leo.usb.trial=1`.
Earlier failed USB trials required power cycling; they are not evidence of
successful transfer. The final composite configuration and on/off UI were tested
after those corrections.

While MTP is enabled the controller's runtime power mode is forced to `on`.
The script saves its previous `auto`/`on` value once per boot and restores it on
transfer shutdown. The new restoration branch is installed but has not yet been
measured through another toggle cycle. Runtime suspend has not been established
as the cause of every historical ADB interruption. Long transfers, arbitrary
media files, unplug-during-copy behavior and battery cost need further testing.

`install-media-runtime.py` installs the WMA decoder, notice, USB script, helper
and init hooks. It verifies hashes, saves originals locally and remounts system
read-only afterward. Installation retains the current USB mode; new init hooks
require reboot. `--restore` restores the saved installation.
