# Bananui on Nokia 6300 4G · SD boot experiment

Target hardware is the Nokia 6300 4G TA-1286 (`nokia-leo`, MSM8909,
ARMv7). The development phone reports `androidboot.hw.name=leo` and currently
has a 32 GB microSD in use. This experiment requires a separate microSD.

## Image inputs

- The [postmarketOS port](https://gitlab.com/postmarketOS/pmaports/-/merge_requests/3768)
  includes a mainline kernel device tree and modem startup for `nokia-leo`.
  Bananui was a separate Wayland UI with its own
  [shell](https://git.abscue.de/obp/bananui/shell) and
  [packaging recipe](https://gitlab.com/postmarketOS/pmaports/-/blob/1f86677848a69f26ba60ec46b2a58b7283e7dead/main/postmarketos-ui-bananui/APKBUILD).
  The checked-out shell is `e0c052fa6db7823b9878c8c6377c1d03dc49c334`
  (0.2.0). Its session starts Phoc, and its build needs `bananui2`,
  `libcallaudio`, `libfeedback` and Wayland. Current pmaports dropped the UI
  in December 2025. The last parent before removal is
  `954b75f585eac54e3c63e653141149d77d88712d`. Use that revision to locate a
  compatible Alpine snapshot, or port the Bananui packages to current Alpine.
  Dependency resolution is still pending. [Removal change](https://gitlab.postmarketos.org/postmarketOS/pmaports/-/merge_requests/7498)
- The [lk2nd device list](https://github.com/msm8916-mainline/lk2nd/blob/main/Documentation/devices.md)
  lists this phone under **MSM8909**. `lk2nd-msm8909.img` from
  [release 23.1](https://github.com/msm8916-mainline/lk2nd/releases/tag/23.1)
  was downloaded and verified against the release API. SHA-256 is
  `af5090d6d97c7ae3364d5bff87da7ea0b26e51da25317a6d763daaf67f5adad8`.
  The upstream device entry matches the `leo` boot parameter.
- Build and export a combined raw image on a Linux host with
  [pmbootstrap](https://docs.postmarketos.org/pmbootstrap/main/usage.html),
  targeting `nokia-leo` and selecting Bananui in the compatible checkout.
  Inspect the boot partition for an lk2nd-readable filesystem, the
  `extlinux.conf` default entry, kernel, initramfs and `nokia-leo` DTB.
  Verify that the initramfs selects the test card's root filesystem. Write the
  image only after identifying the separate card by size and disk identity.

## Boot and acceptance gates

Leo Recovery's [KaiOS selector](KAIOS-SD.md) reuses the existing kernel and
switches userspace. postmarketOS needs its own kernel. The current kernel lacks
kexec, so that selector cannot start this image. lk2nd can load an SD image via
[extlinux](https://github.com/msm8916-mainline/lk2nd/blob/main/Documentation/boot.md),
but the stock loader's ability to run lk2nd temporarily from USB has yet to be
established on this unit. The usual lk2nd installation writes the boot partition.
Require a validated one-shot boot route and verified boot/recovery backups
before trying the card. Leave the working boot and recovery in place while that
route is unresolved.

Read-only checks matched the live boot partition to
`output/rom-snapshots/20261027-232615/boot.img` and the live recovery partition
to `output/leo-recovery/leo-recovery-0.2.img` by SHA-256. These backups stay local.

For the first successful startup, check the 240×320 display, D-pad, OK, both
softkeys, Back and suspend/resume. Record input-to-render timing and whether a
normal restart returns to Leo. Test modem, voice audio and IMS separately.
An [upstream report on the related Nokia 8000 4G](https://gitlab.com/postmarketOS/pmaports/-/issues/2865)
describes a black screen with Bananui from SD, so a visible menu is an explicit
acceptance check.

The bootloader candidate is verified locally. The OS image has yet to be built
and no Bananui boot was attempted.
