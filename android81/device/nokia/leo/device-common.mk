# Shared Nokia hardware bring-up configuration.
DEVICE_PACKAGE_OVERLAYS += device/nokia/leo/overlay
PRODUCT_COPY_FILES += \
    device/nokia/leo/init.leo.hwui.rc:system/etc/init/leo-hwui-full-frame.rc \
    device/nokia/leo/init.leo.haptic.rc:system/etc/init/leo-haptic.rc \
    device/nokia/leo/leo-connectivity.xml:system/etc/permissions/leo-connectivity.xml \
    device/nokia/leo/wpa_supplicant.conf:system/etc/wifi/wpa_supplicant.conf
PRODUCT_COPY_FILES += \
    device/nokia/leo/prebuilt/kernel:kernel \
    device/nokia/leo/rootdir/init.qcom.rc:root/init.qcom.rc \
    device/nokia/leo/rootdir/fstab.qcom:root/fstab.qcom \
    device/nokia/leo/rootdir/ueventd.qcom.rc:root/ueventd.qcom.rc \
    device/nokia/leo/rootdir/init.leo.usb.rc:root/init.leo.usb.rc \
    device/nokia/leo/matrix_keypad.kl:system/usr/keylayout/matrix_keypad.kl \
    device/nokia/leo/gpio-keys.kl:system/usr/keylayout/gpio-keys.kl \
    device/nokia/leo/qpnp_pon.kl:system/usr/keylayout/qpnp_pon.kl
# The diagnostic build has no UI to approve an ADB authentication prompt.
ifneq ($(wildcard device/nokia/leo/local/adb_keys),)
PRODUCT_COPY_FILES += device/nokia/leo/local/adb_keys:root/adb_keys
endif
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.config.low_ram=true \
    ro.adb.secure=1 \
    persist.sys.usb.config=adb
PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.lcd_density=120
