# Local music access

Music Leo requires the Android runtime READ_EXTERNAL_STORAGE permission to query local audio on internal storage and the SD card.

## Empty library diagnosis and fix

The SD card was mounted and its Music directory was present, but the app had lost its storage permission. MediaStore queries raised SecurityException; the previous library silently treated that failure as an empty result. App-op access alone did not grant the missing runtime permission. The reason the permission was lost has not been established.

The library now offers an explicit permission request and a link to app permissions. It checks access again when returning from Settings. Query failures display a retry page, or a partial-library notice when some results are available, instead of silently reporting an empty library.

## Verification

After restoring read access and installing the corrected APK, the read-only MusicLibraryAudit instrumentation under the app UID reported permission granted, 26 indexed audio tracks and 411 indexed media files. Both Music directories were readable; the SD directory contained 14 immediate entries. The library activity opened successfully. No playback, media rescan, file modification or preference reset was performed in this check.

The installed LeoVisual system APK was also synchronized, hash-verified and the system partition remounted read-only. User data was preserved.

Run the audit with the existing six-feature test APK installed:

```sh
am instrument -w ro.leo.visual.tests/ro.leo.visual.MusicLibraryAudit
```
