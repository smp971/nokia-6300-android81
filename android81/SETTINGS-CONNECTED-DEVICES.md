# Connected devices on the Leo port

## USB service is optional

On the current engineering port, bootstrap handles the USB gadget and ADB.
Android does not publish a `usb` binder service. Opening Settings → Connected
devices previously constructed `UsbBackend` unconditionally. On this eng build,
the missing service lookup causes a fatal framework WTF and closes Settings.

`patch-settings-optional-usb.py` checks whether that service exists before
constructing the backend. The USB preference controller hides its row when no
backend exists, skips receiver registration, and avoids querying its summary.
Bluetooth and Printing remain available. This does not implement Android USB
mode switching or alter bootstrap's ADB configuration.

## Build and verification

Run `android81/build-settings-canvas.sh` in the configured kaios WSL environment.
It applies the optional USB patch alongside the existing Wi-Fi and rendering
patches, then builds Settings into ignored `android81/output/settings-canvas.apk`.

On 2026-10-19 the rebuilt APK installed successfully on the Nokia 6300. Starting
`com.android.settings/.Settings$ConnectedDeviceDashboardActivity` returned
`Status: ok`. The activity remained resumed, displayed enabled Bluetooth, and
survived a Home/return cycle. The subsequent log sample contained no new fatal
exception, forced finish, or missing USB service message. This verifies the page;
Bluetooth pairing and hotspot operation require separate hardware tests.
