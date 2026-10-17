#!/system/bin/sh
# Run detached in PID 1's namespace; retain evidence across USB transport loss.
set -u
trap '' HUP
export PATH=/system/bin
export LD_LIBRARY_PATH=/tmp/leo-radio-ipc:/tmp/leo-radio-probe/lib:/system/vendor/lib
out=/tmp/leo-radio-evidence
if [ -d "$out" ]; then
    mv "$out" "$out-$(date +%s)"
fi
mkdir -p "$out"
echo RUNNING > "$out/status"
timeout 145 logcat -b all -v threadtime > "$out/live-log.txt" 2>&1 &
logger=$!
sh /tmp/leo-radio-start.sh --ipc
sleep 6
/tmp/leo-radio-dms > "$out/dms-before.txt" 2>&1
if [ "${1:-}" = --direct-online ]; then
    /tmp/leo-radio-dms --online > "$out/dms-online-request.txt" 2>&1
fi
for delay in 3 20 40; do
    sleep "$delay"
    /tmp/leo-radio-dms > "$out/dms-after-$delay.txt" 2>&1
    getprop > "$out/properties-after-$delay.txt"
    dumpsys telephony.registry > "$out/registration-after-$delay.txt"
    for pid in $(pidof rild); do
        if grep -q '/tmp/leo-radio-probe/lib/' "/proc/$pid/cmdline"; then
            timeout 15 debuggerd -b "$pid" > "$out/stacks-$pid-after-$delay.txt" 2>&1
        fi
    done
done
dmesg > "$out/kernel.txt"
echo COMPLETE > "$out/status"
kill "$logger" 2>/dev/null || true
