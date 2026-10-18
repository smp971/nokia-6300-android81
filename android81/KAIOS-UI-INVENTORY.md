# Inventarul interfeței KaiOS din dump

Data analizei: **19 octombrie 2026**. Scanare statică, fără instalare sau modificări pe telefon.

Baza extrasă declară **KaiOS 2.5.4**, Gecko **48.0a2** în `application.ini`.
Registrul conține **64 de aplicații**, dintre care **63** au `application.zip` local.
Pachetele ocupă aproximativ **73.4 MiB** comprimat; aceasta nu este memoria RAM necesară rulării.

## Inventar complet al registrului

API = numărul denumirilor de API specifice detectate în JS/HTML/CSS. Permisiunile provin din manifest.
Zero API detectate nu înseamnă compatibilitate Android: codul poate utiliza aliasuri, worker-e, IPC sau biblioteci dinamice.

| Aplicație | ID | Tip / rol | ZIP MiB | Permisiuni | API |
| --- | --- | --- | ---: | ---: | ---: |
| anti-theft | antitheft.gaiamobile.org | certified / system | 0.21 | 11 | 11 |
| Assistant | assistant.google.com | certified | 0.52 | 18 | 13 |
| Bluetooth Manager | bluetooth.gaiamobile.org | certified / system | 0.36 | 7 | 6 |
| Calculator | calculator.gaiamobile.org | certified | 0.11 | 2 | 2 |
| Calendar | calendar.gaiamobile.org | certified | 1.00 | 16 | 10 |
| Callscreen | callscreen.gaiamobile.org | certified / system | 0.72 | 20 | 20 |
| Camera | camera.gaiamobile.org | certified | 0.42 | 14 | 10 |
| Clock | clock.gaiamobile.org | certified | 2.38 | 8 | 7 |
| Call Log | communications.gaiamobile.org | certified | 0.43 | 14 | 11 |
| Contact | contact.gaiamobile.org | certified | 0.89 | 14 | 15 |
| Crossy Road | crossyroad.gaiamobile.org | certified | 0.54 | 5 | 6 |
| Customization | customization.gaiamobile.org | certified / system | 0.27 | 8 | 9 |
| Danger Dash | dangerdash-premium.gaiamobile.org | certified | 3.14 | 6 | 4 |
| Danger Dash | dangerdash.gaiamobile.org | certified | 3.13 | 6 | 4 |
| Default Theme | default_theme.gaiamobile.org | certified / theme | 0.00 | 0 | 0 |
| Doodle Jump | doodlejump.gaiamobile.org | certified | 0.47 | 5 | 6 |
| Downloads | download.gaiamobile.org | certified / system | 0.34 | 4 | 9 |
| E-Mail | email.gaiamobile.org | certified | 1.64 | 15 | 16 |
| EmergencyCall | emergency-call.gaiamobile.org | certified / system | 0.22 | 7 | 11 |
| English with Oxford | englishwithoxford.gaiamobile.org | certified | 0.40 | 4 | 5 |
| Engmode | engmode.gaiamobile.org | certified / system | 0.26 | 21 | 16 |
| File Manager | filemanager.gaiamobile.org | certified | 0.35 | 4 | 6 |
| FM Radio | fm.gaiamobile.org | certified | 0.37 | 13 | 11 |
| Fota | fota.gaiamobile.org | certified / system | 0.30 | 6 | 8 |
| FTU | ftu.gaiamobile.org | certified / system | 0.56 | 6 | 9 |
| Gallery | gallery.gaiamobile.org | certified | 0.65 | 10 | 10 |
| KaiOS Pay | kaios-pay.kaiostech.com | certified / system | 0.43 | 18 | 10 |
| KaiOS-Store | kaios-plus.kaiostech.com | certified | 0.58 | 15 | 11 |
| KaiWeather | kaios.kaiweather.net | web | 0.60 | 1 | 6 |
| KaiNews | kaios.news.net | privileged | 0.45 | 2 | 5 |
| QR Reader | kaios.qrreader.org | privileged | 0.47 | 1 | 10 |
| WhatsApp | kaios.whatsapp.net | certified | 0.02 | 25 | 1 |
| Built-in Keyboard | keyboard.gaiamobile.org | certified / input | 0.23 | 6 | 6 |
| Launcher | launcher.gaiamobile.org | certified / homescreen | 1.17 | 19 | 17 |
| Login Pages | loginpages.gaiamobile.org | certified / system | 0.20 | 6 | 5 |
| LogManager | logmanager.gaiamobile.org | certified / system | 0.03 | 3 | 3 |
| Facebook | m.facebook.com | privileged | 0.33 | 11 | 6 |
| MMI Test | mmitest.gaiamobile.org | certified / system | 2.47 | 19 | 13 |
| Music | music.gaiamobile.org | certified | 0.41 | 11 | 8 |
| Network Alerts | network-alerts.gaiamobile.org | certified / system | 0.34 | 14 | 9 |
| Nitro Street Run 2 | nitrostreetrun-premium.gaiamobile.org | certified | 2.42 | 6 | 1 |
| Nitro Street Run 2 | nitrostreetrun.gaiamobile.org | certified | 2.42 | 6 | 1 |
| Note | notes.gaiamobile.org | certified | 0.35 | 6 | 7 |
| Racing Attack | racingroad.gaiamobile.org | certified | 0.60 | 5 | 6 |
| Real Football Runner | realfootballrunner-premium.gaiamobile.org | certified | 1.94 | 5 | 6 |
| Real Football Runner | realfootballrunner.gaiamobile.org | certified | 1.94 | 5 | 6 |
| Ringtones | ringtones.gaiamobile.org | certified / system | 3.91 | 7 | 8 |
| Internet | search.gaiamobile.org | certified / search | 0.61 | 12 | 11 |
| Google Search | search.google.com | certified | 0.01 | 1 | 1 |
| Settings | settings.gaiamobile.org | certified | 7.17 | 42 | 28 |
| Siberian Strike | siberianstrike-premium.gaiamobile.org | certified | 3.26 | 5 | 1 |
| Siberian Strike | siberianstrike.gaiamobile.org | certified | 3.26 | 5 | 1 |
| Messages | sms.gaiamobile.org | certified | 1.00 | 18 | 19 |
| Snake | snake-premium.gaiamobile.org | certified | 1.82 | 5 | 0 |
| Snake | snake.gaiamobile.org | certified | 1.82 | 5 | 0 |
| Recorder | soundrecorder.gaiamobile.org | certified | 0.52 | 12 | 9 |
| stk | stk.gaiamobile.org | certified | 0.21 | 5 | 5 |
| System | system.gaiamobile.org | certified / system | 8.37 | 68 | 34 |
| Unit Converter | unitconverter.gaiamobile.org | certified | 0.31 | 1 | 3 |
| Video | video.gaiamobile.org | certified | 0.44 | 11 | 10 |
| Wallpaper | wallpaper.gaiamobile.org | certified / system | 3.37 | 4 | 8 |
| WAP Push manager | wappush.gaiamobile.org | certified / system | 0.28 | 6 | 9 |
| YouTubeApp | youtube.com | privileged | 0.00 | 0 | 0 |
| Maps | {1fdf3038-1f67-4be1-99a4-b30ac7c48790} | web | lipsește | 4 | 0 |

