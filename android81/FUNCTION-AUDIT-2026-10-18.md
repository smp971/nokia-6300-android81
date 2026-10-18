> **Audit istoric.** Audio, LTE și interfața de apel au evoluat după această probă.
> Tabelul actualizat la 19 octombrie 2026 este în [README-ul proiectului](../README.md#tabelul-funcțiilor).
> Un apel primit real, răspunsul/închiderea din taste și audio în ambele sensuri
> au fost confirmate ulterior; vezi [CALL-KEYPAD.md](CALL-KEYPAD.md).

# LTE follow-up

SIM 1 LTE data now passes after reboot: netmgr automatic, Android connection
validated, two external packets received. Both SIMs remain READY/voice registered.
The menu exposes 4G. VoLTE/VoWiFi and call audio remain unverified.
See LTE-BRINGUP.md; the original audit below predates this integration.

# Verificare functii — Nokia 6300, 18 octombrie 2026

Actualizare ulterioara: difuzorul functioneaza acum prin driverul Qualcomm in
Android si dupa reboot; microfonul/apelurile raman netestate. Vezi
[AUDIO-BRINGUP](AUDIO-BRINGUP.md). Tabelul de mai jos pastreaza auditul initial.

Verificare pe telefonul conectat, Android 8.1, dupa integrarea Adreno si
corectia DispSync. Este o fotografie a starii actuale, nu o certificare a
functionarii complete. Jurnalele si capturile brute raman private in output/.

| Functie | Rezultat verificat | Limita |
| --- | --- | --- |
| Android / meniul Leo | Pornit, utilizator deblocat, interfata vizibila | ANR NetworkPolicy la pornire ramane cunoscut; fara reboot nou in acest audit |
| Adreno / afisare | Qualcomm Adreno 304 activ; verificarea grafica trece | WebView are o cale GPU care inca se inchide; nu toate aplicatiile sunt stabile |
| Taste / navigare | D-pad, Home/Meniu verificate anterior si utilizate in audit | Nu exista T9/multi-tap complet sau adaptare universala a aplicatiilor |
| Memorie interna | /system RO, /data RW, fara card pentru sistem | Aproximativ 2.1 GiB liberi pe /data la verificare |
| USB / ADB / root | Functionale in timpul auditului | MTP si transferul prin interfata utilizatorului nu au fost testate |
| Baterie | Android citeste nivelul, tensiunea, temperatura si alimentarea USB | Autonomia si un ciclu de incarcare nu sunt validate |
| Profil memorie redusa | ro.config.low_ram=true, zRAM ~256 MiB activ | Nu inseamna certificare comerciala Android Go / servicii Google |
| Doua SIM-uri / modem | Ambele READY, ambele sloturi de voce IN_SERVICE | Inregistrarea la operator nu confirma un apel complet |
| Apeluri / SMS | Dialer porneste; transportul real nu este verificat | Nu s-au initiat apeluri sau trimis mesaje; lipseste o aplicatie SMS dedicata in inventarul curent |
| Date mobile / internet | Nicio retea implicita activa, niciun PDP activ confirmat | Nu functioneaza ca acces la internet in starea verificata |
| Difuzor / microfon | Neintegrate: HAL audio.primary.default.so generic AOSP | HAL incarcat si hash identic cu build-ul generic; out_write nu trimite audio la hardware, in_read produce zerouri |
| Wi-Fi | Activarea nu ajunge operationala; stare unknown, fara wlan0 | Comanda a fost acceptata, dar nu exista conectare sau scanare confirmata |
| Bluetooth | Activarea esueaza, procesul se inchide repetat | Abort hci_layer_android.cc: btHci != nullptr; serviciul HCI necesar nu este disponibil |
| Camera | Serviciul raporteaza zero dispozitive | Aplicatia instalata / feature declarat nu inseamna camera integrata |
| Localizare GPS | Fara provider GPS functional sau pozitie reala | Doar fused/passive, fara fix GPS testat |
| Senzori | SensorService: No Sensors on the device, devInitCheck=-19 | Descrie ce vede Android, nu stabileste ce senzori exista fizic |
| Calculator | Porneste; afisarea 2+2 → 4 confirmata prin captura | Expresie introdusa prin ADB; nu reprezinta testarea tuturor operatiilor |
| Setari | Se deschide si afiseaza interfata | Unele categorii nu au driverul hardware din spate |
| Ceas / Calendar / Contacte / Fisiere | Se deschid, interfetele verificate | Nu s-au creat alarme, contacte sau evenimente; sunetul alarmei nu functioneaza prin HAL-ul generic |
| Galerie / Muzica | Se deschid | Fara redare audio/video sau decodare foto completa testata |
| Browser / WebView | Afiseaza HTML local, textul LEO WEB OK verificat vizual | WebView 61.0.3163.98; ulterior SIGBUS in Chrome_InProcGp. Partial functional, instabil; fara proba internet |
| Email | Se inchide la pornire | InflateException / Resources.NotFoundException pentru drawable/ic_next; bitmap fara src valid |
| Card SD / Bluetooth pairing / hotspot | Netestate complet | Niciun card sau dispozitiv extern nu a fost utilizat pentru aceste probe |

## Ce a fost testat si cum a ramas telefonul

Au fost deschise explicit cele 11 aplicatii din proba, cu capturi locale.
Email a generat un dialog de eroare care a acoperit temporar browserul;
browserul a fost verificat din nou dupa inchiderea acelui dialog. Captura HTML
reusita nu anuleaza crash-ul GPU ulterior. Prima asteptare la Ceas a expirat
cu ecranul adormit; repetarea cu ecranul trezit a reusit si nu este marcata ca
defect al aplicatiei Ceas.

Wi-Fi si Bluetooth erau oprite initial. Au fost activate temporar pentru proba,
apoi readuse la setarile globale wifi_on=0 si bluetooth_on=0. Bluetooth a ajuns
OFF; Wi-Fi a ramas cu raportare hardware unknown dupa esec, fara interfata WLAN.
Procesele experimentale oprite si dialogurile lor inchise; focus final Leo Shell.

system_server si SurfaceFlinger au pastrat aceleasi PID-uri pe durata auditului.
Verificarile finale Adreno si radio au trecut: doua SIM-uri READY, doua sloturi
voce IN_SERVICE, sistem RO. Nu au fost instalate corectii, rescris boot/recovery,
formatate date, initiate apeluri, trimise SMS-uri sau configurate conturi.

Declaratiile din package features includ touchscreen/camera/senzori generici
care nu reflecta suportul hardware real. Inventarul de mai sus se bazeaza pe
serviciile efective, probe si capturi, nu doar pe aceste declaratii.

Pentru utilizare ca telefon, integrarea audio real este un blocaj esential;
apoi sunt necesare probe separate pentru apel, microfon/difuzor si SMS.
