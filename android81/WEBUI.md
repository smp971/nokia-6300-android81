# KeyShell WebUI · 27 November 2026

A phone-backed HTTPS control panel for the Android 8.1 Nokia port. The real
screen is framed in a keypad handset, with working D-pad, softkeys, number keys,
Call/End, Menu and Back. The disconnected page contains a sign-in form and empty
status fields. Applications, statistics, themes and folders come from Nokia after
authentication. There is no offline phone simulator or sample catalog in the APK.

![Real Nokia control in a keypad handset](screenshots/webui-fast-2026-11-27/remote.png)

## Connect

1. Connect Nokia and the PC to the same Wi-Fi network.
2. On Nokia, open **Applications → Advanced → WebUI**. The existing
   **Settings → Networks → More → KeyShell WebUI** route also works.
3. Enable WebUI and open the complete **HTTPS address** displayed in the first
   row. The port is **18764**; the Wi-Fi IP can change after reconnection.
4. At first connection, compare the certificate's SHA-256 fingerprint with the
   phone's **Address WebUI** page before accepting its local certificate.
5. Sign in. **Control telefon** opens first. Use its phone keys or focus the
   screen and use arrows, Enter, Escape/Backspace, digits, `*` and `#`.

Create an administrator on a new installation. There is no factory password in
source. Existing accounts, passwords, certificate and phone preferences survive
this update. Change your password under **Administrare → Parola mea**, or reset
it from the native WebUI app.

The owner-enabled service stays in the background, waits for Wi-Fi and rebinds
when its address changes. Automatic startup controls boot/package-update startup;
Home also resumes an enabled service. Disabling WebUI or its notification's Stop
action persists the off state. A satellite on Home indicates an active listener.
No production wake lock is held. If the display is asleep, use **Trezește**, then
**OK → \*** or **soft left → \*** if the keypad guard is active.

## Pages

| Page | Available controls |
| --- | --- |
| **Control telefon** | Real display, adaptive refresh, physical keypad, click-to-tap, wake/sleep/volume, Unicode clipboard paste, app launch |
| **Apeluri** | SIM choice, explicit call confirmation, active-call actions, speaker/receiver, mute, DTMF and recent history |
| **Mesaje** | Recent messages, search, compose/reply, SIM choice, segment count and explicit send confirmation |
| **Contacte** | Phone numbers, search and filling the call/SMS form without transmitting |
| **Prezentare** | Actual battery/charging, storage, RAM, health and catalog counts |
| **Aplicații și foldere / Aspect** | Stage folder/theme/grid changes and explicitly apply them on Nokia |
| **Fișiere** | Upload/download in the existing Companion exchange directory |
| **Administrare** | Own password, administrator/viewer accounts, WebUI theme, automatic startup and feature switches |

Call audio stays on Nokia. Active-call actions require KeyShell's default-dialer
integration. Remote screen/input and the dedicated communication APIs have
separate switches and require an administrator. Full keypad control can still
perform actions through the native apps; the API switches are not an app sandbox.
Read-only accounts cannot access remote input or private communications.

## Input and image performance

The browser sends ordered keys independently of frame updates. Rapid presses
are retained in a bounded queue; a missing receipt cancels unsent presses and
never repeats an ambiguous action automatically. No 750-ms post-key delay remains.

One UID-checked local helper reuses Android's input and screen-capture APIs during
active control. It starts lazily and exits after 15 idle seconds, with no wake
lock or polling. Softkeys preserve scans **139/158**, and dedicated Menu uses
**212**. The helper accepts fixed key/tap/capture operations, not shell commands.
Its read-only capture fallback retains compatibility with this port.

Frames share a 200-ms capture cache. Content hashes allow **304 Not Modified**
without retransmitting unchanged PNGs. Geometry and screen state travel in frame
headers, avoiding a separate status request per image. Refresh adapts to screen
activity and stops when its page is hidden. Images are decoded before replacing
the visible frame, avoiding a blank flash between updates.

See [fresh timing, device checks and build identity](WEBUI-REMOTE-FAST.md).

## Authentication and scope

Passwords use private bcrypt cost-10 verifiers, unique salts, at least eight
characters and at most 72 UTF-8 bytes. Up to eight users and 16 in-memory sessions
are supported. Sessions last at most 12 hours; reset/removal invalidates the user's
sessions. Five failed login/current-password attempts cause a 60-second cooldown.
The final administrator cannot be removed.

Production binds only the phone's Wi-Fi IPv4 address, using TLS 1.2 and its
AndroidKeyStore identity. Host/Origin checks and bundled-only CSP remain enforced.
Tokens, passwords, private rows and frames are not saved in browser storage.
Logout clears loaded phone data. The obsolete local-preview preference is removed
on page startup. Test fixtures live outside the packaged assets.

Only `files/documents/Companion` is shared: 16 MiB per transfer, bounded ASCII
names, no overwrites, staged/synced uploads and serialized publication. The two
server workers retain bounded queues and socket deadlines. There is no arbitrary
command, filesystem-path, Intent or remote package-install API.

Calls/SMS use session-bound, single-use 60-second confirmation tickets and explicit
physical SIM resolution. Native/browser SMS share the archive and sent/delivery
callbacks. [Transport boundaries and earlier validation](WEBUI-CONTROL.md).

## Build and checks

```text
python android81/build-pocket-apps-windows.py
python android81/build-pocket-tests-windows.py --webui-control
python android81/test-webui-browser.py
python android81/test-webui-control-browser.py
python -m unittest discover -s android81/tests -q
```

The retired offline server, demo generator and packaged sample catalog are removed.
`webui-test-fixture.py` supplies isolated browser-test data only; it is not served
or included in the Android APK. Actual cellular calling/SMS delivery through the
browser, long uptime, battery/deep-sleep response and other routers remain separate
live trials. No real call or SMS was sent during this update.
