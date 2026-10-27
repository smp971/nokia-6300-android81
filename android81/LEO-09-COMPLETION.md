# Leo 0.9 · detaliile care fac telefonul mai comod

27 octombrie 2026 · Android 8.1 / Nokia 6300 4G TA-1286.

## Funcții și dovezi

| Domeniu | Ce este implementat | Verificare / limită |
| --- | --- | --- |
| Căutare | SMS din provider și arhiva Leo, evenimente, fișiere; acces direct la rezultat | Fixture SMS, deduplicare, filtrare, calendar real temporar și limite de directoare: trecute |
| Telefon | Apel în așteptare, redirecționare, numere blocate, adăugare/conferință/comutare când Telecom permite | Blocare/deblocare pe provider Android: trecut; codurile operatorului sunt pregătite în dialer și cer apelare explicită; conferința/operatorul rămân de testat |
| Bluetooth | Destinații asociate, confirmare trimitere, progres, acceptare/refuz, anulare și istoric | Citirea metadatelor reale funcționează; transferul între telefoane rămâne de verificat |
| Reportofon | Voce 16 kHz/64 kbps, echilibrat 22,05 kHz/96 kbps, detaliat 44,1 kHz/128 kbps; mono AAC/M4A | Validarea profilurilor și filtrelor: trecută; înregistrarea reală amânată de utilizator |
| Bibliotecă înregistrări | Azi, ultimele 7 zile, favorite, căutare T9/text, sortare, redenumire, partajare, ștergere cu confirmare | Nu se modifică fișiere în timpul înregistrării; maximum 500 intrări afișate |
| Accesibilitate | Contrast ridicat, text normal/mare/foarte mare în liste și formulare, vibrații opționale SMS/mementouri | Paletă și limite scalare: trecute; unele ecrane desenate pe canvas păstrează geometria proprie; vibrația noilor notificări nu a fost confirmată fizic |
| Ajutor | Meniu scurt deschide meniul global; Meniu ținut 650 ms deschide comenzile paginii | Paginile se deschid; apăsarea fizică lungă rămâne de confirmat; protecție în apel, blocare și alarmă |

## Audit tehnic

- 31 verificări `Complete09Tests`: rezultate și destinații de căutare, limite de
  fișiere, respingere de cod MMI în numărul redirecționat, accesibilitate, profiluri
  recorder, citirea OPP, provider calendar/blocare, backup și deschiderea paginilor.
- Regresie: 10 `PhoneTabsTests`, 26 `SixPolishTests`, 34 `DailyUse08Tests`.
  Toate au returnat `INSTRUMENTATION_CODE: -1`.
- Testele locale nu au inițiat apeluri, transferuri, mesaje sau captură de microfon.
  Datele temporare de calendar/blocare au fost șterse după verificare.
- Preferințele de accesibilitate și recorder intră în backupul selectiv de setări.
  Istoricul transferurilor și autentificările nu sunt exportate de această etapă.
- Căutarea de fișiere este limitată la rădăcinile permise, 3.000 intrări, adâncime
  5 și 20 rezultate; ignoră directoarele ascunse, Android și ieșirile prin symlink.
  SMS: maximum 1.500 rânduri per sursă și 20 rezultate; calendar: -30/+366 zile.
- Bluetooth folosește stiva Android OPP existentă. Helperul root este restrâns la
  listare, trimitere și acțiuni pe transfer. Metadatele se citesc local, read-only,
  deoarece cursorul Binder al portului eșua la alocarea ferestrei. Schimbările se
  fac prin providerul nativ, iar trimiterea prin receiverul nativ handover; baza
  de date nu este scrisă direct. Primirea nu este acceptată automat.
- Această cale OPP este specifică acestui Android 8.1: calea bazei de date și
  interfețele ascunse trebuie reevaluate la schimbarea versiunii sistemului.
- Nu s-au rescris boot, recovery, modem sau drivere pentru această versiune.
  Nu este un ZIP de ROM complet instalabil din recovery.

## Recompilare și probe

Folosește [instrucțiunile de build](RELEASE-APPS.md), apoi
`build-six-feature-tests.sh` pentru APK-ul de teste. Rulează instrumentările
secvențial și cu ecranul treaz. Nu rula automat testele vechi de reportofon dacă
înregistrarea nu este autorizată.

`BluetoothFileTrial` acceptă `action=status` sau `action=send` plus adresa unui
telefon deja asociat și aprobat de utilizator. Trimite exclusiv fișierul local
`leo-bluetooth-test.txt`, niciun fișier personal. Nu este un test offline.

## De finalizat

Transfer text Nokia ↔ Samsung și verificare conținut; o înregistrare reală cu
profilurile noi; verificarea fizică a ajutorului și vibrațiilor; servicii operator
și conferință pe un SIM ales explicit. Implementat nu înseamnă testat pe rețea.
