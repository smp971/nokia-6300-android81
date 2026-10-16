# Leo Shell

Launcher Android 8.1 pentru ecranul 240x320 si taste fizice. O singura View
deseneaza interfata 2D, fara WebView, imagini de fundal sau animatii. Manifestul
dezactiveaza accelerarea HWUI. SurfaceFlinger ramane necesar pentru afisare.

- Ecran principal cu ceasul/data telefonului si numele operatorului cand este disponibil.
- Meniu de aplicatii instalate, sase pozitii pe pagina, selectie cu D-pad.
- OK deschide aplicatia; Meniu comuta intre ecranul principal si aplicatii.
- Inapoi revine la ecranul principal. Tasta Home este gestionata de Android.
- Bara inferioara afiseaza actiunile curente ale soft stanga / OK / soft dreapta.

Bara de stare SystemUI Android este vizibila deasupra shellului. Bara tastelor
apartine launcherului. Nu se suprapune peste aplicatiile deschise si nu
pretinde ca acestea sunt adaptate la taste. Aplicatiile pot ramane lente pe
SwiftShader. Nu furnizeaza T9, modem, browser propriu sau servicii KaiOS.
Ceasul foloseste data sistemului: fara sincronizare, aceasta poate fi incorecta.

## Compilare si instalare pe dispozitivul de test

Din Windows:

```powershell
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-shell.sh
& D:/ROMS/platform-tools/adb.exe -s 695d push android81/output/leo-shell.apk /tmp/leo-shell.apk
python android81/framework-command.py /system/bin/sh /system/bin/pm install -r /tmp/leo-shell.apk
& D:/ROMS/platform-tools/adb.exe -s 695d shell '/sbin/leo-framework-exec /system/bin/cmd package set-home-activity --user 0 ro.leo.shell/.MainActivity'
& D:/ROMS/platform-tools/adb.exe -s 695d shell '/sbin/leo-framework-exec /system/bin/cmd activity start-activity -n ro.leo.shell/.MainActivity'
```

APK semnat cu cheia publica de test AOSP din arborele local. Cheia nu este
copiata in acest repository. Alinierea ZIP se face inainte de semnare pentru
a pastra semnatura APK v2. Aplicatia nu cere permisiuni de acces la contacte,
SMS, apeluri sau Internet; porneste alte aplicatii prin activitatile lor launcher.

Launcher3 este pastrat. Revenire la el:

```powershell
& D:/ROMS/platform-tools/adb.exe -s 695d shell '/sbin/leo-framework-exec /system/bin/cmd package set-home-activity --user 0 com.android.launcher3/.Launcher'
& D:/ROMS/platform-tools/adb.exe -s 695d shell '/sbin/leo-framework-exec /system/bin/cmd activity start-activity -n com.android.launcher3/.Launcher'
```
