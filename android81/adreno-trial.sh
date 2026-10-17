#!/system/bin/sh
# Temporary firmware lookup override; no installed EGL/gralloc/boot replacement.
set -eu
path=/sys/module/firmware_class/parameters/path
governor=/sys/class/kgsl/kgsl-3d0/devfreq/governor
oldpath=$(cat "$path")
oldgovernor=$(cat "$governor")
restore() {
    trial_result=$?
    printf '%s' "$oldpath" > "$path"
    echo "$oldgovernor" > "$governor"
    echo "trial_exit=$trial_result"
    sync
}
trap restore EXIT
cat /proc/sys/kernel/random/boot_id
echo "old_firmware_path=$oldpath old_gpu_governor=$oldgovernor"
sha256sum /tmp/adreno-fw/a300_pfp.fw /tmp/adreno-fw/a300_pm4.fw
dmesg > /data/local/tmp/leo-adreno-kernel-before.log
printf '%s' /tmp/adreno-fw > "$path"
echo firmware_path_bytes:
od -An -tx1 "$path"
# param_attr_show appends exactly one display newline to the stored string.
test "$(cat "$path" | wc -c)" -eq 15
echo performance > "$governor"
echo stage=prepared
sync
set +e
unset LD_PRELOAD
LD_LIBRARY_PATH=/tmp/adreno-probe-libs:/system/vendor/lib:/system/lib /tmp/adreno-probe
result=$?
echo "probe_exit=$result"
dmesg > /data/local/tmp/leo-adreno-kernel-after.log
sync
exit "$result"
