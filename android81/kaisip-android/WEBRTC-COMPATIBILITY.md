# WebRTC compatibility for Android WebView 61

`assets/vendor/webrtc-adapter-6.4.8.js` is the unmodified official release bundle from
https://webrtc.github.io/adapter/adapter-6.4.8.js.
Its original BSD 3-clause license is in `assets/vendor/webrtc-adapter-LICENSE.md`.

The Android host loads this before the KaiSIP adapter to supply the track and
sender APIs used by JsSIP on the project's legacy Chromium engine. URLs and
byte hashes are recorded in `webrtc-adapter.json` beside this document.
The builder checks both the bundle and license before packaging. Validate any
replacement with the real WebView 61 fixture, not just a modern host browser.
