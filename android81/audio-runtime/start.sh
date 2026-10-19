#!/system/bin/sh
set -eu
base=/vendor/leo-audio
phase=${1:-}
trace() {
    printf '%s %s\n' "$(date +%s)" "$*" >> /data/leo-audio-trace/setup.log
    sync
}
restart_audio() {
    setprop ctl.stop audioserver
    setprop ctl.stop audio-hal-2-0
    sleep 1
    setprop ctl.start audio-hal-2-0
    sleep 1
    setprop ctl.start audioserver
}
fail() {
    trace 'setup failed; generic fallback requested'
    setprop persist.leo.audio.enabled 0
    setprop sys.leo.audio.state failed
    # Early preload must not start Android's audio classes ahead of normal boot.
    if [ "$phase" = setup ]; then restart_audio; fi
    exit 1
}
load() {
    if ! grep -q "^$2 " /proc/modules; then
        trace "before insmod $1"
        insmod "$base/modules/$1.ko" || fail
        trace "after insmod $1"
        sleep 1
    fi
}
fix_permissions() {
    chown system:audio /dev/snd/* /dev/msm_audio_cal
    chmod 660 /dev/snd/* /dev/msm_audio_cal
    restorecon -R /dev/snd
    restorecon /dev/msm_audio_cal
}
case "$phase" in
preload)
    # post-fs-data precedes both zygote-start and load_persist_props in Oreo.
    # A consumed root-only file arms this stage; a persist property is too late.
    [ -f /data/leo-audio-trace/early-arm ] || exit 0
    mkdir -p /data/leo-audio-trace
    chmod 700 /data/leo-audio-trace
    : > /data/leo-audio-trace/setup.log
    trace 'early preload entered'
    # Clear the arm before touching kernel modules. A failed boot will not loop.
    rm /data/leo-audio-trace/early-arm
    sync
    setprop persist.leo.audio.autoload 0
    setprop persist.leo.audio.enabled 0
    # Late module loading can fail contiguous allocations once Android is busy.
    # Only the synchronous early-boot service may load kernel modules now.
    [ "$(getprop init.svc.zygote)" != running ] || fail
    [ "$(uname -r)" = 4.9.240-perf-g4f9e270fd77f ] || fail
    before=$(dmesg | grep -c 'page allocation failure' || true)
    setprop sys.leo.audio.state loading
    load audio_q6_notifier q6_notifier_dlkm
    load audio_apr apr_dlkm
    load audio_adsp_loader adsp_loader_dlkm
    load audio_q6 q6_dlkm
    after=$(dmesg | grep -c 'page allocation failure' || true)
    [ "$before" = "$after" ] || fail
    load audio_wcd_core wcd_core_dlkm
    load audio_wcd9xxx wcd9xxx_dlkm
    load audio_mbhc mbhc_dlkm
    load audio_stub stub_dlkm
    load audio_platform platform_dlkm
    load audio_digital_cdc digital_cdc_dlkm
    load audio_analog_cdc analog_cdc_dlkm
    setprop sys.leo.audio.state core_preloaded
    trace 'core preloaded before framework; machine/DSP/HAL deferred until modem ready'
    ;;

boot-dsp)
    # Called by radio setup after firmware/rmt_storage, before either RIL
    # powers the modem. The stock loader skips boot if APR is already STARTING.
    [ "$(getprop sys.leo.audio.state)" = core_preloaded ] || exit 0
    [ -r /firmware/image/modem.mdt ] || fail
    trace 'radio storage ready; before boot_adsp'
    printf 1 > /sys/kernel/boot_adsp/boot
    sleep 3
    # Digital codec probe deferred while APR was not ready. Retry its device,
    # without unloading Q6 or re-running large kernel allocations.
    if [ ! -e /sys/bus/platform/drivers/msm_digital_codec/msm_digital_codec ]; then
        printf msm_digital_codec > /sys/bus/platform/drivers/msm_digital_codec/bind || fail
    fi
    setprop sys.leo.audio.dsp_ready 1
    trace 'DSP boot requested and digital codec bound'
    ;;

setup)
    [ "$(getprop sys.leo.audio.state)" = core_preloaded ] || exit 0
    trace 'late HAL setup entered'
    # Keep core_preloaded visible until radio setup runs the DSP hook.
    ready=0
    for i in $(seq 1 60); do
        for p in /sys/bus/msm_subsys/devices/subsys*; do
            if [ "$(cat "$p/name")" = modem ] && [ "$(cat "$p/state")" = ONLINE ]; then ready=1;break;fi
        done
        [ "$ready" = 1 ] && [ "$(getprop sys.leo.audio.dsp_ready)" = 1 ] && break
        sleep 2
    done
    [ "$ready" = 1 ] && [ "$(getprop sys.leo.audio.dsp_ready)" = 1 ] || fail
    setprop sys.leo.audio.state configuring
    # The radio setup must mount firmware and start rmt_storage before DSP boot.
    [ -r /firmware/image/modem.mdt ] || fail
    load audio_machine_int_msm8909 machine_int_dlkm
    for i in $(seq 1 20); do
        [ -r /proc/asound/card0/id ] && break
        sleep 1
    done
    [ "$(cat /proc/asound/card0/id)" = msm8909sndcard ] || fail
    fix_permissions
    setprop persist.leo.audio.enabled 1
    restart_audio
    sleep 5
    p=$(pidof android.hardware.audio@2.0-service)
    [ -n "$p" ] && grep -q '/vendor/leo-audio/lib/audio.primary.msm8909.so' /proc/$p/maps || fail
    if grep -q '/vendor/leo-audio/lib/audio-primary-generic.so' /proc/$p/maps; then fail;fi
    setprop sys.leo.audio.state active
    trace 'setup active'
    if [ "$(getprop persist.leo.audio.validated)" = 1 ]; then
        : > /data/leo-audio-trace/early-arm
        chmod 600 /data/leo-audio-trace/early-arm
        sync
        setprop persist.leo.audio.autoload 1
    fi
    ;;
disable)
    rm -f /data/leo-audio-trace/early-arm
    setprop persist.leo.audio.validated 0
    setprop persist.leo.audio.autoload 0
    setprop persist.leo.audio.enabled 0
    restart_audio
    setprop sys.leo.audio.state disabled
    ;;
*) exit 2;;
esac
