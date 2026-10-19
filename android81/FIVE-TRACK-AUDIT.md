# Audit de dezvoltare — 19 octombrie 2026

## Calendar și Fișiere

Două activități native noi în Leo Visual, fără motor web:

- Calendar: lună navigabilă din D-pad, `*`/`#` pentru luni, agendă și creare/editare/ștergere de evenimente într-un calendar local Leo din CalendarProvider. Nu include recurențe, sincronizare sau notificări de eveniment.
- Fișiere: memoria partajată, Documente Leo și directoarele proprii de pe card; directoare, text UTF-8 până la 32 KiB, redenumire și ștergere confirmată. Directoarele se șterg numai goale. Deschiderea externă depinde de o aplicație compatibilă instalată.
- LeoFileProvider oferă doar citire prin URI cu permisiune temporară; verifică limita canonică a rădăcinii. Accesul prin `..` a fost respins pe telefon.

Probele efectuate: creare, persistență după reboot, editare și ștergere de eveniment sintetic; creare folder și text, persistență după reboot, editare, redenumire de fișier, ștergere fișier și folder gol, citirea prin provider. Datele sintetice au fost curățate. Providerul Android 8.1 refuză selecții pe URI events/id; editarea și ștergerea folosesc URI events cu filtru simultan pentru ID și calendarul Leo.

## Pornire și statistici de rețea

`NetworkPolicyManagerService.initService()` ieșea înainte de semnalizarea latch-ului când kernelul nu oferea bandwidth accounting. Patch-ul mută semnalizarea în `finally`. NetworkStats păstrează starea indisponibilă și raportează explicit recorderele neinițializate în dumpsys.

Scripturi: `patch-netpolicy-boot.py`, `build-netpolicy-boot.sh`, `collect-netpolicy-runtime.py`, `install-netpolicy-boot.py`. Instalarea verifică cele 45 de artefacte ART de boot și păstrează copii locale verificate SHA-256. Restaurare: `python android81/install-netpolicy-boot.py --restore`, apoi reboot. JAR-ul optimizat trebuie instalat împreună cu cele trei fișiere ART/ODEX/VDEX corespunzătoare.

Prima pornire a ajuns la boot-completed în 29 s. Ulterior dispozitivul a fost găsit în Qualcomm 900E. După recuperarea fizică: Android pornește, `/system` este read-only, dumpsys netstats răspunde explicit că accounting nu este disponibil și activity lastanr raportează niciun ANR de la boot. Aceste rezultate nu demonstrează stabilitate pe termen lung.

## Conectivitate: rezultat limitat

Proba manuală deja instalată a obținut Wi-Fi COMPLETED, adresă și două răspunsuri ping prin `wlan0`; Bluetooth a ajuns ON. Nu sunt validate pairing, profiluri audio sau transfer.

Activarea automată experimentală a fost dezactivată după episodul 900E (`persist.leo.connectivity.auto=0`). După recuperare, Bluetooth a repetat SIGABRT la `hci_layer_android.cc:78`, inițializare HCI fără SUCCESS. Bluetooth a fost oprit. Cauza exactă a episodului 900E rămâne nedeterminată; lipsesc pstore/last_kmsg. Nu se declară conectivitate stabilă după reboot.

`init.leo.connectivity-auto.rc` și `install-connectivity-autostart.py` sunt instrumente de dezvoltare pentru proba HAL deja instalată, nu un pachet complet de conectivitate și nici configurație de producție. Nu activa autostart înainte de rezolvarea ordinii de inițializare HCI și a recuperării după eroare.

## Telefonie și alarmă

Numărul autorizat pentru probe este păstrat exclusiv local. După recuperare, SIM 1 a raportat trimitere SMS reușită, confirmată de utilizator pe telefonul destinatar. Răspunsul cu emoji a fost primit și salvat de SmsReceiver în baza privată, subscription 1. Apelul inițiat pe SIM 1 a ajuns la destinatar (confirmat de utilizator). În timpul apelului USB s-a deconectat temporar; ulterior uptime a confirmat că telefonul nu se repornise, iar ambele stări de apel au revenit la idle. Utilizatorul a raportat lipsa sunetului. La reconectare: nicio placă în /proc/asound/cards, persist.leo.audio.autoload=0 și setup oprit; serviciile Android rulează fără driverul Qualcomm încărcat. Configurația audio validată anterior este reactivată separat înainte de retestare. Nu se declară audio funcțional la apelul inițiat. Validarea alarmelor după reboot este neconcludentă din cauza episodului 900E și a resetării ceasului după scoaterea bateriei; ceasul a fost sincronizat cu PC-ul. Confirmarea anterioară a temporizatorului audibil rămâne valabilă.

Jurnalele brute, copiile binare și numerele de telefon nu sunt incluse în repository.

O a doua pornire, cu autostartul conectivității și Bluetooth oprite, a ajuns la boot-completed în 29 s. După aproximativ nouă minute: fără ANR raportat. Această probă nu validează autostartul HAL.

Reîncărcarea manuală a configurației audio a trecut prin stările waiting/loading, apoi dispozitivul a reapărut ca Qualcomm 900E. Încărcarea completă nu a fost repetată. Protecția dezarmează autoload înainte de încărcare; cauza la nivel de modul/DSP trebuie izolată. Proba SIM 2 nu este declarată efectuată.
