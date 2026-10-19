#!/system/bin/sh
# Engineering trial setup. Uses matching factory NV; never writes persist.
set -eu
exec >>/data/local/tmp/leo-connectivity-setup.log 2>&1
base=/vendor/leo-connectivity
# Disarm before touching WCNSS. An unsuccessful boot cannot retry indefinitely.
automatic=$(getprop persist.leo.connectivity.auto)
if [ "$automatic" = 1 ]; then setprop persist.leo.connectivity.auto 0; fi
setprop sys.leo.connectivity.state starting
trap 'result=$?; if [ "$result" -ne 0 ]; then setprop sys.leo.connectivity.state failed; fi' EXIT
test "$(getprop ro.boot.serialno)" = 695d
test "$(uname -r)" = 4.9.240-perf-g4f9e270fd77f
# Radio normally mounts firmware during boot, too late for early connectivity.
# Verify the stock partition identity, then mount only read-only. No NV writes.
grep -qx 'PARTNAME=modem' /sys/class/block/mmcblk0p25/uevent
if [ ! -r /firmware/image/wcnss.mdt ]; then
    mount -o remount,rw /
    if ! mkdir -p /firmware; then mount -o remount,ro /; exit 1; fi
    mount -o remount,ro /
    mount -t vfat -o ro /dev/block/mmcblk0p25 /firmware
fi
printf '%s' /firmware/image > /sys/module/firmware_class/parameters/path
count=0
while [ ! -r /firmware/image/wcnss.mdt ] && [ "$count" -lt 15 ]; do
    sleep 1
    count=$((count + 1))
done
test -r /firmware/image/wcnss.mdt
# No runtime calibration file exists on the current Android data partition.
# Refuse to silently ignore a later calibration file.
/system/bin/sh "$base/no-cal.sh"
sleep 4
if ! grep -q '^wlan ' /proc/modules; then
    timeout 40 insmod "$base/modules/pronto_wlan.ko"
fi
test -d /sys/class/net/wlan0
setprop wlan.driver.status ok
mkdir -p /data/misc/wifi/sockets /data/misc/wifi/hostapd
chown wifi:wifi /data/misc/wifi /data/misc/wifi/sockets /data/misc/wifi/hostapd
chmod 0770 /data/misc/wifi /data/misc/wifi/sockets /data/misc/wifi/hostapd
chown bluetooth:bluetooth /dev/smd2 /dev/smd3
chmod 0660 /dev/smd2 /dev/smd3
setprop ctl.start leo_wifi_hal
setprop ctl.start leo_bluetooth_hal
setprop sys.leo.connectivity.state ready
if [ "$automatic" = 1 ] && [ "$(getprop persist.leo.connectivity.validated)" = 1 ]; then
    setprop persist.leo.connectivity.auto 1
fi
echo LEO_CONNECTIVITY_TRIAL_READY
