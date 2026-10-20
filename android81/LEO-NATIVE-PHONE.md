# Telefon Leo · Night

Interfață nativă pentru ecranul 240 × 320, inspirată de varianta **Leo Night**
aleasă pentru proiect: fundal întunecat, accent turcoaz, trei favorite colorate.
Ecranul Telefon păstrează taburile Apelare / Istoric / Statistici și informațiile
ambelor SIM-uri. Nu desenează o tastatură numerică virtuală.

## Integrare

`PhoneActivity` acceptă `ACTION_DIAL` cu și fără URI `tel:`. Deschiderea unui
număr nu inițiază un apel: selecția SIM-ului și confirmarea rămân explicite.
`LeoInCallService`, legat exclusiv de Telecom prin `BIND_INCALL_SERVICE`,
gestionează interfața apelurilor în `InCallActivity`.

Leo folosește în continuare serviciile Android Telecom, telefonie și audio,
precum și modemul existent. Nu înlocuiește aceste servicii. Dialerul AOSP rămâne
instalat pentru recuperare și fluxurile speciale ale platformei, inclusiv urgențe.
Nu s-a efectuat nicio probă de apel de urgență.

| Control | Acțiune |
| --- | --- |
| Verde / OK la apel primit | Răspunde; OK respectă și selecția Respinge |
| Soft dreapta | Respinge / închide apelul |
| Stânga / dreapta | Selectează Microfon sau Ieșire audio |
| OK în convorbire | Schimbă microfonul / deschide lista ieșirilor audio |
| Sus / jos în convorbire | Volum voce |
| Soft stânga / Meniu | Opțiuni apel |
| 0–9, * și # în convorbire | DTMF, oprit la eliberarea tastei |

Meniul include așteptare/reluare, selecția apelurilor și conferință atunci când
Telecom raportează capabilitățile necesare. Lista audio conține doar rutele oferite
de backend. Aceste scenarii necesită probe reale separate; existența meniului nu
confirmă serviciul în rețea.

Notificarea apelului curent redeschide Leo. După semnalarea unei suprapuneri
Android la apel, notificarea proprie folosește un canal nou IMPORTANCE_LOW,
fără heads-up/full-screen intent; serviciul deschide direct ecranul Leo.
Eliminarea suprapunerii necesită încă o probă reală.
Notificările de apel pierdut sunt
primite prin receiver protejat și duc la istoricul Leo. Identitățile cu prezentare
restricționată nu sunt afișate sau căutate în contacte. Soneria rămâne în grija
platformei: serviciul Leo nu declară `IN_CALL_SERVICE_RINGING`.

Modul mouse este oprit la intrarea în apel. Tasta Meniu nu mai este interceptată
de meniul global cât timp ecranul de apel Leo este vizibil.

## Instalare și verificare

1. `build-visual-shell.sh` construiește APK-ul.
2. `python -X utf8 android81/install-native-phone.py` salvează APK-ul anterior și
   rolul existent în output local ignorat, verifică absența apelurilor și instalează
   actualizarea fără să schimbe rolul.
3. `build-six-feature-tests.sh` include `NativePhoneTests`: intențiile DIAL,
   protecția serviciului și receiverului, ownership-ul soneriei, ecranul privat,
   prioritățile stărilor și formatarea duratei. Testul a trecut pe aparat.
4. `install-native-phone.py --activate` setează rolul implicit Leo; verificat
   `dialer_default_application=ro.leo.visual` pe aparatul de dezvoltare.
5. `install-visual-system-settings.py` sincronizează aceeași versiune în baza
   privilegiată; sistemul este remontat doar temporar și revine read-only.

`install-native-phone.py --restore` restaurează actualizarea APK și rolul din
copia inițială locală; nu rescrie boot/recovery și nu restaurează automat copia
APK din partiția system. Copia privilegiată se gestionează separat prin scriptul
de sincronizare, folosind APK-ul corespunzător.

Demo-ul privat (`InCallActivity`, extra `demo=true`) a fost verificat din taste:
răspuns, durată, microfon, selecție receptor/difuzor și închidere. Nu obține obiecte
Call și nu schimbă rutele sau microfonul real. Capturile de mai jos sunt demo-uri,
nu dovezi pentru un apel în rețea.

După prima instalare a apărut o excepție în `ActivityThread.handleBindApplication`,
la încărcarea resurselor, înainte de codul aplicației. Lansările ulterioare au
reușit; cauza exactă și nerepetarea pe termen lung nu sunt demonstrate.
O actualizare ulterioară a întrerupt ADB; reconectarea fizică USB a permis
continuarea. Nu s-a aplicat un nou mecanism de resetare USB.

Utilizatorul a confirmat apariția ecranului Leo la un apel primit pe SIM 1,
dar a semnalat și un popup Android. Sunetul și închiderea pe noua cale nu au
primit încă o confirmare explicită. După feedback, pagina Telefon are spațiere
nouă, două chipuri SIM colorate, ultimul apel citit din jurnal și cod local
`*#06#` pentru identificatori (fără cerere în rețea sau publicarea lor).
Utilizatorul a confirmat că pagina arată mai bine. Afișarea IMEI, maparea
conturilor la sloturi și dispariția popupului necesită probe manuale separate.
Apelurile bidirecționale confirmate anterior foloseau vechea interfață AOSP.
SIM 2, DTMF, notificările reale de apel pierdut, așteptarea/conferința și
comportamentul după reboot rămân probe distincte.

| Apelare Night | Apel primit · demo | În convorbire · demo |
| :---: | :---: | :---: |
| ![Telefon](screenshots/leo-night-phone.png) | ![Apel primit demo](screenshots/leo-night-incoming-demo.png) | ![Convorbire demo](screenshots/leo-night-active-demo.png) |

## Vibrații · investigație separată

Utilizatorul confirmă că motorul vibra fizic sub KaiOS. Kernelul curent expune
`/sys/class/leds/vibrator` prin `qcom,qpnp-vibrator`; interfața `timed_output`
nu există. Configurația originală extrasă include deja
`CONFIG_LEDS_QPNP_VIBRATOR=y`, deci simpla diferență față de vechea bibliotecă
HAL din dump nu dovedește cauza: KaiOS putea folosi alt traseu.

HAL-ul AOSP construit are fallback LED pentru duration/activate. În noua probă
directă au fost trimise trei impulsuri de 650 ms, separate la două secunde.
Driverul a raportat `state=1` la activare și `state=0` după oprire explicită;
utilizatorul nu a simțit vibrații. Nu este doar un eșec al aplicației Terminal
sau al profilului sonor. Următorul pas este compararea inițializării și driverului
efectiv stock cu sursa kernelului reconstruit; succesul scrierilor sysfs nu
dovedește alimentarea motorului. Sursa publicată `gecko/hal/gonk/GonkHal.cpp`
conține și ramura `SPEAKER_VIBRATE_SUPPORT`, care trimite parametri AudioSystem
`vibrate_enabled` / `vibrate_duration`. Ambii parametri se găsesc efectiv în
libxul.so și audio.primary.msm8909.so din dump. Este o pistă concretă pentru
o cale prin difuzor, încă nevalidată fizic. `VibrationAudioProbe.java` și
`build-vibration-audio-probe.sh` pregătesc o probă de trei impulsuri de 650 ms,
cu dezactivare în finally; această probă audio nu a fost încă executată.
Nu s-au scris registre PMIC brute și nu s-au
modificat boot/recovery în această etapă.