## Fundația interfeței și dependențe observate

### system.gaiamobile.org

**API detectate:** `MozActivity`, `SettingsHelper`, `SettingsListener`, `battery`, `engmodeExtension`, `getDeviceStorage`, `getDeviceStorages`, `mozAlarms`, `mozApps`, `mozAudioChannelManager`, `mozBluetooth`, `mozCellBroadcast`, `mozChromeNotifications`, `mozContact`, `mozContacts`, `mozDownloadManager`, `mozHour12`, `mozIccManager`, `mozId`, `mozInputMethod`, `mozL10n`, `mozMobileConnection`, `mozMobileConnections`, `mozMobileMessage`, `mozNetworkStats`, `mozNfc`, `mozPermissionSettings`, `mozPower`, `mozSetMessageHandler`, `mozSettings`, `mozTCPSocket`, `mozTelephony`, `mozVoicemail`, `mozWifiManager`.

**Permisiuni declarate:** `OmaService`, `account-manager`, `alarms`, `audio-capture`, `audio-channel-alarm`, `audio-channel-content`, `audio-channel-notification`, `audio-channel-publicnotification`, `audio-channel-ringer`, `audio-channel-system`, `audio-channel-telephony`, `background-sensors`, `before-after-keyboard-event`, `bluetooth`, `browser`, `camera`, `cellbroadcast`, `cloud-authorization`, `contacts`, `customization`, `datacall`, `desktop-notification`, `device-storage:apps`, `device-storage:music`, `device-storage:pictures`, `device-storage:sdcard`, `device-storage:videos`, `dfc`, `donglemanager`, `downloads`, `embed-apps`, `engmode-extension`, `external-api`, `feature-detection`, `flashlight`, `flip`, `fmradio`, `fota`, `geolocation`, `idle`, `input-manage`, `kaios-accounts`, `keyboard-event-generator`, `mobileconnection`, `network-events`, `networkstats-manage`, `nfc`, `nfc-manager`, `nfc-share`, `permissions`, `power`, `secureelement-manage`, `serviceworker`, `settings`, `sms`, `softkey`, `speaker-control`, `storage`, `system-app-only-audio-channels-in-app`, `systemXHR`, `tcp-socket`, `telephony`, `themeable`, `video-capture`, `voicemail`, `volumemanager`, `webapps-manage`, `wifi-manage`.

