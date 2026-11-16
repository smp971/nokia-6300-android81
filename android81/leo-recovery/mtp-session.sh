#!/system/bin/sh
# Run inside root recovery. The caller owns the lifetime of this USB session.
set -eu
bb=/system/bin/busybox
state=$1
old=/config/usb_gadget/g1
g=/config/usb_gadget/leo_recovery_mtp
udc=msm_hsusb
daemon=
switched=
case "$2" in
    system) source=/mnt/leo-mount/system; label=System; mode=ro ;;
    data) source=/mnt/leo-mount/data; label=Data; mode=ro ;;
    internal) source=/mnt/leo-mount/data/media/0; label='Internal storage'; mode=ro ;;
    sd) source=/sdcard; label=microSD; mode=rw ;;
    *) exit 2 ;;
esac
[ "$($bb cat "$old/UDC")" = "$udc" ]
[ "$($bb cat "$old/idVendor")" = 0x18d1 ]
[ "$($bb cat "$old/idProduct")" = 0xd001 ]
[ "$($bb readlink -f "$old/configs/b.1/f1")" = "$old/functions/ffs.adb" ]
[ ! -L "$old/configs/b.1/f2" ]
[ ! -e "$g" ]
[ ! -e "$state/jail" ]
jail=$state/jail

cleanup() {
    result=$?
    trap - EXIT
    if [ -n "$switched" ]; then
        if [ "$($bb cat "$g/UDC")" = "$udc" ]; then
            printf '\n' > "$g/UDC" || result=1
        fi
        if [ -z "$($bb cat "$old/UDC")" ]; then
            printf '%s' "$udc" > "$old/UDC" || result=1
        fi
    fi
    if [ -n "$daemon" ]; then
        kill -KILL "$daemon" 2>/dev/null || true
        wait "$daemon" 2>/dev/null || true
    fi
    $bb umount "$jail/ffs" 2>/dev/null || true
    $bb umount "$jail/volume" 2>/dev/null || true
    $bb rm -f "$g/configs/b.1/f1" "$jail/mtp.conf" "$jail/umtprd"
    $bb rmdir "$g/configs/b.1/strings/0x409" "$g/configs/b.1" "$g/strings/0x409" \
        "$g/functions/ffs.leorecoverymtp" "$g" "$jail/volume" "$jail/ffs" "$jail" 2>/dev/null || true
    exit "$result"
}
trap cleanup EXIT
trap 'exit 0' TERM INT
$bb mkdir -m 700 "$jail" "$g"
$bb mkdir "$jail/volume" "$jail/ffs" "$g/configs/b.1" "$g/strings/0x409" \
    "$g/configs/b.1/strings/0x409" "$g/functions/ffs.leorecoverymtp"
$bb mount -o bind "$source" "$jail/volume"
$bb mount -o "remount,bind,$mode,nodev,nosuid,noexec" "$jail/volume"
$bb mount -t functionfs leorecoverymtp "$jail/ffs"
printf 0x18d1 > "$g/idVendor"
printf 0x4ee1 > "$g/idProduct"
printf 695d > "$g/strings/0x409/serialnumber"
printf Leo > "$g/strings/0x409/manufacturer"
printf 'Leo Recovery' > "$g/strings/0x409/product"
printf MTP > "$g/configs/b.1/strings/0x409/configuration"
$bb ln -s "$g/functions/ffs.leorecoverymtp" "$g/configs/b.1/f1"
$bb cp /sbin/leo-mtp-responder "$jail/umtprd"
$bb cat > "$jail/mtp.conf" <<CONF
storage "/volume" "$label" "$mode"
product "Leo Recovery"
manufacturer "Leo"
serial "695d"
interface "MTP"
usb_functionfs_mode 0x1
usb_dev_path "/ffs/ep0"
usb_epin_path "/ffs/ep1"
usb_epout_path "/ffs/ep2"
usb_epint_path "/ffs/ep3"
usb_max_packet_size 0x200
loop_on_disconnect 0
CONF
$bb chroot "$jail" /umtprd -conf /mtp.conf > "$state/responder.log" 2>&1 &
daemon=$!
for tick in 1 2 3 4 5; do
    [ ! -e "$jail/ffs/ep3" ] || break
    kill -0 "$daemon"
    $bb sleep 1
done
[ -e "$jail/ffs/ep3" ]
switched=1
printf '\n' > "$old/UDC"
printf '%s' "$udc" > "$g/UDC"
printf 'active\n' > "$state/active"
while kill -0 "$daemon" 2>/dev/null; do
    $bb sleep 1
done
