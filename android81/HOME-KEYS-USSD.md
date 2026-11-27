# KeyShell · held Home keys and USSD · 27 November 2026

Dashboard shortcuts now use a 650-ms hold. A short digit opens Phone with that
character entered. App launch and speed-dial assignments for 2–9 keep their
existing IDs and preference formats; preparing a contact never calls it.

| Key | Short press | Hold |
| --- | --- | --- |
| 0 | Phone, digit 0 | Now playing |
| 1 | Phone, digit 1 | Local search |
| 2–9 | Phone, selected digit | Assigned app or prepared speed-dial contact |
| 5, when unassigned | Phone, digit 5 | Quick access |
| * | Next dashboard card; Phone if no cards | Keypad lock |
| # | Open dashboard card; Phone if no cards | Silent / normal, through shared SoundProfiles |

Arrows retain navigation, Up quick controls and Down notifications. Existing
OK/left-soft then * unlocking remains separate. App-grid digits, editors, games,
call controls and the lock screen retain their own handlers. Favorites now offer
an OK contact picker, keeping the digit keys available for dialing.

`HomeKeyPress` defers short actions until release, fires a hold once even without
hardware repeats, ignores repeat events and cancels pending work on pause or a
new control key. No continuous polling or global keypad remapping is added.
Help and the long-press settings page describe the new map. The former optional
Home numeric-search preference is retained in backups, but the agreed short-key
Phone behavior takes precedence.

## USSD/MMI

Phone now accepts service codes such as `*123#`, `*#43#` and `##21#` as complete
destinations instead of rejecting them as invalid phone numbers. The field allows
64 characters; codes need a starting * or #, at least one digit and a final #.
The parser rejects incomplete codes, escaped URLs and post-dial separators.
Ordinary formatted phone numbers keep their existing validation.

Green opens **Choose SIM for USSD**. Only SIM subscription accounts are offered;
SIP accounts are excluded for these requests. Selecting the SIM passes the intact
code to Telecom with that PhoneAccountHandle. `Uri.fromParts` encodes the final
# as part of the destination rather than a URL fragment. Local `*#06#` stays local.

Oreo's telephony service owns operator replies and interactive MMI dialogs;
this change reuses that route rather than creating a second USSD session engine.
Primary platform references:
[TelephonyConnectionService, Android 8.1](https://android.googlesource.com/platform/packages/services/Telephony/+/android-8.1.0_r1/src/com/android/services/telephony/TelephonyConnectionService.java),
[MMIDialogActivity](https://android.googlesource.com/platform/packages/services/Telephony/+/android-8.1.0_r1/src/com/android/phone/MMIDialogActivity.java).
Actual carrier replies, interactive sessions and IMS/CS fallback still need an
explicit operator trial. No request was sent to a carrier in these checks.

## Fresh evidence

Same Nokia 6300 4G TA-1286, serial 695d, Android 8.1/API 27:

- 56 device checks passed: delayed taps, one-shot holds, cancellation, actual Home
  routing for short 0/1/5 and held 0/1, destination parsing/URI encoding and Green
  opening the SIM picker without choosing an account.
- Kernel scan injection verified short 5 → Phone, held 0 → Now playing, unassigned
  held 5 → Quick access, held * → lock and physical OK → * → Home. Long # timing
  used a callback fixture; real sound-profile application was not repeated here.
- The first test harness waited for a new `onCreate` on singleTask Home. It was
  replaced with the existing bounded resume pattern; the final run passed.
- 107 host checks and the signed candidate audit passed, retaining all 431
  baseline external dependencies. Catalog IDs and Android components are unchanged.
- Home, accessibility, notification listener and keypad IME match the pre-install
  state. The owner's authenticated WebUI, automatic start and enabled features
  were preserved. No calls or SMS were made.

USB ADB disappeared during the session. The final update and these latter tests
used the owner's existing certificate-pinned HTTPS WebUI and on-device Terminal,
not a second handset or emulator. Phone serial/model and running APK were checked
through that connection. The USB transport itself was not recovered in this run. The temporary diagnostic
adbd process was stopped; no TCP ADB listener was left open. Disposable test
packages and transfer files were removed; Home and the persistent WebUI were
restored.

Running data APK SHA-256:
`226224e8ed21988d1db70e35e90c5f92640beb3163cc7c5cf850ecd933b8d022`.
No shell APK was promoted into `/system`, and no ROM flash or reboot was performed.
Build tests: `python android81/build-pocket-tests-windows.py --home-dial`.
