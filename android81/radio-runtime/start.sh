#!/system/bin/sh
# Mount the original firmware read-only and start the validated dual-RIL chain.
set -eu
export PATH=/system/bin
base=/system/vendor/leo-radio
export LD_LIBRARY_PATH=$base/lib:/system/vendor/lib:/system/lib
exec >>/data/local/tmp/leo-radio-setup.log 2>&1
date
test -f /leo-framework-internal
test "$(getprop ro.boot.hwid)" = 0x0
mkdir -p /dev/block/bootdevice/by-name
for entry in /sys/class/block/mmcblk0p*; do
    name= number=
    while IFS='=' read -r key value; do
        case "$key" in PARTNAME) name="$value" ;; DEVNAME) number="$value" ;; esac
    done < "$entry/uevent"
    if [ -n "$name" ]; then
        alias=/dev/block/bootdevice/by-name/$name
        if [ ! -e "$alias" ]; then ln -s "/dev/block/$number" "$alias"; fi
        test "$(readlink -f "$alias")" = "/dev/block/$number"
    fi
done
grep -qx 'PARTNAME=modem' /sys/class/block/mmcblk0p25/uevent
if [ ! -r /firmware/image/modem.mdt ]; then
    mount -o remount,rw /
    trap 'mount -o remount,ro / 2>/dev/null || true' EXIT
    mkdir -p /firmware
    mount -o remount,ro /
    mount -t vfat -o ro /dev/block/bootdevice/by-name/modem /firmware
fi
printf '%s' /firmware/image > /sys/module/firmware_class/parameters/path
"$base/bin/irsc_util" "$base/sec_config"
mkdir -p /data/misc/radio /dev/socket/qmux_radio
chown radio:radio /data/misc/radio /dev/socket/qmux_radio
chmod 0770 /data/misc/radio
chmod 2770 /dev/socket/qmux_radio
setprop persist.radio.multisim.config dsds
setprop persist.leo.radio.trace 0
setprop persist.radio.adb_log_on 0
setprop ctl.stop ril-daemon
setprop ctl.start leo_rmt_storage
setprop ctl.start leo_per_mgr
if [ "$(getprop persist.leo.data.enabled)" = 1 ]; then
    mkdir -p /dev/socket/netmgr /data/misc/net
    chown radio:radio /dev/socket/netmgr /data/misc/net
    chmod 0750 /dev/socket/netmgr /data/misc/net
    setprop ctl.start leo_netmgrd
    # DSI initialization needs the netmgr listener before either RIL opens data.
    count=0
    while [ ! -S /dev/socket/netmgr/netmgr_connect_socket ] && [ "$count" -lt 30 ]; do
        sleep 1
        count=$((count + 1))
    done
fi
sleep 1
setprop ctl.start leo_rild1
setprop ctl.start leo_rild2
echo LEO_RADIO_SERVICES_STARTED
