#!/system/bin/sh
# Temporary, bounded daemon trial, executed in PID 1's mount namespace.
set -eu
export PATH=/system/bin
export LD_LIBRARY_PATH=/tmp/leo-radio-probe/lib:/system/vendor/lib
chmod 0755 /tmp/leo-radio-probe/probe /tmp/leo-radio-probe/rmt_storage /tmp/leo-radio-probe/qmuxd /tmp/leo-radio-probe/irsc_util /tmp/leo-radio-probe/pm-service
if [ "${1:-}" = --ipc ]; then
    export LD_LIBRARY_PATH=/tmp/leo-radio-ipc:/tmp/leo-radio-probe/lib:/system/vendor/lib
    # Stock uses separate rild instances, not AOSP's multi-SIM callback ABI.
    [ ! -f /tmp/leo-radio-ipc/libril.so ]
    setprop persist.radio.multisim.config dsds
fi
mkdir -p /dev/block/bootdevice/by-name
for entry in /sys/class/block/mmcblk0p*; do
    name= number=
    while IFS='=' read -r key value; do
        case "$key" in
            PARTNAME) name="$value" ;;
            DEVNAME) number="$value" ;;
        esac
    done < "$entry/uevent"
    if [ -n "$name" ] && [ ! -e "/dev/block/bootdevice/by-name/$name" ]; then
        ln -s "/dev/block/$number" "/dev/block/bootdevice/by-name/$name"
    fi
done
grep -qx 'PARTNAME=modem' /sys/class/block/mmcblk0p25/uevent
if [ ! -r /firmware/image/modem.mdt ]; then
    mount -o remount,rw /
    mkdir -p /firmware
    mount -o remount,ro /
    mount -t vfat -o ro /dev/block/bootdevice/by-name/modem /firmware
fi
echo /firmware/image > /sys/module/firmware_class/parameters/path
/tmp/leo-radio-probe/irsc_util /tmp/leo-radio-probe/sec_config
chown radio:radio /dev/smdcntl0
chmod 0640 /dev/smdcntl0
for group in radio audio bluetooth gps nfc; do
    mkdir -p "/dev/socket/qmux_$group"
    chown radio:radio "/dev/socket/qmux_$group"
done
timeout 240 /tmp/leo-radio-probe/rmt_storage > /tmp/leo-radio-rmt.log 2>&1 &
echo $! > /tmp/leo-radio-rmt.pid
if [ "${1:-}" != --ipc ]; then
    timeout 240 /tmp/leo-radio-probe/qmuxd > /tmp/leo-radio-qmux.log 2>&1 &
    echo $! > /tmp/leo-radio-qmux.pid
fi
if ! pidof pm-service >/dev/null; then
    timeout 240 /tmp/leo-radio-probe/pm-service > /tmp/leo-radio-pm.log 2>&1 &
    echo $! > /tmp/leo-radio-pm.pid
fi
timeout 200 /tmp/leo-radio-probe/probe --boot-modem > /tmp/leo-radio-boot.log 2>&1 &
echo $! > /tmp/leo-radio-boot.pid
binary=/system/vendor/bin/hw/rild
if [ "${1:-}" = --ipc ]; then
    timeout 180 env LD_PRELOAD=/tmp/leo-radio-ipc/shim.so "$binary" \
        -l /tmp/leo-radio-probe/lib/libril-qc-qmi-1.so > /tmp/leo-radio-rild.log 2>&1 &
else
    timeout 180 "$binary" -l /tmp/leo-radio-probe/lib/libril-qc-qmi-1.so \
        > /tmp/leo-radio-rild.log 2>&1 &
fi
echo $! > /tmp/leo-radio-rild.pid
echo 'Bounded modem and radio trial started; legacy qmuxd is skipped in IPC mode.'
