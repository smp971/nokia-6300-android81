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
cmd package set-home-activity --user 0 ro.leo.shell/.MainActivity
cmd activity start-activity -n ro.leo.shell/.MainActivity
