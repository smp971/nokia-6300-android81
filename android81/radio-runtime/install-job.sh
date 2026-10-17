#!/system/bin/sh
# Detached installation job: USB loss must not interrupt remount/copy cleanup.
set -eu
trap '' HUP
export PATH=/system/bin
stage=/tmp/leo-radio-install
echo RUNNING > "$stage/status"
finish() {
    result=$?
    mount -o remount,ro /system || result=1
    if [ "$result" = 0 ]; then echo COMPLETE; else echo FAILED; fi > "$stage/status"
}
trap finish EXIT
test -f /leo-framework-internal
test "$(getprop ro.boot.hwid)" = 0x0
grep -qx PARTNAME=system /sys/class/block/mmcblk0p30/uevent
test ! -e /system/vendor/leo-radio
test ! -e /system/vendor/leo-radio.new
test ! -e /system/etc/init/leo-radio.rc
mount -o remount,rw /system
cp -Rp "$stage/files/vendor/leo-radio" /system/vendor/leo-radio.new
restorecon -R /system/vendor/leo-radio.new
mv /system/vendor/leo-radio.new /system/vendor/leo-radio
cp -p "$stage/files/etc/init/leo-radio.rc" /system/etc/init/leo-radio.rc.new
restorecon /system/etc/init/leo-radio.rc.new
mv /system/etc/init/leo-radio.rc.new /system/etc/init/leo-radio.rc
setprop persist.radio.multisim.config dsds
sync
