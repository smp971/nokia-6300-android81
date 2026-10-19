#!/system/bin/sh
# Engineering defaults for the clean, keypad-only Android installation.
# Run after boot; write only Android's freshly formatted userdata settings.
cmd settings put global device_provisioned 1
cmd settings put secure user_setup_complete 1
cmd settings put secure show_ime_with_hard_keyboard 0
cmd settings put system screen_off_timeout 1800000
cmd settings put global window_animation_scale 0
cmd settings put global transition_animation_scale 0
cmd settings put global animator_duration_scale 0
cmd settings put global bluetooth_on 0
cmd settings put global ble_scan_always_enabled 0
sh /system/bin/svc bluetooth disable
cmd lock_settings set-disabled true
# Use Leo Visual when installed; otherwise let Android resolve an available HOME.
if cmd package list packages ro.leo.visual | grep -qx 'package:ro.leo.visual'; then
    cmd package set-home-activity --user 0 ro.leo.visual/.MainActivity
    cmd activity start-activity -n ro.leo.visual/.MainActivity
else
    cmd activity start-activity -a android.intent.action.MAIN -c android.intent.category.HOME
fi
# Leo system-app permissions for the keypad engineering image.
if cmd package list packages ro.leo.visual | grep -qx 'package:ro.leo.visual'; then
    for permission in READ_PHONE_STATE READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG CALL_PHONE READ_SMS RECEIVE_SMS SEND_SMS; do
        cmd package grant --user 0 ro.leo.visual android.permission.$permission
    done
fi
# Default physical-key text input, only when the IME is installed.
if cmd package list packages ro.leo.keypad | grep -qx 'package:ro.leo.keypad'; then
    /system/bin/ime enable ro.leo.keypad/.KeypadIme
    /system/bin/ime set ro.leo.keypad/.KeypadIme
fi
