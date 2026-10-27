# Leo offline web-app SDK · version 1

Local keypad apps on the existing Android 8.1 port. This is not a KaiOS
compatibility layer or a modern Internet browser.

Package layout: `manifest.json`, HTML entry and local assets.

```json
{"id":"my-app","name":"My app","entry":"index.html"}
```

IDs match `[a-z][a-z0-9-]{0,39}` and cannot start with `stage-`. Limits: 64 files,
512 KiB/file, 2 MiB total. Paths are relative, without empty, dot or parent
segments. Existing IDs cannot be silently overwritten.

Build the included example with Python 3.9 or newer:

```powershell
python -X utf8 android81/package-web-app.py android81/web-app-sdk/pocket-board --output android81/output/my-app.leoapp.zip
```

Output must be outside the input folder and must not already exist. Transfer
through MTP, Bluetooth or Companion, then open in Files and confirm import.

## Key contract

Implement `window.leoKey(code)` and draw a visible selection:

| Code | Key |
| --- | --- |
| 19 | Up |
| 20 | Down |
| 21 | Left |
| 22 | Right |
| 23 | OK |

The shell owns Options/Back softkeys. Use the existing IME for ordinary HTML
inputs and support click handlers for external mice. Apps implement their own
D-pad focus order; arbitrary desktop websites are not made keypad-ready.

Use localStorage for small persistent data. Each ID gets its own origin
`https://ID.apps.leo.invalid`. Bundle CSS, JS, images and fonts locally. Remote
resources, connections, external navigation, frames, permissions and file/content
access are blocked. No Android JavaScript bridge is provided. Inline JS/CSS work.

The renderer is WebView 61: use compatible syntax and test on Nokia. Background
timers are not reliable because WebView pauses when hidden. Runtime Clear data
clears localStorage/sessionStorage. There is no background Android-service API,
package signature scheme or separate Android UID in this SDK.
