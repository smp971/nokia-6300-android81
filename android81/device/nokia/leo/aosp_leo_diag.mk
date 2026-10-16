# Diagnostic native userspace: no Java framework or telephony at this stage.
$(call inherit-product, $(SRC_TARGET_DIR)/product/embedded.mk)
PRODUCT_NAME := aosp_leo_diag
PRODUCT_DEVICE := leo
PRODUCT_BRAND := Nokia
PRODUCT_MODEL := Nokia 6300 4G Android diagnostic
PRODUCT_MANUFACTURER := HMD Global
$(call inherit-product, device/nokia/leo/device-common.mk)
