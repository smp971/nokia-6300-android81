#!/system/bin/sh
# Requires the private libraries, probe and guard staged under /tmp.
# Never selects Adreno for Android; never changes firmware path or GPU governor.
set -eu
test -f /tmp/adreno-guard.so
test -f /tmp/adreno-probe
test -f /tmp/adreno-probe-libs/libEGL_adreno.so
case "${1:-deny}" in
    deny) fake=0 ;;
    fake) fake=1 ;;
    *) echo 'Usage: adreno-guarded-trial.sh [deny|fake]' >&2; exit 2 ;;
esac
export LEO_ADRENO_FAKE_DEVICES="$fake"
export LD_PRELOAD=/tmp/adreno-guard.so
export LD_LIBRARY_PATH=/tmp/adreno-probe-libs:/system/vendor/lib:/system/lib
exec /tmp/adreno-probe --guarded
