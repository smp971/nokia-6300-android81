#!/system/bin/sh
# Called synchronously BEFORE leo_audio_preload, while zygote is still stopped.
# Seed once per newly formatted userdata. Never continuously re-arm a failure.
set -eu
umask 077
mkdir -p /data/system/leo-firstboot
if [ ! -f /data/system/leo-firstboot/audio-v1 ]; then
    mkdir -p /data/leo-audio-trace
    chmod 700 /data/leo-audio-trace
    touch /data/leo-audio-trace/early-arm
    chmod 600 /data/leo-audio-trace/early-arm
    touch /data/system/leo-firstboot/audio-v1
    sync
fi
