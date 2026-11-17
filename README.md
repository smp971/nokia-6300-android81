# N0d3 KeyShell

N0d3 KeyShell is a native, keypad-first interface for Android 8.1 on phones
with physical keys and a small, non-touch display. It includes a Home dashboard,
an app launcher, Settings and everyday apps designed around the D-pad, OK and
the two softkeys. The **Nokia 6300 4G TA-1286** is the reference device.

`AOSP 8.1 · API 27` · `240 × 320` · `ARMv7` · `0.14.0-dev`

[Screenshots](#screenshots) · [Features](#what-works) · [Keypad controls](#keypad-controls) ·
[Architecture](#architecture) · [Installation](#installation-and-recovery) · [Build](#building-keyshell)

<a id="leo-în-imagini"></a>

## Screenshots

On-device screenshots from the Nokia 6300 4G, captured during the November UI work.
The original gallery uses shell commit `e5b26ad`; the shared-page update below
is documented in [Leo Modern UI](android81/LEO-MODERN-UI.md).
The audio and SMS examples use the built-in demos.
The boot animation is a rendered preview. Select any thumbnail to open it at
240 × 320.

| Home dashboard | App launcher | Settings |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/home.png"><img src="android81/screenshots/keyshell-2026-11-17/home.png" width="160" alt="Home with the SIM operator and technology above the clock"></a> | <a href="android81/screenshots/keyshell-2026-11-17/apps.png"><img src="android81/screenshots/keyshell-2026-11-17/apps.png" width="160" alt="Three-column launcher with colorful icons and focused app subtitle"></a> | <a href="android81/screenshots/keyshell-2026-11-17/settings.png"><img src="android81/screenshots/keyshell-2026-11-17/settings.png" width="160" alt="Settings with larger text and keypad tabs"></a> |

| Updated Clock | Shared keypad options | Android apps |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/ui-polish/clock.png"><img src="android81/screenshots/keyshell-2026-11-17/ui-polish/clock.png" width="160" alt="Clock with shared heading and three visible neighboring tab titles"></a> | <a href="android81/screenshots/keyshell-2026-11-17/ui-polish/dialogs.png"><img src="android81/screenshots/keyshell-2026-11-17/ui-polish/dialogs.png" width="160" alt="Alarm options with themed native rows and a position indicator"></a> | <a href="android81/screenshots/keyshell-2026-11-17/ui-polish/android-apps.png"><img src="android81/screenshots/keyshell-2026-11-17/ui-polish/android-apps.png" width="160" alt="Android application list with icons and common selected-row styling"></a> |

| Files · fixture | Gallery · Gaia samples | Updated player · demo |
| :---: | :---: | :---: |
| <a href="android81/screenshots/daily-ui/files.png"><img src="android81/screenshots/daily-ui/files.png" width="160" alt="Files with a storage breadcrumb, readable size and preserved selection"></a> | <a href="android81/screenshots/daily-ui/gallery.png"><img src="android81/screenshots/daily-ui/gallery.png" width="160" alt="Four larger thumbnails with wrapping names"></a> | <a href="android81/screenshots/daily-ui/player.png"><img src="android81/screenshots/daily-ui/player.png" width="160" alt="Music player with the shared native heading and softkeys"></a> |

[Updated lock screen](android81/screenshots/daily-ui/lock.png) ·
[Daily UI tests and resource measurements](android81/DAILY-UI-POLISH.md)

| Audio player · demo | Internet radio | Terminal |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/audio-player.png"><img src="android81/screenshots/keyshell-2026-11-17/audio-player.png" width="160" alt="Music player with track artwork, progress and transport controls"></a> | <a href="android81/screenshots/keyshell-2026-11-17/radio.png"><img src="android81/screenshots/keyshell-2026-11-17/radio.png" width="160" alt="Internet radio player with the România Actualități preset"></a> | <a href="android81/screenshots/keyshell-2026-11-17/terminal.png"><img src="android81/screenshots/keyshell-2026-11-17/terminal.png" width="160" alt="Terminal running pwd in a local shell"></a> |

| Global Menu | SIM settings | Boot animation |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/global-menu.png"><img src="android81/screenshots/keyshell-2026-11-17/global-menu.png" width="160" alt="Global Menu with the keypad pointer shortcut"></a> | <a href="android81/screenshots/keyshell-2026-11-17/sims.png"><img src="android81/screenshots/keyshell-2026-11-17/sims.png" width="160" alt="Mobile settings showing actual 4G and 2G on the two SIM slots"></a> | <a href="android81/screenshots/keyshell-2026-11-17/boot.gif"><img src="android81/screenshots/keyshell-2026-11-17/boot.gif" width="160" alt="Animated D-pad and N0d3 KeyShell wordmark"></a> |

<details>
<summary>More on-device screenshots</summary>

| Phone | Messages · demo | New contact |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/phone.png"><img src="android81/screenshots/keyshell-2026-11-17/phone.png" width="160" alt="Dialer with two SIM indicators, number field and favorite slots"></a> | <a href="android81/screenshots/keyshell-2026-11-17/messages.png"><img src="android81/screenshots/keyshell-2026-11-17/messages.png" width="160" alt="Sample SMS conversation with bubbles and SIM markers"></a> | <a href="android81/screenshots/keyshell-2026-11-17/contact.png"><img src="android81/screenshots/keyshell-2026-11-17/contact.png" width="160" alt="Empty contact editor with name and number fields"></a> |

| Music library | Current Media · demo | FM tuner |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/music.png"><img src="android81/screenshots/keyshell-2026-11-17/music.png" width="160" alt="Music library with artists, albums, playlists and Current Media"></a> | <a href="android81/screenshots/keyshell-2026-11-17/current-media.png"><img src="android81/screenshots/keyshell-2026-11-17/current-media.png" width="160" alt="Current Media dashboard with music and FM source tabs"></a> | <a href="android81/screenshots/keyshell-2026-11-17/fm.png"><img src="android81/screenshots/keyshell-2026-11-17/fm.png" width="160" alt="FM tuner with frequency, RDS and signal scale"></a> |

| Clock | Calendar | Calculator |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/clock.png"><img src="android81/screenshots/keyshell-2026-11-17/clock.png" width="160" alt="Clock with alarm, world time, timer and stopwatch tabs"></a> | <a href="android81/screenshots/keyshell-2026-11-17/calendar.png"><img src="android81/screenshots/keyshell-2026-11-17/calendar.png" width="160" alt="Monthly calendar with selected day"></a> | <a href="android81/screenshots/keyshell-2026-11-17/calculator.png"><img src="android81/screenshots/keyshell-2026-11-17/calculator.png" width="160" alt="Classic calculator with keypad shortcuts"></a> |

| Files | Quick Settings | Theme collections |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/files.png"><img src="android81/screenshots/keyshell-2026-11-17/files.png" width="160" alt="Storage locations for phone, documents and SD card"></a> | <a href="android81/screenshots/keyshell-2026-11-17/quick.png"><img src="android81/screenshots/keyshell-2026-11-17/quick.png" width="160" alt="Quick controls for connections, sound, brightness and mobile data"></a> | <a href="android81/screenshots/keyshell-2026-11-17/themes.png"><img src="android81/screenshots/keyshell-2026-11-17/themes.png" width="160" alt="List of theme collections including Noapte, Nordic and Solar"></a> |

| Gallery | Camera controls | Voice recorder |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/gallery.png"><img src="android81/screenshots/keyshell-2026-11-17/gallery.png" width="160" alt="Photo viewer displaying the bundled Gaia sample artwork"></a> | <a href="android81/screenshots/keyshell-2026-11-17/camera.png"><img src="android81/screenshots/keyshell-2026-11-17/camera.png" width="160" alt="Camera options for flash, timer and diagnostics"></a> | <a href="android81/screenshots/keyshell-2026-11-17/recorder.png"><img src="android81/screenshots/keyshell-2026-11-17/recorder.png" width="160" alt="Recorder in its idle state with save location"></a> |

| Games | Unit converter | About |
| :---: | :---: | :---: |
| <a href="android81/screenshots/keyshell-2026-11-17/games.png"><img src="android81/screenshots/keyshell-2026-11-17/games.png" width="160" alt="Snake, Sudoku and 15 Puzzle in the games menu"></a> | <a href="android81/screenshots/keyshell-2026-11-17/converter.png"><img src="android81/screenshots/keyshell-2026-11-17/converter.png" width="160" alt="Converter categories for length, mass, temperature and more"></a> | <a href="android81/screenshots/keyshell-2026-11-17/about.png"><img src="android81/screenshots/keyshell-2026-11-17/about.png" width="160" alt="About N0d3 KeyShell on Android 8.1 API 27"></a> |

</details>

[Earlier on-device screen atlas](archive/screenshots/atlas-2026-11-13/README.md) ·
[Extended atlas](archive/screenshots/atlas-2026-11-13/extended/README.md) ·
[Boot animation build](android81/BOOTANIMATION-SD.md)

<a id="ce-poți-face-deja"></a>
<a id="tabelul-funcțiilor"></a>

## What works

- **Home and apps.** Clock, per-SIM carrier and network technology, notification
  cards and editable shortcuts. The launcher uses one app catalog, with a
  three-column grid or a list. The focused app name appears below the heading.
  [Home and keypad guide](android81/LEO-HOME.md)
- **Settings and Android controls.** Larger text, six settings tabs, per-SIM
  details and page-specific help. The dedicated Menu key opens a global menu
  with app switching, notifications, quick settings and a D-pad pointer.
  [Android keypad integration](android81/ANDROID-KEYPAD-NAVIGATION.md)
- **Communication and text.** Dialer, in-call controls, contacts, SMS and a
  physical-keyboard IME with multi-tap, Romanian T9 and a personal dictionary.
  The shell has Romanian and English labels. [Localization](android81/LOCALIZATION.md)
- **Media and daily apps.** Music library, a dedicated Internet radio player,
  FM tuner, recorder, calendar, alarms, notes and internal or microSD file
  browsing. [Pocket apps](android81/POCKET-APPS.md)
- **Java ME games.** Open the separately installed J2ME Loader from **Games →
  Java games (J2ME)**. In **Files**, select a local JAR and choose **Open** to
  import it. [J2ME Loader 1.8.2](https://github.com/nikita36078/J2ME-Loader/releases/tag/1.8.2)
- **Appearance.** A KaiOS-inspired app grid with selected Mozilla Gaia icons
  and wallpapers, Material-inspired themes, adjustable accents and an
  editable Home layout. **Settings → Appearance → Themes** includes a preview
  of the digital lock screen. The lock screen uses the latest weather saved
  by the Weather app. [Gaia artwork credits](android81/visual-shell/assets/catalog-icons/gaia/README.md) ·
  [Wallpaper credits](android81/visual-shell/assets/wallpapers/gaia/README.md) ·
  [Material theme](android81/MATERIAL-SHELL.md)

On the development Nokia, the current build has been checked for Home, the app
grid, Settings navigation, per-SIM details and the Internet radio screen.
Telephony uses the existing AOSP/Qualcomm integration. The current modem
configuration allocates LTE to one SIM, with both SIMs available in standby.
[Index of hardware tests and limitations](android81/LEO-SHELL-AUDIT.md) ·
[Dual-slot modem investigation](android81/IMS-DUAL-SLOT.md)

<a id="din-taste"></a>

## Keypad controls

| From | Keys | Action |
| --- | --- | --- |
| Home | OK with no selection | Open the app launcher |
| Home | Up / Down | Quick Settings / notifications |
| Home | `0` / `1` | Current Media / local search |
| Home | `*` / `#` | Switch / open the current card |
| Apps | D-pad / OK | Move focus / open the highlighted app |
| Apps | `*` / `#` | Previous / next page |
| Shell or compatible Android screen | Dedicated Menu | Open the global menu when accessibility is enabled |
| Pointer enabled | D-pad / OK / `#` | Move / click / exit pointer mode |
| Internet radio | `4` / `5` / `6` | Previous preset / play or stop / next preset |

The softkey actions are shown at the bottom of each screen.

## Architecture

The low-RAM AOSP port provides device services. KeyShell handles the user
interface with native Android views and Canvas drawing. Shared list and dialog
components handle focus, softkeys and keypad navigation.

| Layer | Source |
| --- | --- |
| Dashboard, app catalog, settings and native apps | [Visual shell](android81/visual-shell) |
| Multi-tap and Romanian T9 | [Keypad IME](android81/keypad-ime) |
| Android Settings, status bar and system dialogs | [Keypad integration](android81/ANDROID-KEYPAD-NAVIGATION.md) · [System overlays](android81/system-dark) |
| TA-1286 boot, display, audio and modem | [Device tree](android81/device/nokia/leo) · [Radio integration](android81/RADIO-RECOVERY.md) |
| Recovery and one-shot microSD boot | [Leo Recovery](android81/LEO-RECOVERY.md) · [SD boot design](android81/KAIOS-SD.md) |

## Build status

| Component | Status |
| --- | --- |
| N0d3 KeyShell app update | Signed build installed and checked on the TA-1286 development handset |
| N0d3 boot artwork | 240 × 320 package installed with hash verification. The gallery shows the rendered preview. |
| 16 November unified firmware kit | Earlier AOSP baseline. The internal image passed offline checks and the matching microSD profile reached first boot on the development handset. [Package history](android81/GITHUB-PACKAGES.md) |

## Installation and recovery

For an existing compatible AOSP installation, build the apps and use the
signed APK update workflow. The project retains the Android package IDs
`ro.leo.visual` and `ro.leo.keypad` for compatible updates.
[App builds and update workflow](android81/RELEASE-APPS.md)

For a ROM installation on TA-1286, the firmware and recovery guides cover the
internal and microSD profiles. **A clean internal installation erases
userdata.** Check the exact package, device variant and recovery prerequisites
in the guide. [Installation guide](android81/INSTALL-GUIDE.md) ·
[Firmware packages](android81/GITHUB-PACKAGES.md) ·
[KaiOS restore notes](android81/FLASH-FROM-STOCK.md)

<a id="building-leo"></a>
<a id="pentru-cei-care-vor-să-construiască"></a>

## Building KeyShell

The handset uses the `aosp_leo_go-eng` product based on
`android-8.1.0_r81`, a stock-derived Linux 4.9 kernel, msm8909 and Adreno 304.
The app build uses Java 8, the API 27 platform and the pinned AOSP vCard
dependency. The full ROM build also needs the documented local firmware and
vendor inputs. [Build prerequisites](android81/REBUILD.md)

| Goal | Command or guide |
| --- | --- |
| Build the shell APK on Windows | `python -X utf8 android81/build-pocket-apps-windows.py` |
| Build the shell and the keypad IME from AOSP | `bash android81/repro/build-apps.sh` |
| Generate the boot animation | `python android81/make-bootanimation.py` |
| Run host tests | `python -m unittest discover -s android81/tests` |
| Rebuild ROM or recovery packages | [ROM and recovery bundle guide](android81/RECOVERY-BUNDLE.md) |

<a id="auditul-proiectului"></a>

## Project audit

The earlier audit records device integration, release tooling and open tests.
For a bug report, include the phone variant, ROM or APK version, the sequence
of keys pressed and the behavior observed. [Technical audit](android81/PROJECT-AUDIT-2026-11-13.md)

<a id="ce-mai-avem-de-construit"></a>

## Roadmap

- Make keypad navigation and focus consistent across shell apps and Android dialogs.
- Measure memory, startup time and battery use on the reference handset.
- Compare LTE and IMS allocation across both SIM slots with the KaiOS baseline.
- Build and verify a firmware package with the current KeyShell version.

<a id="harta-proiectului"></a>

## Documentation

[Documentation index](android81/README.md) · [Hardware and lab notes](android81/STATUS.md) ·
[Project archive](archive/README.md)
