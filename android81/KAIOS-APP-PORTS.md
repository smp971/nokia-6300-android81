# Aplicații KaiOS → Leo

19 octombrie 2026. Copia de referință este locală, în
`android81/local/kaios-apps/`: 63 de `application.zip`, manifestele disponibile,
registrul cu 64 de intrări și `checksums.json`. Intrarea Maps nu are arhivă în dump.
Au fost păstrate toate pachetele disponibile, inclusiv aplicațiile implicite;
nu doar aplicațiile alese pentru portare. Originalele din `analysis/` sunt păstrate.

Reproducere: `python android81/copy-kaios-apps.py`. Fiecare fișier de aplicație
este verificat SHA-256; o copie existentă diferită oprește operația. Scriptul
nu extrage și nu execută arhivele. Folderul `local/` este ignorat de Git;
în repository publicăm implementările proprii și documentația.

## Ce înseamnă portarea

Separăm fluxul vizual de serviciul din spate. Folosim aplicațiile copiate pentru
a analiza ecranele, navigarea, stările și comenzile. Adaptarea Leo folosește API-uri
Android, componente native și stilul shell-ului. Nu este executarea nemodificată
a aplicațiilor Gaia și nu include automat resursele sau codul lor în APK.

Aplicațiile cu `mozContacts`, `mozMobileMessage`, `mozAlarms`, device storage,
telefonie și alte API-uri Gecko au nevoie de înlocuitori Android. Numărul de
dependențe detectate și permisiunile originale sunt în [inventar](KAIOS-UI-INVENTORY.md).
Serviciile KaiOS nu se copiază peste serviciile Android.

## Primul port: Ceas Leo

Referințe inspectate: `js/panels/stopwatch/panel.html` și `main.js`, plus
panourile `alarm`, `alarm_edit`, `alarm_settings` și `timer` identificate în arhivă.
Fluxul original include start/pauză/continuare/resetare/ture.

Implementarea proprie: `visual-shell/ClockActivity.java`, deschisă de intrarea
**Ceas** din meniul principal. Aplicația Android Clock originală rămâne accesibilă
în **Aplicații Android**.

| Funcție | Stare |
| --- | --- |
| Oră și dată | Implementate, folosesc ora sistemului Android |
| Schimbare filă | Stânga/dreapta: Ora ↔ Cronometru |
| Start / pauză / continuare | OK, cu etichetă contextuală |
| Ture | Soft stânga în mers, maximum 10; afișează ultimele patru durate totale |
| Resetare | Soft stânga în pauză, confirmare din meniul Leo |
| Persistență | Stare și ture în preferințe private; ceas monotonic în aceeași pornire |
| După reboot | Nu continuă automat cronometrul; păstrează ultimul checkpoint salvat |
| Alarme | Editor propriu Leo și AlarmManager; o singură declanșare sau repetare zilnică |
| Temporizator | Implementat, 1 secundă–24 ore; declanșare în fundal verificată |

Verificat pe telefon: intrarea Ceas lansează Leo; start → tură → pauză a salvat
2.978 ms total și o tură la 1.488 ms; relansarea după oprirea procesului a păstrat
starea și tura. Captura reală a fost inspectată. Nu a fost făcut un reboot nou.
Etapa următoare a adăugat alarme și temporizator, cu declanșare în fundal verificată;
sunetul temporizatorului a fost confirmat de utilizator. Recuperarea după reboot
rămâne de verificat. [Detalii](LEO-SYSTEM-APPS.md).

![Cronometru Leo](screenshots/leo-clock-stopwatch.png)

## Lista de portare

Aceasta este ordinea propusă, nu o listă de aplicații deja funcționale.

| Aplicații / componentă | Destinație Android | Stare / criteriu de acceptare |
| --- | --- | --- |
| Launcher, System, temă | Leo Visual, meniu global, softkeys | Bază implementată; uniformizare în curs |
| Keyboard | Leo Multi-tap / InputConnection | Multi-tap și T9 local de bază implementate; probe pe editorul real, fără învățare |
| Clock | Ceas Leo + AlarmManager | Oră, cronometru, alarme și temporizator; declanșare în fundal verificată și sunetul temporizatorului confirmat; recuperarea la reboot netestată |
| Calculator | Calculator Leo nativ | Instalat; 29 cazuri JVM trecute; priorități, zecimale și împărțire la zero verificate pe telefon |
| Unit Converter | Convertor Leo nativ | Instalat; șase categorii, 137 verificări JVM și probă de temperatură pe telefon |
| Notes | Editor nativ + stocare privată | Instalat; multi-tap, salvare, redeschidere, ciornă, renunțare și ștergere confirmată verificate |
| Contact, Call Log | ContactsContract / CallLog / Telecom | Contacte și favorite testate local; apelul inițiat pe SIM 1 a sunat la destinatar, dar fără audio după recuperare |
| Messages | Furnizor SMS Android / SmsManager + copie privată la recepție | SIM 1: trimitere și răspuns cu emoji confirmate; SIM 2 încă de validat |
| Calendar | CalendarContract / stocare locală | Calendar Leo: creare, persistență după reboot, editare și ștergere verificate |
| File Manager, Downloads | Storage Access Framework / DownloadManager | Fișiere Leo: text, editare, redenumire și ștergere testate. Descărcări: UI implementat, transfer nevalidat |
| Music, Video | MediaStore / MediaPlayer | Muzică/Video Leo implementate; redarea reală depinde de repararea driverelor după recuperare |
| Gallery, Wallpaper | MediaStore / resurse proprii | Galerie și Fundaluri Leo: miniaturi, vizualizare, zoom și aplicare/eliminare fundal testate |
| Recorder | MediaRecorder / ruta microfon | Reportofon Leo implementat; înregistrare și redare reale încă nevalidate |
| Camera | Camera API / HAL | Depinde de integrarea hardware; un ecran nou nu rezolvă driverul |
| FM Radio | Driver / HAL / rută audio | Necesită investigație hardware; fără echivalent Android generic garantat |
| Settings, Bluetooth Manager | API-uri și servicii Android | Setări Leo implementate: ecran, profiluri, stocare, teme și legături Android; conectivitatea are un flux separat |
| Callscreen, EmergencyCall | Telecom / UI apel Leo | Apel primit și audio verificate anterior; nu testăm apeluri de urgență |
| STK, Network Alerts, WAP Push | Serviciile de telefonie Android | Necesită analiză pe funcții și operator; nu se copiază daemonii KaiOS |
| Internet, E-Mail | Motor web / client compatibil Android | Investigație separată de stabilitate și servicii |
| Store, FOTA, FTU, anti-theft, conturi | Servicii specifice platformei | Fără compatibilitate declarată; cerințe proprii, nu portare prin copiere |

Pentru fiecare aplicație urmărim: ecrane analizate → API-uri înlocuite → navigare
completă din taste → stocare/permisiuni → probă reală → captură și rezultat în Git.
Jocurile și clienții terți din copia completă nu sunt incluse automat în aplicațiile
standard Leo.

Detalii despre implementările noi, comenzi și criteriile de validare:
[Calculator și Notițe Leo](LEO-CALCULATOR-NOTES.md).
[Contacte, Telefon, Mesaje și alarme](LEO-SYSTEM-APPS.md).

[Suita nouă: implementare, verificări și limite](LEO-APP-SUITE.md).
