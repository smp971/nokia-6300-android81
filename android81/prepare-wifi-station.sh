#!/system/bin/sh
set -eu
test "$(getprop ro.boot.serialno)" = 695d
test "$(getprop init.svc.wpa_supplicant)" != running
config=/data/misc/wifi/wpa_supplicant.conf
test -f "$config"
if [ ! -e "$config.leo-before-station" ]; then
    cp -p "$config" "$config.leo-before-station"
fi
if grep -q '^p2p_disabled=' "$config"; then
    sed -i 's/^p2p_disabled=.*/p2p_disabled=1/' "$config"
else
    printf '\np2p_disabled=1\n' >> "$config"
fi
chown wifi:wifi "$config"
chmod 0660 "$config"
restorecon "$config"
