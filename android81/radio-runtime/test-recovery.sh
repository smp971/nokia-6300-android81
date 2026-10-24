#!/system/bin/sh
# Sanitized stdin fixtures; only --classify is invoked, never the restart branch.
set -eu
script=${1:?recovery script required}
off='  mCallState=0
  mServiceState={mVoiceRegState=3(POWER_OFF)}
  mCallState=0
  mServiceState={mVoiceRegState=3(POWER_OFF)}'
healthy='  mCallState=0
  mServiceState={mVoiceRegState=0(IN_SERVICE)}
  mCallState=0
  mServiceState={mVoiceRegState=3(POWER_OFF)}'
out='  mCallState=0
  mServiceState={mVoiceRegState=1(OUT_OF_SERVICE)}
  mCallState=0
  mServiceState={mVoiceRegState=1(OUT_OF_SERVICE)}'
checks=0
check() {
    expected=$1; fixture=$2; shift 2
    actual=$(printf '%s\n' "$fixture" | /system/bin/sh "$script" --classify "$@")
    [ "$actual" = "$expected" ] || { echo "FAIL expected=$expected actual=$actual"; exit 1; }
    checks=$((checks + 1))
}
check RECOVER "$off" READY,READY 0 1 2
check HEALTHY "$healthy" READY,READY 0 1 2
check SKIP "$off" READY,READY 1 1 2
check SKIP "$off" READY,READY 0 0 2
check WAIT "$off" READY,ABSENT 0 1 2
check WAIT "$off" READY,READY 0 1 0
check WAIT "$off" READY,READY null 1 2
check WAIT "$off" READY,READY 0 null 2
check WAIT "$out" READY,READY 0 1 2
check WAIT '' READY,READY 0 1 2
busy=$(printf '%s\n' "$off" | sed '1s/mCallState=0/mCallState=2/')
check WAIT "$busy" READY,READY 0 1 2
history="$out
  2026-10-24 log mServiceState={mVoiceRegState=3(POWER_OFF)}"
check WAIT "$history" READY,READY 0 1 2
echo "PASS $checks radio recovery guard checks; no radio commands issued"
