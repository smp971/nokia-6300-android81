#!/system/bin/sh
PATH=/system/bin
export PATH
exec >>/data/local/tmp/leo-ims-watchdog.log 2>&1
while [ "$(getprop persist.leo.ims.enabled)" = 1 ]; do
    if [ "$(getprop sys.boot_completed)" = 1 ] &&
       [ "$(getprop sys.leo.radio.start_state)" = started ] &&
       ! pidof org.codeaurora.ims:leo_controller >/dev/null; then
        /system/bin/sh /system/bin/am startservice -n org.codeaurora.ims/.LeoImsController
    fi
    sleep 30
done
