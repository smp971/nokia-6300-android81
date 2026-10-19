#!/system/bin/sh
# The native diagnostic bootstrap owns this gadget. Keep its ADB interface.
# Transfer is opt-in for this USB session; no block devices are exported.
set -eu
g=/config/usb_gadget/g1
power=/sys/devices/platform/soc/78d9000.usb/power/control
saved_power=/dev/.leo-usb-power-before
[ "$(cat "$g/strings/0x409/serialnumber")" = 695d ]
mode=${1:-}
clear_functions() {
    for link in "$g/configs/b.1/f1" "$g/configs/b.1/f2"; do
        if [ -L "$link" ]; then
            target=$(readlink -f "$link")
            case "$target" in "$g/functions/ffs.adb"|"$g/functions/mtp.gs0") rm "$link";; *) exit 3;; esac
        fi
    done
}
renew_adbd() {
    old=$(pidof adbd)
    new=$(/system/bin/leo-adbd-renew "$old")
    ready=0
    for i in $(seq 1 40); do
        if [ "$(cat /proc/$new/comm 2>/dev/null)" = adbd ] && ls -l /proc/$new/fd 2>/dev/null | grep -q '/dev/usb-ffs/adb/ep2'; then ready=1;break;fi
        sleep 0.2
    done
    [ "$ready" = 1 ]
    echo "diagnostic adbd renewed: $new"
}
case "$mode" in
request)
    exec >>/data/local/tmp/leo-usb-mtp.log 2>&1
    date
    requested=$(getprop sys.leo.usb.request)
    setprop sys.leo.usb.request idle
    case "$requested" in mtp|adb) ;; *) exit 2;; esac
    if dumpsys telephony.registry | grep -Eq 'mCallState=[12]'; then
        setprop sys.leo.usb.state failed
        exit 1
    fi
    setprop sys.leo.usb.state switching
    if /system/bin/sh "$0" "$requested"; then exit 0;fi
    setprop sys.leo.usb.state failed
    exit 1
    ;;
watchdog)
    # Recovery reboots are only for explicitly armed host diagnostics.
    [ "$(getprop sys.leo.usb.trial)" = 1 ] || exit 0
    sleep 20
    # Logging must never block the recovery timer.
    timeout 5 logcat -d -s MtpService:D MtpServer:D MtpReceiver:D AndroidRuntime:E '*:S' > /data/local/tmp/leo-mtp-service.log 2>&1 &
    sleep 40
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
    if [ ! -f "$saved_power" ]; then
        (umask 077; cat "$power" > "$saved_power")
    fi
    printf on > "$power"
    mkdir -p "$g/functions/mtp.gs0"
    chown root:mtp /dev/mtp_usb
    chmod 660 /dev/mtp_usb
    # Stop an earlier server before changing descriptors.
    /system/bin/sh /system/bin/am stopservice -n com.android.providers.media/.MtpService || true
    echo 'before USB unbind'
    printf '\n' > "$g/UDC"
    echo 'after USB unbind'
    clear_functions
    # Google 4ee2 on Windows expects MTP at MI_00, ADB at MI_01.
    # Link order determines actual interface order. Plain ln -s is required:
    # Oreo toybox's forced form fails with EPERM on this configfs.
    ln -s "$g/functions/mtp.gs0" "$g/configs/b.1/f1"
    ln -s "$g/functions/ffs.adb" "$g/configs/b.1/f2"
    renew_adbd
    printf 0x4ee2 > "$g/idProduct"
    printf 'mtp,adb' > "$g/configs/b.1/strings/0x409/configuration"
    echo 'before composite USB bind'
    printf msm_hsusb > "$g/UDC"
    echo 'after composite USB bind'
    /system/bin/sh /system/bin/am broadcast -a android.hardware.usb.action.USB_STATE -n com.android.providers.media/.MtpReceiver --ez connected true --ez configured false --ez mtp true --ez configChanged true --ez unlocked true
    /system/bin/sh /system/bin/am startservice -n com.android.providers.media/.MtpService --ez unlocked true
    setprop sys.leo.usb.state mtp,adb
    ;;
adb)
    /system/bin/sh /system/bin/am broadcast -a android.hardware.usb.action.USB_STATE -n com.android.providers.media/.MtpReceiver --ez connected false --ez configured false --ez mtp false --ez unlocked false
    /system/bin/sh /system/bin/am stopservice -n com.android.providers.media/.MtpService || true
    printf '\n' > "$g/UDC"
    clear_functions
    ln -s "$g/functions/ffs.adb" "$g/configs/b.1/f1"
    renew_adbd
    printf 0xd001 > "$g/idProduct"
    printf adb > "$g/configs/b.1/strings/0x409/configuration"
    printf msm_hsusb > "$g/UDC"
    if [ -f "$saved_power" ]; then
        previous=$(cat "$saved_power")
        case "$previous" in auto|on) printf '%s' "$previous" > "$power";; *) exit 3;; esac
        rm "$saved_power"
    fi
    setprop sys.leo.usb.state adb
    ;;
*) exit 2;;
esac