**Canale inter-aplicații declarate:** 20. **Fișiere în pachet:** 816.

### launcher.gaiamobile.org

**API detectate:** `MozActivity`, `SettingsListener`, `engmodeExtension`, `mozApps`, `mozContacts`, `mozHour12`, `mozIccManager`, `mozId`, `mozL10n`, `mozMobileConnections`, `mozMobileMessage`, `mozPower`, `mozSetMessageHandler`, `mozSettings`, `mozTelephony`, `mozVoicemail`, `mozWifiManager`.

**Permisiuni declarate:** `account-manager`, `audio-channel-notification`, `contacts`, `customization`, `datacall`, `engmode-extension`, `feature-detection`, `flashlight`, `kaios-accounts`, `mobileconnection`, `power`, `settings`, `softkey`, `storage`, `telephony`, `themeable`, `voicemail`, `webapps-manage`, `wifi-manage`.

**Canale inter-aplicații declarate:** 0. **Fișiere în pachet:** 173.

### default_theme.gaiamobile.org

**API detectate:** Niciunul din tiparele căutate..

**Permisiuni declarate:** Niciuna..

**Canale inter-aplicații declarate:** 0. **Fișiere în pachet:** 4.

### keyboard.gaiamobile.org

**API detectate:** `MozActivity`, `getDeviceStorage`, `mozApps`, `mozInputMethod`, `mozL10n`, `mozSettings`.

**Permisiuni declarate:** `device-storage:sdcard`, `feature-detection`, `input`, `input-manage`, `settings`, `themeable`.

**Canale inter-aplicații declarate:** 0. **Fișiere în pachet:** 89.

### settings.gaiamobile.org

**API detectate:** `MozActivity`, `MozSettingsEvent`, `SettingsHelper`, `SettingsListener`, `battery`, `engmodeExtension`, `getDeviceStorage`, `getDeviceStorages`, `mozApps`, `mozAudioChannelManager`, `mozBluetooth`, `mozContacts`, `mozDownloadManager`, `mozHour12`, `mozIccManager`, `mozId`, `mozL10n`, `mozMobileConnection`, `mozMobileConnections`, `mozMobileMessage`, `mozNfc`, `mozPermissionSettings`, `mozPower`, `mozSetMessageHandler`, `mozSettings`, `mozTelephony`, `mozTime`, `mozWifiManager`.

