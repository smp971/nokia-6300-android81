#!/system/bin/sh
# System-owned bootstrap. No accounts or personal state are shipped in the ROM.
set -eu
uid=$(dumpsys package ro.leo.visual | sed -n 's/^[[:space:]]*userId=\([0-9]*\)[[:space:]]*$/\1/p' | head -n 1)
case "$uid" in ''|*[!0-9]*) exit 1;; esac
[ "$uid" -ge 10000 ] && [ "$uid" -le 19999 ] || exit 1
# PackageManager assigns a new UID after a wipe. Never bake the developer UID
# into system. Refresh authorization before permitting terminal/FM connections.
umask 077
printf '%s\n' "$uid" > /data/system/leo-terminal-root.uid.new
chown 0:0 /data/system/leo-terminal-root.uid.new
chmod 600 /data/system/leo-terminal-root.uid.new
mv /data/system/leo-terminal-root.uid.new /data/system/leo-terminal-root.uid
restorecon /data/system/leo-terminal-root.uid
setprop ctl.start leo_terminal_root
mkdir -p /data/system/leo-firstboot
[ ! -f /data/system/leo-firstboot/ui-v1 ] || exit 0
cmd settings put global device_provisioned 1
cmd settings put secure user_setup_complete 1
cmd settings put secure show_ime_with_hard_keyboard 0
cmd settings put system screen_off_timeout 60000
cmd settings put system end_button_behavior 1
for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    cmd settings put global "$key" 0
done
cmd lock_settings set-disabled true
cmd package set-home-activity --user 0 ro.leo.visual/.MainActivity
cmd settings put secure dialer_default_application ro.leo.visual
/system/bin/sh /system/bin/ime enable ro.leo.keypad/.KeypadIme
/system/bin/sh /system/bin/ime set ro.leo.keypad/.KeypadIme
cmd settings put secure enabled_notification_listeners ro.leo.visual/ro.leo.visual.LeoNotificationService
cmd settings put secure enabled_notification_policy_access_packages ro.leo.visual
for permission in READ_PHONE_STATE READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_SMS RECEIVE_SMS SEND_SMS READ_CALENDAR WRITE_CALENDAR READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE RECORD_AUDIO ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION CAMERA; do
    cmd package grant --user 0 ro.leo.visual android.permission.$permission || true
done
# Verify essential results before marking initialization complete. Later boots
# refresh only root authorization; user choices must survive.
[ "$(cmd settings get secure default_input_method)" = ro.leo.keypad/.KeypadIme ]
[ "$(cmd settings get secure dialer_default_application)" = ro.leo.visual ]
touch /data/system/leo-firstboot/ui-v1
cmd activity start-activity -n ro.leo.visual/.MainActivity
