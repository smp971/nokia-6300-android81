#!/system/bin/sh
# Metadata-only, bounded capture. Never read a FunctionFS endpoint or change USB.
set -eu
seconds=${1:-60}
case "$seconds" in *[!0-9]*|'') exit 2;; esac
[ "$seconds" -ge 4 ] && [ "$seconds" -le 120 ] || exit 2
base=/data/leo-stability
umask 077
mkdir -p "$base"
chmod 700 "$base"
mkdir "$base/usb-transport.lock" || exit 4
log="$base/usb-transport.log"
: > "$log"
cleanup() {
    trap - EXIT INT TERM
    printf 'END\n' >> "$log"
    rmdir "$base/usb-transport.lock"
}
trap cleanup EXIT
trap 'exit 130' INT TERM
trap '' HUP
n=0
while [ "$n" -lt $((seconds/2)) ]; do
    read up rest < /proc/uptime
    printf 'SAMPLE uptime=%s\n' "$up" >> "$log"
    for item in /sys/class/udc/msm_hsusb/state /sys/devices/platform/soc/78d9000.usb/power/control /sys/devices/platform/soc/78d9000.usb/power/runtime_status; do
        printf '%s=' "$item" >> "$log"
        cat "$item" >> "$log" 2>&1 || true
    done
    for proc in /proc/[0-9]*; do
        [ -r "$proc/comm" ] || continue
        read name < "$proc/comm" || continue
        [ "$name" = adbd ] || continue
        printf 'DAEMON pid=%s\n' "${proc##*/}" >> "$log"
        # Names/counts only; no process arguments, socket payloads or user files.
        ls -l "$proc/fd" >> "$log" 2>&1 || true
        for thread in "$proc"/task/[0-9]*; do
            printf 'THREAD tid=%s\n' "${thread##*/}" >> "$log"
            for item in comm wchan syscall stack; do
                printf '%s: ' "$item" >> "$log"
                cat "$thread/$item" >> "$log" 2>&1 || true
                printf '\n' >> "$log"
            done
        done
    done
    n=$((n+1))
    sleep 2
done
