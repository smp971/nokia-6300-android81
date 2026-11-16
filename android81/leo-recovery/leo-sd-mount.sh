#!/sbin/busybox sh
# The early selector already verified the images and attached these loops.
set -eu
bb=/sbin/busybox
[ -f /leo-framework-sd-v1 ]
for index in 0 1 2; do
    case "$index" in
        0) name=system ;;
        1) name=data ;;
        2) name=cache ;;
    esac
    backing=$("$bb" cat "/sys/class/block/loop$index/loop/backing_file")
    case "$backing" in
        */LeoAndroid/"$name".img) ;;
        *) exit 11 ;;
    esac
    "$bb" mknod "/dev/leo-android-loop$index" b 7 "$index"
done
"$bb" mount -t ext4 -o ro,nodev,noload /dev/leo-android-loop0 /system
[ -r /system/framework/services.jar ]
if [ -f /sbin/leo-sd-radio-start.sh ] && [ -f /system/vendor/leo-radio/start.sh ]; then
    "$bb" mount -o bind /sbin/leo-sd-radio-start.sh /system/vendor/leo-radio/start.sh ||
        printf '%s\n' 'Leo SD radio: cannot bind the startup script' >&2
fi
"$bb" mount -t ext4 -o nosuid,nodev,errors=remount-ro /dev/leo-android-loop1 /data
"$bb" mount -t ext4 -o nosuid,nodev,noexec,errors=remount-ro /dev/leo-android-loop2 /cache
printf '%s\n' LEO_ANDROID_SD_PARTITIONS_MOUNTED
