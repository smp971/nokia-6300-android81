# KeyShell web browser

Catalog ID **19** opens the native **Browser** hub. It offers the graphical
browser, the existing text reader, address/search entry and optional Opera Mini
integration. Existing shortcuts, favorites and text-reader storage keep their
meaning. The hub and graphical page inherit the existing Browser-specific
theme override. Address entry includes **Open with** selection and retains a draft
when returning to the hub.

## Graphical page

The page uses shared KeyShell headers, address subtitle, progress, dialogs and
softkeys. Options include address/search, reload, stop, text size, forward,
JavaScript on/off, text mode, help and close. Back follows page history.
D-pad scrolls and selects where the page supports keyboard focus. The dedicated
Menu exposes **Mouse din taste** for other controls: D-pad moves the pointer,
OK clicks and `#` exits. Guidance stays inside Help.

WebView runs in the existing `:webapps` process, keeping its runtime out of
Home and sharing one WebView data directory on API 27. This does not loosen
the local web-app runtime's origin checks, CSP, resource allowlist or blocked
worker networking. No additional library or background service is introduced.

The reference ROM uses WebView **61.0.3163.98**. Accelerated rendering crashed
in `Chrome_InProcGp` with SIGBUS during a controlled SVG-image trial. The new
browser uses a software layer and disables acceleration for its own activity.
Images, scripts, navigation and history passed the same trial in this mode.
Modern-site compatibility remains tied to the ROM's WebView version.

HTTP/HTTPS pages are accepted; file/content/JavaScript URL navigation and URL
credentials are rejected. File/content access is disabled and no JavaScript
bridge is exposed. Certificate errors cancel loading. Web permission and file
upload requests are denied. Downloads use the existing DownloadManager after
confirmation and respect its saved Wi-Fi preference. Downloads, authenticated
sites, streaming video and long browsing sessions were not tested here.

Automatic keypad-service repair now runs only in the launcher process. Browser,
camera and local web-app processes no longer infer that the service is absent
from their own process and toggle its configuration unnecessarily.

## Opera Mini trial

The APK came from [Opera's official download page](https://www.opera.com/download),
redirecting to the official CDN's
[Opera Mini 99.8.2254.3041](https://download3.operacdn.com/ftp/pub/opera/android/mini/99.8.2254.3041/operamini-99.8.2254.3041.apk).
It declares minimum API 23 and ARMv7. Build-tools 34 `apksigner` verified its
original signature for API 27. It was installed separately; no vendor binary
is included in this repository or in the earlier private ROM.

```text
Package  com.opera.mini.native
APK      e901f7da2cc293a00e001124e616755ddab4d0ada45eb82d99c5701448b869e0
Signer   57acbc525f1b2ebd19196cd6f014397cc910fd18841e0ae850febc3e1e593ff2
```

On this port, onboarding opened but the main browser remained at its loading
logo. It is an optional compatibility experiment, not the validated browsing
path. Default-browser selection and optional data collection were skipped,
and the app was stopped after the trial. The graphical browser and text reader
work independently. Opera URL handoff resolves its HTTP `VIEW` handler instead
of reusing its launcher alias.

## Validation

The final KeyShell APK passed **188 daily UI checks**, **56 graphical browser
checks**, **48 prior polish checks**, **72 theme checks**, and the physical
keypad smoke. The host suite passed **69 checks**. The full Windows build
compiled **279** sources; the DEX audit retained all **431** baseline dependency
classes without duplicates.

The browser suite serves an isolated loopback HTTP fixture and checks actual
JavaScript output, image load completion, a link, Back, disabling scripts and
closing the page. The assertions need no public website. An external HTTPS page
and the native address-to-page route were checked separately on the handset.
Automation uses `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`; generic
`uiautomator dump` can suspend accessibility while inspecting its overlay.

```powershell
python android81/build-pocket-apps-windows.py
python android81/build-pocket-tests-windows.py --web-browser
python android81/framework-command.py --timeout 180 /system/bin/am instrument -w ro.leo.visual.webbrowserchecks/ro.leo.visual.WebBrowserDeviceTests
python android81/build-pocket-tests-windows.py --daily-polish
python android81/framework-command.py --timeout 180 /system/bin/am instrument -w ro.leo.visual.dailychecks/ro.leo.visual.KeyShellDailyUiTests
python android81/smoke-leo-keypad.py --serial 695d
```

Running KeyShell data update SHA-256:
`03ccaac8f784f7f4ccd3b8a69ebc413018fa8b6f74e6459542c426791e246718`.
The bundled system base is
`abcb93b6ee1305ededb7e52105a2d5dc232f8c795e67420834e49564cb89f0e6`.
The previous private clean ROM predates the browser. Resource measurements in
[the daily UI report](DAILY-UI-POLISH.md) refer to the preceding UI batch and
do not measure the separate WebView process.

## Screenshots

| Browser hub | HTTPS page | Local image/JavaScript fixture |
| --- | --- | --- |
| ![Native browser hub](screenshots/browser/hub.png) | ![External HTTPS page](screenshots/browser/https.png) | ![Graphical browser fixture](screenshots/browser/fixture.png) |

After temporary test packages were removed, Home, appearance, accessibility,
notification-listener access and IME settings matched the initial snapshot.
The notification listener was explicitly rebound after instrumentation, and
its live service binding was checked along with the accessibility connection.
The original music volume was restored and the handset was returned to Home.
