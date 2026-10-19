#!/system/bin/sh
# The native diagnostic bootstrap owns this gadget. Keep its ADB interface.
# Transfer is opt-in for this USB session; no block devices are exported.
set -eu
g=/config/usb_gadget/g1
[ "$(cat "$g/strings/0x409/serialnumber")" = 695d ]
mode=${1:-}
case "$mode" in
request)
    exec >>/data/local/tmp/leo-usb-mtp.log 2>&1
    date
    requested=$(getprop sys.leo.usb.request)
    setprop sys.leo.usb.request idle
    case "$requested" in mtp|adb) ;; *) exit 2;; esac
    exec /system/bin/sh "$0" "$requested"
    ;;
watchdog)
    sleep 60
    if [ "$(getprop sys.leo.usb.confirmed)" != 1 ]; then
        echo 'MTP not confirmed; reboot to diagnostic ADB configuration' >> /data/local/tmp/leo-usb-mtp.log
        setprop sys.powerctl reboot
    fi
    exit 0
    ;;
mtp)
    [ "$(getprop sys.boot_completed)" = 1 ]
    [ -d "$g/functions/ffs.adb" ]
    [ -L "$g/configs/b.1/f1" ]
    printf on > /sys/devices/platform/soc/78d9000.usb/power/control
    mkdir -p "$g/functions/mtp.gs0"
    chown root:mtp /dev/mtp_usb
    chmod 660 /dev/mtp_usb
    # Stop an earlier server before changing descriptors.
    /system/bin/sh /system/bin/am stopservice -n com.android.providers.media/.MtpService || true
    echo 'before USB unbind'
    printf '\n' > "$g/UDC"
    echo 'after USB unbind'
    ln -sf "$g/functions/mtp.gs0" "$g/configs/b.1/f2"
    printf 0x4ee2 > "$g/idProduct"
    printf 'mtp,adb' > "$g/configs/b.1/strings/0x409/configuration"
    echo 'before composite USB bind'
    printf msm_hsusb > "$g/UDC"
    echo 'after composite USB bind'
    /system/bin/sh /system/bin/am startservice -n com.android.providers.media/.MtpService --ez unlocked true
    setprop sys.leo.usb.state mtp,adb
    ;;
adb)
    /system/bin/sh /system/bin/am stopservice -n com.android.providers.media/.MtpService || true
    printf '\n' > "$g/UDC"
    rm -f "$g/configs/b.1/f2"
    printf 0xd001 > "$g/idProduct"
    printf adb > "$g/configs/b.1/strings/0x409/configuration"
    printf msm_hsusb > "$g/UDC"
    setprop sys.leo.usb.state adb
    ;;
*) exit 2;;
esac
