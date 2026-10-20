# Leo visual refresh and Email integration — 19 October 2026

This is an original native Android UI inspired by the compact, colorful menus
and active standby of keypad phones. No Symbian/KaiOS artwork or application
code is included in the new icon set. It does not use a WebView.

## Appearance

- Six themes: the original Violet, Apus, Ocean and Pădure, plus Clasic · Albastru
  and Coral. Choose them under Settings → Aspect → Fundaluri și teme.
- Shared palettes style native Leo headers, rounded list rows, selection,
  focused editors, softkey menus and footers. Custom Calculator, Clock, Notes,
  Calendar, SMS bubbles and Android-app list also use the palette.
- `IconArt` draws the same colorful symbols in HOME and application headers.
  The grid uses 50px artwork with space below it for labels; long names are
  shortened to fit their cell. No icon bitmaps or perpetual animation loops.
- HOME has four standby shortcuts: Phone, Messages, Music and Email. Left/right
  selects one, OK opens it; Back clears the selection. With none selected, OK
  opens the app grid. There are now 23 entries on three pages.

## Phone

The final layout has **no on-screen numeric keypad**. Type using the physical
keys. The earlier decorative/interactive keypad trial was removed at the user's
request before publishing this revision.

Three favorite cards replace it. Down from the number field focuses the first
card; left/right chooses a card, OK assigns a contact to an empty card or loads
an assigned number into the editor. Loading a favorite never places a call.
Green still opens SIM selection and a separate call confirmation. Long-press a
card, or use Options → Editează favorite, to replace/clear it. Favorites keep a
local snapshot of the selected name/number; later contact edits require
reassignment. No contact was automatically chosen during testing.

Up from the number editor focuses the tab strip. Left/right changes Apelare /
Istoric / Statistici; Down accesses the current tab's controls. History reads
the latest 100 calls. Statistics aggregate at most the latest 5,000 local call
records, including incoming/outgoing/missed counts, total duration and a rolling
seven-day count/duration. They are not carrier billing or lifetime statistics.

HOME and the dialer's main page show per-SIM operator, registration/RAT and
signal dBm when supplied by the platform. Telephony listeners update the
display and are unregistered while paused. No cell scan, GPS, network traffic
or background polling is added. Missing signal values are omitted.

## Email

`EmailActivity` is a keypad frontend over the installed AOSP Email provider.
The signature-protected provider permission is requested explicitly; the frontend
does not read HostAuth, credentials or passwords. Queries use restricted
projections and cap lists at 100 entries; message bodies are bounded to 256 KiB.
HTML becomes plain text with no WebView or external-image loading.

The frontend implements account/folder/message lists, a scrollable reader and a
native compose form. Authentication, synchronization, attachments and final
send confirmation remain in AOSP Email, which may require the keypad mouse.
Leo hands a composition to that app; it does not silently send mail. Account
creation, live IMAP/SMTP, provider authentication, read-state updates and real
message sending/receiving are **not validated**. No account credentials were
entered or mail sent during this work.

AOSP Email's setup crashed because a `<bitmap>` wrapper referenced the API-21
vector `ic_next_24dp`. `patch-email-resources.py` replaces that wrapper with an
inset drawable and corrects the equivalent compose-button vector wrapper.
`build-email-resources.sh` rebuilds Email. The rebuilt APK was installed as a
same-signed data update, retaining the original system base. Account setup now
opens successfully; later setup/compose screens still need real-account tests.

## Verification

The final Leo APK compiled and installed, and its system base was synchronized
without deleting data or rebooting. On-device checks covered theme application,
HOME/grid artwork and spacing, dialer telemetry, History/Statistics tab switching,
and D-pad/OK opening Contacts from an empty favorite. The real journal supplied
8 calls and 1:13 total duration in the statistics check. Calculator 2 + 3 returned
5. Empty-account Email, the Leo compose form and corrected AOSP account setup
opened. Favorites assignment with a real contact and live mail remain untested.

Audio stayed active and MTP/ADB stayed connected. This is a visual/functionality
check, not a latency or battery benchmark. Some specialized and external Android
screens still retain their existing layouts.

## Intermittent menu-header flicker — 20 October 2026

The user observed the application-name header flickering after opening the menu.
The idle-grid capture reproduced no changing header pixels: all 20 frames matched
before the change. There was no new crash in the collected crash buffer; older
Email resource failures were retained in that buffer and are not new failures.

HOME had been invalidating its whole View for signal and badge callbacks even
while the grid was visible, where neither value is drawn. Telemetry now updates
its cache without invalidating the grid, identical signal values are ignored,
and Home redraws only when displayed values change. Explicit navigation still
invalidates the View, including returning through a new HOME intent.

The custom HOME View now declares and paints an opaque background, with a solid
grid title strip. Keypad dialogs also paint opaque surfaces and use a matching
window/status-bar background, with dimming and window animations disabled.

The APK compiled, installed and was synchronized to the system base. Another 20
idle-grid frames had identical header pixels after the change. The options menu
and grid were visually inspected. One uiautomator dump returned a null root;
screenshots and ADB still worked, and SurfaceFlinger/system_server remained
running. Physical confirmation of the intermittent flicker remains pending.
This reduces avoidable redraw/composition work; it does not establish that a
graphics-driver or panel problem has been eliminated.
