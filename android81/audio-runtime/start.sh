#!/system/bin/sh
set -eu
base=/vendor/leo-audio
restart_audio() {
    setprop ctl.stop audioserver
    setprop ctl.stop audio-hal-2-0
    sleep 1
    setprop ctl.start audio-hal-2-0
    sleep 1
    setprop ctl.start audioserver
}
fail() {
    setprop persist.leo.audio.enabled 0
    setprop sys.leo.audio.state failed
    restart_audio
    exit 1
}
load() {
    if ! grep -q "^$2 " /proc/modules; then
        insmod "$base/modules/$1.ko" || fail
        sleep 1
    fi
}
case "$1" in
setup)
    [ "$(getprop persist.leo.audio.autoload)" = 1 ] || exit 0
    # Clear the arm before touching kernel modules. A failed boot will not loop.
    setprop persist.leo.audio.autoload 0
    setprop sys.leo.audio.state waiting
    sleep 2
    ready=0
    for i in $(seq 1 90); do
        for p in /sys/bus/msm_subsys/devices/subsys*; do
            if [ "$(cat "$p/name")" = modem ] && [ "$(cat "$p/state")" = ONLINE ]; then ready=1;break;fi
        done
        [ "$ready" = 1 ] && [ "$(getprop sys.boot_completed)" = 1 ] && break
        sleep 2
    done
    [ "$ready" = 1 ] && [ "$(getprop sys.boot_completed)" = 1 ] || fail
    setprop sys.leo.audio.state loading
    load audio_q6_notifier q6_notifier_dlkm
    load audio_apr apr_dlkm
    load audio_adsp_loader adsp_loader_dlkm
    printf 1 > /sys/kernel/boot_adsp/boot
    sleep 3
    load audio_q6 q6_dlkm
    load audio_wcd_core wcd_core_dlkm
    load audio_wcd9xxx wcd9xxx_dlkm
    load audio_mbhc mbhc_dlkm
    load audio_stub stub_dlkm
    load audio_platform platform_dlkm
    load audio_digital_cdc digital_cdc_dlkm
    load audio_analog_cdc analog_cdc_dlkm
    sleep 3
    load audio_machine_int_msm8909 machine_int_dlkm
    [ "$(cat /proc/asound/card0/id)" = msm8909sndcard ] || fail
    chown system:audio /dev/snd/*
    chmod 660 /dev/snd/*
    restorecon -R /dev/snd
    chown system:audio /dev/msm_audio_cal
    chmod 660 /dev/msm_audio_cal
    restorecon /dev/msm_audio_cal
    setprop persist.leo.audio.enabled 1
    restart_audio
    sleep 5
    p=$(pidof android.hardware.audio@2.0-service)
    [ -n "$p" ] && grep -q '/vendor/leo-audio/lib/audio.primary.msm8909.so' /proc/$p/maps || fail
    if grep -q '/vendor/leo-audio/lib/audio-primary-generic.so' /proc/$p/maps; then fail;fi
    setprop sys.leo.audio.state active
    if [ "$(getprop persist.leo.audio.validated)" = 1 ]; then setprop persist.leo.audio.autoload 1;fi
    ;;
disable)
    setprop persist.leo.audio.validated 0
    setprop persist.leo.audio.autoload 0
    setprop persist.leo.audio.enabled 0
    restart_audio
    setprop sys.leo.audio.state disabled
    ;;
*) exit 2;;
esac