**Permisiuni declarate:** `OmaService`, `account-manager`, `audio-channel-alarm`, `audio-channel-content`, `audio-channel-notification`, `bluetooth`, `browser`, `contacts`, `customization`, `datacall`, `desktop-notification`, `device-storage:apps`, `device-storage:apps-storage`, `device-storage:music`, `device-storage:pictures`, `device-storage:sdcard`, `device-storage:videos`, `downloads`, `embed-apps`, `feature-detection`, `fota`, `idle`, `kaios-accounts`, `mobileconnection`, `nfc`, `nfc-manager`, `open-remote-window`, `permissions`, `power`, `secureelement-manage`, `settings`, `sms`, `softkey`, `speaker-control`, `storage`, `systemXHR`, `telephony`, `themeable`, `time`, `voicemail`, `webapps-manage`, `wifi-manage`.

**Canale inter-aplicații declarate:** 0. **Fișiere în pachet:** 764.

### callscreen.gaiamobile.org

**API detectate:** `MozActivity`, `SettingsListener`, `battery`, `getDeviceStorage`, `mozApps`, `mozAudioChannelManager`, `mozBluetooth`, `mozCameras`, `mozContacts`, `mozHour12`, `mozIccManager`, `mozL10n`, `mozMobileConnections`, `mozMobileMessage`, `mozPhoneNumberService`, `mozPower`, `mozSetMessageHandler`, `mozSettings`, `mozTelephony`, `mozVoicemail`.

**Permisiuni declarate:** `attention`, `audio-capture`, `audio-channel-telephony`, `bluetooth`, `camera`, `contacts`, `device-storage:sdcard`, `feature-detection`, `flip`, `geolocation-noprompt`, `mobileconnection`, `phonenumberservice`, `power`, `settings`, `sms`, `storage`, `telephony`, `themeable`, `voicemail`, `volumemanager`.

**Canale inter-aplicații declarate:** 0. **Fișiere în pachet:** 164.

### communications.gaiamobile.org

**API detectate:** `MozActivity`, `mozApps`, `mozContacts`, `mozHour12`, `mozIccManager`, `mozL10n`, `mozMobileConnections`, `mozSetMessageHandler`, `mozSettings`, `mozTelephony`, `mozVoicemail`.

**Permisiuni declarate:** `audio-channel-content`, `bluetooth`, `contacts`, `desktop-notification`, `device-storage:sdcard`, `feature-detection`, `mobileconnection`, `phonenumberservice`, `settings`, `softkey`, `storage`, `telephony`, `themeable`, `voicemail`.

**Canale inter-aplicații declarate:** 0. **Fișiere în pachet:** 98.

### contact.gaiamobile.org

**API detectate:** `MozActivity`, `getDeviceStorage`, `getDeviceStorages`, `mozApps`, `mozContact`, `mozContacts`, `mozHour12`, `mozIccManager`, `mozL10n`, `mozMobileConnections`, `mozMobileMessage`, `mozSetMessageHandler`, `mozSettings`, `mozTelephony`, `mozVoicemail`.

**Permisiuni declarate:** `account-manager`, `contacts`, `customization`, `device-storage:apps`, `device-storage:sdcard`, `feature-detection`, `mobileconnection`, `settings`, `softkey`, `systemXHR`, `telephony`, `themeable`, `voicemail`, `webapps-manage`.

**Canale inter-aplicații declarate:** 0. **Fișiere în pachet:** 140.

### sms.gaiamobile.org

**API detectate:** `MozActivity`, `SettingsListener`, `battery`, `engmodeExtension`, `getDeviceStorage`, `mozAlarms`, `mozApps`, `mozAudioChannelManager`, `mozContacts`, `mozHasPendingMessage`, `mozHour12`, `mozIccManager`, `mozL10n`, `mozMobileConnections`, `mozMobileMessage`, `mozPhoneNumberService`, `mozSetMessageHandler`, `mozSettings`, `mozTelephony`.

