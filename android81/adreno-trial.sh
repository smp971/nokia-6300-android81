#!/system/bin/sh
# Temporary firmware lookup override; no installed EGL/gralloc/boot replacement.
set -eu
path=/sys/module/firmware_class/parameters/path
governor=/sys/class/kgsl/kgsl-3d0/devfreq/governor
oldpath=$(cat "$path")
oldgovernor=$(cat "$governor")
restore() {
    printf '%s' "$oldpath" > "$path"
    echo "$oldgovernor" > "$governor"
    sync
}
trap restore EXIT
cat /proc/sys/kernel/random/boot_id
echo "old_firmware_path=$oldpath old_gpu_governor=$oldgovernor"
sha256sum /tmp/adreno-fw/a300_pfp.fw /tmp/adreno-fw/a300_pm4.fw
dmesg > /data/local/tmp/leo-adreno-kernel-before.log
printf '%s' /tmp/adreno-fw > "$path"
echo performance > "$governor"
echo stage=prepared
sync
set +e
LD_LIBRARY_PATH=/tmp/adreno-probe-libs:/system/vendor/lib:/system/lib /tmp/adreno-probe
result=$?
echo "probe_exit=$result"
dmesg > /data/local/tmp/leo-adreno-kernel-after.log
sync
exit "$result"
