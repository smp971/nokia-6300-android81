# Experimental framework target. Native diagnostic boot must work first.
# Rootdir currently uses volatile /data; this is not a daily-use ROM.
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/go_defaults_512.mk)
$(call inherit-product, device/nokia/leo/device-common.mk)
PRODUCT_NAME := aosp_leo_go
PRODUCT_DEVICE := leo
PRODUCT_BRAND := Nokia
PRODUCT_MODEL := Nokia 6300 4G AOSP Go experimental
PRODUCT_MANUFACTURER := HMD Global
PRODUCT_CHARACTERISTICS := nosdcard
PRODUCT_AAPT_CONFIG := normal ldpi
PRODUCT_AAPT_PREF_CONFIG := ldpi

# First framework bring-up uses the software display stack verified on this phone.
PRODUCT_PACKAGES += \
    Launcher3Go \
    hwcomposer.leo \
    gralloc.default \
    libEGL_swiftshader \
    libGLESv1_CM_swiftshader \
    libGLESv2_swiftshader \
    android.hardware.graphics.composer@2.1-service \
    android.hardware.graphics.composer@2.1-impl \
    android.hardware.graphics.allocator@2.0-service \
    android.hardware.graphics.allocator@2.0-impl \
    android.hardware.graphics.mapper@2.0-impl \
    android.hardware.audio@2.0-service \
    android.hardware.audio@2.0-impl \
    android.hardware.audio.effect@2.0-impl
PRODUCT_PACKAGES += \
    android.hardware.keymaster@3.0-impl \
    android.hardware.keymaster@3.0-service
PRODUCT_PROPERTY_OVERRIDES += \
    ro.setupwizard.mode=DISABLED \
    ro.config.low_ram=true \
    dalvik.vm.heapstartsize=8m \
    dalvik.vm.heaptargetutilization=0.75 \
    dalvik.vm.heapminfree=512k \
    dalvik.vm.heapmaxfree=8m