**Permisiuni declarate:** `alarms`, `audio-channel-notification`, `contacts`, `customization`, `desktop-notification`, `device-storage:apps`, `engmode-extension`, `feature-detection`, `geolocation-noprompt`, `mobileconnection`, `phonenumberservice`, `settings`, `sms`, `softkey`, `storage`, `telephony`, `themeable`, `webapps-manage`.

**Canale inter-aplicații declarate:** 0. **Fișiere în pachet:** 242.

## Servicii native declarate în ramdisk

Au fost găsite **182 declarații service**, cu **174 de nume distincte**, în configurațiile RC extrase.
Acest inventar include variante condiționale și configurații pentru alte proiecte prezente în ramdisk. Nu afirmă că toate sunt importate sau active pe Leo.
Lista indică executabilul, fără argumente sau date de configurare. Nu copiem fișierele init originale peste Android.

| Serviciu declarat | Executabil | Fișier RC |
| --- | --- | --- |
| gonksched | /system/bin/gonksched | init.b2g.rc |
| fakeappops | /system/bin/fakeappops | init.b2g.rc |
| b2g | /system/bin/b2g.sh | init.b2g.rc |
| nfcd | /system/bin/nfcd | init.b2g.rc |
| radvd | /system/bin/radvd | init.b2g.rc |
| api-daemon | /system/bin/api-daemon.sh | init.b2g.rc |
| updater-daemon | /system/bin/updater-daemon.sh | init.b2g.rc |
| metrics-daemon | /system/kaios/metrics_daemon | init.b2g.rc |
| bluetoothd | /system/bin/bluetoothd | init.bluetooth.rc |
| lmlogcatservice | /system/bin/sh | init.logmanager.rc |
| lmgeckologsvc | /system/bin/sh | init.logmanager.rc |
| lmdmesgservice | /system/bin/sh | init.logmanager.rc |
| lmqxdmservice | /system/bin/sh | init.logmanager.rc |
| lmtcpdpservice | /system/bin/sh | init.logmanager.rc |
| lmitlogservice | /system/bin/sh | init.logmanager.rc |
| trace_util | /system/bin/trace_util | init.project.rc |
| fih_fota | /system/bin/nvdiag_client | init.project.rc |
| nvdiag_daemon | /system/bin/nvdiag_daemon | init.project.rc |
| diag_get_daemon | /system/bin/diag_get_daemon | init.project.rc |
| restore_sfs | /system/bin/restore_sfs | init.project.rc |
| nv_backup | /system/bin/backup.sh | init.project.rc |
| diag_get_daemon | /system/bin/diag_get_daemon | init.project.rc |
| slate_diag | /system/bin/slate_diag | init.project.rc |
| up_ver | /system/bin/up_ver | init.project.rc |
| tct_diag | /system/bin/tct_diag | init.project.rc |
| tctd | /system/bin/tctd | init.project.rc |
| bgservice | /system/bin/sh | init.project.rc |
| jrdlogcatservice | /system/bin/sh | init.project.rc |
| jrdgeckologsvc | /system/bin/sh | init.project.rc |
| jrddmesgservice | /system/bin/sh | init.project.rc |
| jrdqxdmservice | /system/bin/sh | init.project.rc |
| jrdtcpdpservice | /system/bin/sh | init.project.rc |
| jrditlogservice | /system/bin/sh | init.project.rc |
| read_fota | /system/bin/sh | init.project.rc |
| fastmmi | /system/bin/mmi | init.qcom.factory.rc |
| srvmag_ffbm | /system/bin/servicemanager | init.qcom.factory.rc |
| surfaceflinger | /system/bin/surfaceflinger | init.qcom.factory.rc |
| lmlogcatservice | /system/bin/sh | init.qcom.rc |
| lmgeckologsvc | /system/bin/sh | init.qcom.rc |
| lmdmesgservice | /system/bin/sh | init.qcom.rc |
| lmqxdmservice | /system/bin/sh | init.qcom.rc |
| lmtcpdpservice | /system/bin/sh | init.qcom.rc |
| lmitlogservice | /system/bin/sh | init.qcom.rc |
| perfd | /system/vendor/bin/perfd | init.qcom.rc |
| qcomsysd | /system/bin/qcom-system-daemon | init.qcom.rc |
| ssr_setup | /system/bin/ssr_setup | init.qcom.rc |
| ss_ramdump | /system/bin/subsystem_ramdump | init.qcom.rc |
| qcom-debug | /system/bin/sh | init.qcom.rc |
| qcom-c_core-sh | /system/bin/sh | init.qcom.rc |
| qcom-c_main-sh | /system/bin/sh | init.qcom.rc |
| cnd | /system/bin/cnd | init.qcom.rc |
| dpmd | /system/bin/dpmd | init.qcom.rc |
| irsc_util | /system/bin/irsc_util | init.qcom.rc |
| rmt_storage | /system/bin/rmt_storage | init.qcom.rc |
| rfs_access | /system/bin/rfs_access | init.qcom.rc |
| tftp_server | /system/bin/tftp_server | init.qcom.rc |
| start_hci_filter | /system/bin/wcnss_filter | init.qcom.rc |
| config_bt_addr | /system/bin/btnvtool | init.qcom.rc |
| config_bluetooth | /system/bin/sh | init.qcom.rc |
| hciattach | /system/bin/sh | init.qcom.rc |
| hciattach_ath3k | /system/bin/sh | init.qcom.rc |
| bt-dun | /system/bin/dun-server | init.qcom.rc |
| bt-sap | /system/bin/sapd | init.qcom.rc |
| btsnoop | /system/bin/btsnoop | init.qcom.rc |
| ftmd | /system/bin/logwrapper | init.qcom.rc |
| bridgemgrd | /system/bin/bridgemgrd | init.qcom.rc |
| port-bridge | /system/bin/port-bridge | init.qcom.rc |
| qmiproxy | /system/bin/qmiproxy | init.qcom.rc |
| qmuxd | /system/bin/qmuxd | init.qcom.rc |
| netmgrd | /system/bin/netmgrd | init.qcom.rc |
| ipacm-diag | /system/bin/ipacm-diag | init.qcom.rc |
| ipacm | /system/bin/ipacm | init.qcom.rc |
| qti | /system/vendor/bin/qti | init.qcom.rc |
| sensors | /system/bin/sensors.qcom | init.qcom.rc |
| btwlancoex | /system/bin/sh | init.qcom.rc |
| amp_init | /system/bin/amploader | init.qcom.rc |
| amp_load | /system/bin/amploader | init.qcom.rc |
| amp_unload | /system/bin/amploader | init.qcom.rc |
| p2p_supplicant | /system/bin/wpa_supplicant | init.qcom.rc |
| wpa_supplicant | /system/bin/wpa_supplicant | init.qcom.rc |
| dhcpcd_wlan0 | /system/bin/dhcpcd | init.qcom.rc |
| dhcpcd_p2p | /system/bin/dhcpcd | init.qcom.rc |
| iprenew_wlan0 | /system/bin/dhcpcd | init.qcom.rc |
| iprenew_p2p | /system/bin/dhcpcd | init.qcom.rc |
| ptt_socket_app | /system/bin/ptt_socket_app | init.qcom.rc |
| ptt_ffbm | /system/bin/ptt_socket_app | init.qcom.rc |
| wifi_ftmd | /system/bin/wifi_ftmd | init.qcom.rc |
| cnss_diag | /system/bin/cnss_diag | init.qcom.rc |
| cnss-daemon | /system/bin/cnss-daemon | init.qcom.rc |
| dhcpcd_bt-pan | /system/bin/dhcpcd | init.qcom.rc |
| iprenew_bt-pan | /system/bin/dhcpcd | init.qcom.rc |
| dhcpcd_bnep0 | /system/bin/dhcpcd | init.qcom.rc |
| dhcpcd_bnep1 | /system/bin/dhcpcd | init.qcom.rc |
| dhcpcd_bnep2 | /system/bin/dhcpcd | init.qcom.rc |
| dhcpcd_bnep3 | /system/bin/dhcpcd | init.qcom.rc |
| dhcpcd_bnep4 | /system/bin/dhcpcd | init.qcom.rc |
| loc_launcher | /system/bin/loc_launcher | init.qcom.rc |
| fm_dl | /system/bin/sh | init.qcom.rc |
| drmdiag | /system/bin/drmdiagapp | init.qcom.rc |
| qcom-sh | /system/bin/sh | init.qcom.rc |
| qcom-post-boot | /system/bin/sh | init.qcom.rc |
| qti-testscripts | /system/bin/sh | init.qcom.rc |
| wifi-sdio-on | /system/bin/sh | init.qcom.rc |
| wifi-crda | /system/bin/sh | init.qcom.rc |
| config-zram | /system/bin/sh | init.qcom.rc |
| atfwd | /system/bin/ATFWD-daemon | init.qcom.rc |
| hostapd | /system/bin/hostapd | init.qcom.rc |
| ds_fmc_appd | /system/bin/ds_fmc_appd | init.qcom.rc |
| ims_regmanager | /system/bin/exe-ims-regmanagerprocessnative | init.qcom.rc |
| battery_monitor | /system/bin/battery_monitor | init.qcom.rc |
| ril-daemon2 | /system/bin/rild | init.qcom.rc |
| ril-daemon3 | /system/bin/rild | init.qcom.rc |
| usb_uicc_enable | /system/bin/sh | init.qcom.rc |
| usb_uicc_daemon | /system/bin/usb_uicc_client | init.qcom.rc |
| profiler_daemon | /system/bin/profiler_daemon | init.qcom.rc |
| hcidump | /system/bin/sh | init.qcom.rc |
| charger | /charger | init.qcom.rc |
| ssr_diag | /system/bin/ssr_diag | init.qcom.rc |
| hvdcp | /system/bin/hvdcp | init.qcom.rc |
| charger_monitor | /system/bin/charger_monitor | init.qcom.rc |
| qbcharger | /charger | init.qcom.rc |
| diag_mdlog_start | /system/bin/diag_mdlog | init.qcom.rc |
| diag_mdlog_stop | /system/bin/diag_mdlog | init.qcom.rc |
| qlogd | /system/xbin/qlogd | init.qcom.rc |
| rootagent | /system/bin/sh | init.qcom.rc |
| bms-sh | /system/bin/sh | init.qcom.rc |
| vm_bms | /system/bin/vm_bms | init.qcom.rc |
| msm_irqbalance | /system/bin/msm_irqbalance | init.qcom.rc |
| wfdservice | /system/bin/wfdservice | init.qcom.rc |
| RIDL | /system/vendor/bin/RIDLClient.exe | init.qcom.rc |
| bugreport | /system/bin/dumpstate | init.qcom.rc |
| fidodaemon | /system/vendor/bin/fidodaemon | init.qcom.rc |
| poweroffhandler | /system/bin/poweroffhandler | init.qcom.rc |
| qcom-usb-sh | /system/bin/sh | init.qcom.usb.rc |
| ueventd | /sbin/ueventd | init.rc |
| logd | /system/bin/logd | init.rc |
| logd-reinit | /system/bin/logd | init.rc |
| console | /system/bin/sh | init.rc |
| adbd | /sbin/adbd | init.rc |
| lmkd | /system/bin/lmkd | init.rc |
| servicemanager | /system/bin/servicemanager | init.rc |
| vold | /system/bin/vold | init.rc |
| netd | /system/bin/netd | init.rc |
| debuggerd | /system/bin/debuggerd | init.rc |
| debuggerd64 | /system/bin/debuggerd64 | init.rc |
| ril-daemon | /system/bin/rild | init.rc |
| drm | /system/bin/drmserver | init.rc |
| media | /system/bin/mediaserver | init.rc |
| defaultcrypto | /system/bin/vdc | init.rc |
| encrypt | /system/bin/vdc | init.rc |
| bootanim | /system/bin/bootanimation | init.rc |
| gatekeeperd | /system/bin/gatekeeperd | init.rc |
| installd | /system/bin/installd | init.rc |
| flash_recovery | /system/bin/install-recovery.sh | init.rc |
| racoon | /system/bin/racoon | init.rc |
| mtpd | /system/bin/mtpd | init.rc |
| keystore | /system/bin/keystore | init.rc |
| dumpstate | /system/bin/dumpstate | init.rc |
| mdnsd | /system/bin/mdnsd | init.rc |
| uncrypt | /system/bin/uncrypt | init.rc |
| fakeperm | /system/bin/fakeperm | init.rc |
| rilproxy | /system/bin/rilproxy | init.rilproxy.rc |
| qcamerasvr | /system/bin/mm-qcamera-daemon | init.target.rc |
| qrngd | /system/bin/qrngd | init.target.rc |
| qrngp | /system/bin/qrngp | init.target.rc |
| qseecomd | /system/bin/qseecomd | init.target.rc |
| esepmdaemon | /system/vendor/bin/esepmdaemon | init.target.rc |
| perfd | /system/vendor/bin/perfd | init.target.rc |
| thermal-engine | /system/vendor/bin/thermal-engine | init.target.rc |
| time_daemon | /system/bin/time_daemon | init.target.rc |
| audiod | /system/bin/audiod | init.target.rc |
| wcnss-service | /system/bin/wcnss_service | init.target.rc |
| ppd | /system/bin/mm-pp-daemon | init.target.rc |
| per_mgr | /system/bin/pm-service | init.target.rc |
| imsstarter | /system/bin/sh | init.target.rc |
| imsqmidaemon | /system/bin/imsqmidaemon | init.target.rc |
| imsdatadaemon | /system/bin/imsdatadaemon | init.target.rc |
| ims_rtp_daemon | /system/bin/ims_rtp_daemon | init.target.rc |
| imscmservice | /system/bin/imscmservice | init.target.rc |
| touch | /system/bin/sh | init.target.rc |
| dts_configurator | /system/bin/dts_configurator | init.target.rc |
| zygote | /system/bin/app_process | init.zygote32.rc |

