#!/system/bin/sh
# Bounded diagnostic bootstrap. Existing runtime calibration must use its loader.
# Control message 2 (big-endian u16) reports absence of runtime calibration.
# Protocol: AOSP hardware/qcom/wlan, revision 0e91137, wcnss_service.c.
set -eu
test "$(getprop ro.boot.serialno)" = 695d
test ! -e /data/misc/wifi/WCNSS_qcom_wlan_cal.bin
if ! printf '\000\002\000' > /dev/wcnss_ctrl; then
    # This stock kernel applies the message but returns ENOTTY. Require readback.
    test "$(cat /sys/module/wcnsscore/parameters/has_calibrated_data)" = 0
fi
# Opening this node triggers the stock platform driver; no malformed write.
exec 3<> /dev/wcnss_wlan
exec 3>&-
echo WCNSS_NO_RUNTIME_CAL_SIGNALLED
