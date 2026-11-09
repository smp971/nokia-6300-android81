# Leo keypad interface · Nokia reference pass

The Nokia C3-00 RM-614 package contains Series 40 firmware. The 08.63 and
08.71 MCU payloads contain media player, albums, shuffle, repeat and search
labels. The [Nokia C3 user guide](https://www.speakout7eleven.ca/media/wysiwyg/user_guides/Nokia_C3-00_UG_en-US.pdf)
documents the keypad and the context-dependent Options action.

The Nokia N8 RM-596 package is Symbian firmware `111.040.1511`. Local read-only
inspection reconstructed the Nokia FPSX payloads and walked both the core
ROFS and Euro 2 ROFS directory trees. The extracted English resources
`resource/apps/mpxcommonplaybackview.r01` and `mpxcollectionviewhg.r01`
describe the music library, current playback, track details, lyrics,
equalizer, repeat and shuffle. The core contains the corresponding
`mpxcommonplaybackview.mif` and `musicwidgeticons.mif` icon sets. The
[FPSX/ROFS unpacker source](https://github.com/saur0n/unpacker) was used to
cross-check the container and filesystem layouts. The [N8 user guide](https://one.nz/MEDIA_CustomProductCatalog/m4120203_Nokia_N8_Userguide.pdf)
documents the hold-to-seek interaction and library/playlist flows.

The original N8 sample music thumbnail
`Private/2003157E/720p/music/default_music1.png` (222 × 222) was extracted from
the core and placed in Leo's private files as `music-placeholder.png` on the
development phone. It is the missing-art fallback in both music views. It
stays local to that installation. The APK draws its own placeholder when the
local file is absent or invalid. Stock firmware files are not distributed by
this repository.

## Controls and layout

- Music Player and Media Current use the same panel for album art, wrapped
  titles, state, elapsed time, volume, transport and softkeys. The music
  player retains the real spectrum. Media Current retains the FM tab.
- Tap Left/Right for previous/next in music, or hold to seek. `4`/`6` also
  change tracks. OK/`5` plays or pauses, Up/Down changes volume. `#` opens the
  library in the music player. Back returns to the previous screen.
- Music Options groups repeat, shuffle, equalizer, sleep, save, resume and
  share under Playback settings. The top level exposes the library, queue,
  track details, lyrics and stop. Track details follows the N8 option.
- On Home, `1` opens app search, short `5` opens quick access, and `0` opens
  Media Current. Short 2–4 and 6–9 open Phone with the digit filled by default.
  The optional direct digit search still exists for those keys. Long-press
  shortcuts are retained. In Search, Down enters results and
  Up from the first result returns to the editor. The right softkey deletes
  text while editing and goes back when there is no text to delete.
- Home cards and shortcut labels are measured at their rendered size and
  fit their allotted columns. The card counter has its own space. Hidden
  Home cards and shortcuts are skipped during keypad navigation.

On the development Nokia, the UI checks covered Home → Phone and Quick
Access, Media Current → Music Player → Back, the same rendered music card
in both views, focus when returning from Playback settings, the FM tab,
dedicated Menu and the right softkey editing a query before opening
Calculator from its result. Track tap, hold and canceled-release behavior
also passed a host check with a fake playback backend. These checks did not
measure playback speed or retest live FM and music audio.
