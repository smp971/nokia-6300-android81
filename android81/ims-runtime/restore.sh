#!/system/bin/sh
PATH=/system/bin
for slot in 0 1; do
    saved=/dev/leo-ims/socket-mode-$slot
    socket=/dev/socket/qmux_radio/rild_ims$slot
    if [ -f "$saved" ] && [ -S "$socket" ]; then
        mode=$(cat "$saved")
        case "$mode" in
            [0-7][0-7][0-7]|[0-7][0-7][0-7][0-7]) chmod "$mode" "$socket";;
        esac
    fi
done
setprop sys.ims.QMI_DAEMON_STATUS ''
setprop sys.ims.DATA_DAEMON_STATUS ''
setprop sys.leo.ims.native_ready 0
