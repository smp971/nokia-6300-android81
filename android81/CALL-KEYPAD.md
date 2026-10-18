# Keypad call controls

## Device validation, 19 October 2026

The updated Dialer APK compiled and installed on 695d, retaining app data.
All ten offline UI checks passed: incoming, answered, muted, speaker, DTMF key
release, options, dialog dismissal without ending the call, ended, rejected,
and a fresh incoming preview. UI hierarchy assertions and device screenshots
were used; the phone display was explicitly awakened before the run.
The APK SHA-256 is `a72b358ee5a1c5ee8acfca1a3353d782a4228608af1e4afac443d9fdad925f79`.
These are simulated call-state tests, not a live call or microphone test.
After one reboot, the installed preview still launches and Android policy reports
`mEndcallBehavior=1`. Injecting the physical matrix scan code 107 through the input
device while both SIMs were idle returned to Leo HOME, confirming the new layout
and idle policy together. The pre-existing system boot ANR required selecting
Wait; this UI change does not repair that boot issue.
Post-boot software regression checks passed: stock audio HAL active, LTE data
validated with two ping replies, both SIMs ready and voice-registered, and system
mounted read-only. Boot ID: `5209e1f8-0c5b-45f6-bb47-4a5baf4a7ba6`.

![Offline incoming preview](screenshots/call-keypad-incoming-demo.png)
![Keypad options dialog](screenshots/call-keypad-options-demo.png)

The existing AOSP Oreo Dialer/InCallUI keeps ownership of Telecom calls. A small
native control strip reserves 48 dp at the bottom of the call screen; no web
runtime, extra phone service or continuous animation is added. Text is refreshed
only when its state changes, with a 500 ms check while the activity is visible.

## Keys

| Physical control | Incoming call | Connected call |
| --- | --- | --- |
| Green | Answer audio call | No additional action |
| OK | Answer audio call | Toggle the selected microphone/speaker control |
| Left soft / Menu | Answer audio call | Open a native options list |
| Right soft / Back | Reject without a text message | Disconnect foreground call |
| Red | System Telecom rejects the call | System Telecom ends the call |
| Left / Right | No action | Select microphone or speaker |
| Up / Down | No action | Adjust voice-call volume |
| 0–9, star, pound | No action | DTMF down/up through the existing Telecom adapter |

While dialing, Right soft cancels the outgoing call. The original graphical
controls remain available above the strip. Both physical Back keys have the
same in-call behavior; Back inside the options dialog dismisses that dialog.
Call audio routing and microphone hardware still require independent real-call
validation; showing a changed setting does not establish that the audio path works.

## Red key

Matrix scan code 107 is mapped to ENDCALL. Android's PhoneWindowManager handles
it globally, including calls whose UI is not foreground. System setting
`end_button_behavior=1` retains HOME while no call exists. A SettingsProvider
device overlay supplies that default for new data partitions; the installer sets
it explicitly for the current device. Reloading the physical key map requires a
reboot. Boot and recovery partitions are untouched.

## Reproduction

Run `build-call-keypad.sh` in WSL. `patch-call-keypad.py` copies the two maintained
Java files into AOSP Dialer, patches InCallActivity and adds a non-exported offline
preview activity. The build creates `output/leo-keypad-dialer.apk`.

Run `install-call-keypad.py` on Windows with serial 695d connected. It refuses to
install while Telecom reports a call, saves the previous system Dialer and key
layout under ignored `output/call-keypad-backup/`, installs an APK update retaining
app data, and atomically replaces only the key layout. It remounts system read-only
afterward. The installed base Dialer APK is retained unchanged.

`verify-call-keypad.py` exercises incoming/answer/reject/end states, microphone,
speaker, DTMF and options with the **same control class** and a fake host. Its
non-exported preview contains no Telecom, phone or network APIs. Screenshots and
UI state checks validate the controls without placing a call. Real incoming call
delivery, connected-call actions, DTMF transport, and audio need separate testing.

The preview is not a default dialer or a replacement phone service. It is launched
explicitly as `com.android.dialer/com.android.incallui.LeoCallPreview` for testing.
