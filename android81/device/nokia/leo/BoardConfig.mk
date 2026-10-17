# EXPERIMENTAL: diagnostic target, not yet build/boot validated.
# Partition sizes are from the PUBLIC dump; verify against the actual target.
TARGET_ARCH := arm
TARGET_ARCH_VARIANT := armv7-a-neon
TARGET_CPU_VARIANT := cortex-a7
TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_BOARD_PLATFORM := msm8909
TARGET_BOOTLOADER_BOARD_NAME := msm8909
TARGET_NO_BOOTLOADER := true
TARGET_NO_RECOVERY := true
TARGET_USES_64_BIT_BINDER := false
TARGET_PREBUILT_KERNEL := device/nokia/leo/prebuilt/kernel
BOARD_KERNEL_BASE := 0x80000000
BOARD_KERNEL_PAGESIZE := 2048
# Android image packaging alignment (64 boot pages), not a measured eMMC erase size.
BOARD_FLASH_BLOCK_SIZE := 131072
BOARD_KERNEL_OFFSET := 0x00008000
BOARD_RAMDISK_OFFSET := 0x01000000
BOARD_TAGS_OFFSET := 0x00000100
BOARD_KERNEL_CMDLINE := console=ttyMSM0,115200,n8 androidboot.console=ttyMSM0 androidboot.hardware=qcom msm_rtb.filter=0x237 ehci-hcd.park=3 androidboot.bootdevice=7824900.sdhci lpm_levels.sleep_disabled=1 earlyprintk earlycon=msm_hsl_uart,0x78B0000
# Temporary engineering policy for bring-up; not a production security setting.
BOARD_KERNEL_CMDLINE += androidboot.selinux=permissive enforcing=0
BOARD_MKBOOTIMG_ARGS += --ramdisk_offset $(BOARD_RAMDISK_OFFSET)
BOARD_MKBOOTIMG_ARGS += --tags_offset $(BOARD_TAGS_OFFSET)
TARGET_USERIMAGES_USE_EXT4 := true
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 838860800
BOARD_BOOTIMAGE_PARTITION_SIZE := 33554432
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 33554432
BOARD_CACHEIMAGE_PARTITION_SIZE := 209715200
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
# Userdata size/encryption policy deliberately require target validation.
# TODO: validate init/USB and Binder ABI, SELinux/VINTF, graphics HAL linkage,
# proprietary dependencies and Android input behavior.

# The framebuffer HWC completes presentation synchronously and returns no retire fences.
# Keep DispSync sampling vsync instead of waiting for absent present fences.
TARGET_RUNNING_WITHOUT_SYNC_FRAMEWORK := true
