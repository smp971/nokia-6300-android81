#!/system/bin/sh
PATH=/system/bin
setprop sys.leo.ims.native_ready 0
mkdir -p /dev/leo-ims
chmod 0700 /dev/leo-ims
while [ "$(getprop sys.leo.ims.native)" = 1 ]; do
    slot=$(getprop sys.leo.ims.slot)
    case "$slot" in 0|1) ;; *) sleep 5; continue;; esac
    socket=/dev/socket/qmux_radio/rild_ims$slot
    saved=/dev/leo-ims/socket-mode-$slot
    if [ -S "$socket" ]; then
        if [ ! -f "$saved" ]; then stat -c %a "$socket" > "$saved"; fi
        # Reapply after a RIL recreates its socket; leave the other SIM alone.
        chmod 0660 "$socket"
        if [ "$(getprop init.svc.leo_ims_qmi)" = running ] &&
           [ "$(getprop init.svc.leo_ims_data)" = running ] &&
           [ -S /dev/socket/ims_qmid ] && [ -S /dev/socket/ims_datad ]; then
            setprop sys.leo.ims.native_ready 1
        else
            setprop sys.leo.ims.native_ready 0
        fi
    else
        setprop sys.leo.ims.native_ready 0
    fi
    sleep 5
done
