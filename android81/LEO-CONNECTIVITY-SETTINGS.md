# Setări Leo: categorii și conectivitate

Interfață nativă pentru Android 8.1, inspirată de organizarea KaiOS. Nu încarcă
shell-ul sau aplicațiile proprietare KaiOS și nu depinde de WebView.

## Navigare

- Stânga/dreapta: schimbă tabul; trecerea între primul și ultimul este circulară.
- Sus/jos: selectează o opțiune din categoria curentă.
- OK: deschide opțiunea sau submeniul.
- Soft stânga / Meniu: lista categoriilor; în paginile de conectivitate actualizează starea.
- Înapoi: revine din pagina de detaliu sau închide Setări Leo.

Taburi: **Rețele** (Wi-Fi, Bluetooth, hotspot, rețea mobilă), **Aspect**
(profil, luminozitate, timeout, fundal, tastatură), **Siguranță** (blocare taste,
aplicații/permisiuni, localizare), **Telefon** (dată, stocare, informații, setările
Android). Selecția fiecărui tab se păstrează la schimbarea categoriei și revenire.
Paginile Android sunt folosite unde nu există încă un editor nativ Leo.

## Controale native

- Wi-Fi: activare, conexiune curentă, scanare, adăugare manuală, rețele deschise și
  WPA/WPA2 Personal. WEP și Enterprise trimit către configurarea Android.
- Bluetooth: activare, căutare, asociere prin confirmarea Android și conectare /
  deconectare a dispozitivelor de intrare prin profilul HID. Nu implementează
  profiluri audio proprii și nu validează accesoriile audio.
- Hotspot: stare reală, nume și parolă WPA2, pornire/oprire prin serviciul de
  tethering Android. Pornirea cere confirmare dacă Wi-Fi stație este activ.
  Starea AP nu dovedește singură DHCP sau acces Internet de pe un client.

Android 8.1 cere permisiune și Localizare activă pentru scanare Wi-Fi/Bluetooth.
Leo deschide configurarea corespunzătoare când lipsește; rețeaua Wi-Fi poate fi
adăugată manual. Parolele nu sunt afișate în clar în formulare și nu sunt păstrate
în repository. Datele de asociere și configurațiile sunt gestionate de Android.

## Integrare în imaginea de dezvoltare

`build-visual-shell.sh` compilează aplicația. După instalarea APK-ului cu
`pm install -r`, `install-visual-system-settings.py` verifică APK-ul instalat și
adaugă aceeași aplicație în `/system/priv-app/LeoVisual/`, cu allowlist explicit
pentru `OVERRIDE_WIFI_CONFIG`. Android verifică compatibilitatea semnăturii la
instalarea inițială; UID-ul și datele existente se păstrează. Reboot-ul activează
dreptul privilegiat necesar configurării hotspotului pe Oreo.
Extinderea pentru SIM, ceas și setări protejate are un allowlist suplimentar,
descris în [paginile native de setări](LEO-NATIVE-SETTINGS.md).

`build-connectivity-resources.sh` construiește overlay-ul WLAN pentru tethering.
`install-connectivity-resources.py` păstrează APK-ul anterior, verifică toate
perechile ID/nume de resursă și refuză alte modificări de conținut decât
`resources.arsc`. Nu înlocuiește JAR-uri sau fișiere ART. Un remount RO ocupat de
mapările framework-ului necesită repornirea planificată înainte de alte teste.

Vezi [bring-up conectivitate](CONNECTIVITY-BRINGUP.md) pentru hardware și init.
