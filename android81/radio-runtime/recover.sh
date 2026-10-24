#!/system/bin/sh
# One bounded cold-start recovery; never loop on an absent network or a disabled radio.
set -u
PATH=/system/bin

classify() {
    case "$2:$3" in 1:*|*:0) echo SKIP; return;; esac
    [ "$1" = READY,READY ] && [ "$2" = 0 ] && [ "$3" = 1 ] || { echo WAIT; return; }
    states=$(printf '%s\n' "$snapshot" | grep -E '^[[:space:]]*mServiceState=')
    registered=$(printf '%s\n' "$states" | grep -c 'mVoiceRegState=0(IN_SERVICE)' || true)
    [ "$registered" -eq 0 ] || { echo HEALTHY; return; }
    off=$(printf '%s\n' "$states" | grep -c 'mVoiceRegState=3(POWER_OFF)' || true)
    idle=$(printf '%s\n' "$snapshot" | grep -cE '^[[:space:]]*mCallState=0[[:space:]]*$' || true)
    [ "$off" -eq 2 ] && [ "$idle" -eq 2 ] && [ "$4" = 2 ] && { echo RECOVER; return; }
    echo WAIT
}

# Pure classifier for sanitized fixture tests. Does not query or change the phone.
if [ "${1:-}" = --classify ] && [ "$#" -eq 5 ]; then
    snapshot=$(cat)
    classify "$2" "$3" "$4" "$5"
    exit 0
fi

read_state() {
    snapshot=$(timeout 6 dumpsys telephony.registry 2>/dev/null) || { echo WAIT; return; }
    airplane=$(timeout 5 /system/bin/sh /system/bin/settings get global airplane_mode_on 2>/dev/null) || airplane=
    cell=$(timeout 5 /system/bin/sh /system/bin/settings get global cell_on 2>/dev/null) || cell=
    # Desired power comes from the framework, not just a vendor's cached state.
    desired=$(timeout 6 dumpsys activity service com.android.phone/.TelephonyDebugService 2>/dev/null | grep -cE '^[[:space:]]*mDesiredPowerState=true[[:space:]]*$' || true)
    classify "$(getprop gsm.sim.state)" "$airplane" "$cell" "$desired"
}

if [ "${1:-}" = --check ]; then read_state; exit 0; fi
[ "$#" -eq 0 ] || exit 2
[ "$(getprop sys.leo.radio.recovery_attempted)" != 1 ] || exit 0
stable=0
for attempt in $(seq 1 30); do
    state=$(read_state)
    case "$state" in
        HEALTHY|SKIP) setprop sys.leo.radio.recovery_state "$state"; exit 0;;
        RECOVER) stable=$((stable + 1));;
        *) stable=0;;
    esac
    uptime=$(cut -d. -f1 /proc/uptime)
    if [ "$stable" -ge 3 ] && [ "$uptime" -ge 150 ]; then
        # Re-read immediately before restarting: an intervening call cancels recovery.
        [ "$(read_state)" = RECOVER ] || exit 0
        setprop sys.leo.radio.recovery_attempted 1
        setprop sys.leo.radio.recovery_state RESTARTING
        log -t LeoRadioRecovery 'Both READY SIMs stuck POWER_OFF after boot; one RIL restart'
        setprop ctl.stop leo_rild2
        setprop ctl.stop leo_rild1
        sleep 1
        setprop ctl.start leo_rild1
        setprop ctl.start leo_rild2
        setprop sys.leo.radio.recovery_state RESTARTED
        exit 0
    fi
    sleep 10
done
setprop sys.leo.radio.recovery_state NO_ACTION