## Componente ale motorului

Indexul central optimizat al `omni.ja` conține **1861 intrări**. Formatul nu este acceptat direct de cititorul ZIP standard folosit aici pentru aplicații; scriptul citește separat indexul Mozilla.
Exemple de nume de componente observate în acest index:

- `components/SettingsService.js`
- `modules/ContactService.jsm`
- `components/SimContactService.js`

## Ce înseamnă rezultatele

- Shell-ul este împărțit între `system`, `launcher`, tema vizuală, tastatură și aplicațiile de funcții; nu este un singur launcher independent.
- HTML/CSS/JS și resursele vizuale sunt disponibile local pentru analiză și prototipare. Nu am demonstrat rularea pachetelor nemodificate în Android WebView.
- Aplicațiile certified și API-urile `moz*` necesită servicii/permisiuni KaiOS. Un WebView Android standard nu furnizează automat aceste implementări.
- `b2g`, `libxul.so` și `omni.ja` sunt prezente în arborele extras. Prezența lor nu demonstrează compatibilitatea cu userspace-ul Android 8.1 sau posibilitatea înlocuirii WebView.
- Aplicațiile găsite în dump nu au fost verificate online. Prezența unui client terț nu confirmă accesul curent la serviciul său.
- Nu publicăm pachetele originale, resursele extrase sau codul vendor prin acest inventar. Reutilizarea și distribuirea fiecărui material necesită verificarea licenței/provenienței sale.

## Reproducere și limite

Rulează `python analysis/inventory_kaios_ui.py` după extragerea locală a sistemului în `analysis/system`.
Scriptul citește registrul, manifestele și arhivele fără să le extragă sau execute. Hash-urile, contoarele și lista completă de API-uri sunt salvate local în `android81/output/kaios-ui-inventory.json`.
Acest raport include numai metadate și rezultate de analiză, fără cod sau resurse din pachete.
Numerele de API reprezintă indicii textuale, nu un graf complet de dependențe și nici o probă de funcționare.

Planul de adaptare: [KAIOS-ALTERNATIVE.md](KAIOS-ALTERNATIVE.md).
