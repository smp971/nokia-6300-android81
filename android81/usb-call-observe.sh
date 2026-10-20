#!/system/bin/sh
# Bounded diagnostic: log only controller/ADB state, restore runtime PM on exit.
set -eu
mode=${1:-observe}
seconds=${2:-120}
case "$seconds" in *[!0-9]*|'') exit 2;; esac
[ "$seconds" -ge 4 ] && [ "$seconds" -le 120 ] || exit 2
case "$mode" in observe|hold-awake) ;; *) exit 2;; esac
base=/data/leo-stability
power=/sys/devices/platform/soc/78d9000.usb/power/control
previous=$(cat "$power")
case "$previous" in auto|on) ;; *) exit 3;; esac
mkdir -p "$base"
chmod 700 "$base"
mkdir "$base/usb-trial.lock" || exit 4
umask 077
log="$base/usb-call-trial.log"
printf 'MODE %s PREVIOUS %s\n' "$mode" "$previous" > "$log"
cleanup() {
    trap - EXIT INT TERM
    printf '%s' "$previous" > "$power"
    read up rest < /proc/uptime
    printf 'RESTORED uptime=%s power=%s\n' "$up" "$(cat "$power")" >> "$log"
    rmdir "$base/usb-trial.lock"
}
trap cleanup EXIT
trap 'exit 130' INT TERM
trap '' HUP
if [ "$mode" = hold-awake ]; then printf on > "$power"; fi
n=0
while [ "$n" -lt $((seconds/2)) ]; do
    read up rest < /proc/uptime
    runtime=$(cat /sys/devices/platform/soc/78d9000.usb/power/runtime_status)
    suspended=$(cat /sys/devices/platform/soc/78d9000.usb/power/runtime_suspended_time)
    udc=$(cat /sys/class/udc/msm_hsusb/state)
    daemon=$(pidof adbd || true)
    printf 'uptime=%s power=%s runtime=%s suspended_ms=%s udc=%s adbd=%s\n' "$up" "$(cat "$power")" "$runtime" "$suspended" "$udc" "$daemon" >> "$log"
    n=$((n+1))
    sleep 2
done
