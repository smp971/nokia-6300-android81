# Leo · mai mult loc pentru lucrurile tale

21 octombrie 2026 · **0.2.0-dev / cod 2**

Această versiune include și toate funcțiile din [etapa anterioară](LEO-PROJECT-FLOWS.md).
**Implementare compilată pe PC; instalarea și probele Android sunt în așteptarea
reconectării USB.** Nu prezentăm capturi ori rezultate de pe telefon pentru această versiune.

| Funcție | Ce aduce | Unde o găsești |
| --- | --- | --- |
| Calendar | Săptămână luni–vineri, precedentă/următoare, căutare, import/export ICS, legătură cu contactele și aniversările existente | Calendar → Opțiuni → Instrumente |
| Galerie | Album Favorite, marcaje multiple, partajare a selecției, zoom 1–8× și deplasare accelerată | Opțiuni; 5 marchează în grilă, 7/9 micșorează/mărește imaginea |
| Liste muzicale | Adăugare, eliminare, reordonare, redenumire, verificarea referințelor lipsă, copertă proprie | Muzică → Liste de redare → listă → Editează |
| Notificări | Grupare pe zile sau aplicații, filtrare explicită, deschidere directă a conversațiilor SMS Leo | Notificări → Opțiuni |
| Program sonor | Interval Noapte/Silențios, zile lucrătoare/weekend, apeluri din favorite și selecție de alarme Leo | Setări → Sunete → Programare |
| Diagnostic | Versiunea Leo, servicii, ultimele tipuri de erori, raport editabil înainte de salvare | Setări → Diagnostic → Exportă jurnalul tehnic |

## Calendar: schimb de evenimente, fără înlocuirea agendei

Exportul produce un fișier nou în **Documente Leo**. Pentru import deschide fișierul
`.ics` în Fișiere Leo, citește numărul de evenimente și confirmă adăugarea.
Evenimentele existente sunt păstrate; repetarea aceluiași import omite duplicatele
cu același titlu, început, sfârșit/repetare și durată. Operațiile de adăugare se
execută într-o singură tranzacție CalendarProvider; limita totală este 500.

Prima versiune suportă UTF-8, linii împăturite, text escapate, ore UTC, date întregi
cu sfârșit exclusiv și repetările simple zilnic/săptămânal/lunar/anual ale Leo.
Exportul recurent păstrează fusul orar prin `X-LEO-TZ`.
Fișierele cu `TZID`, `VTIMEZONE`, invitați, alarme sau alte structuri nesuportate
sunt respinse integral înaintea importului. Limita de intrare este 512 KiB.
Nu este un sincronizator CalDAV și nu importă în tăcere un subset de fișier.
Exportul nu include mementouri, participanți sau legăturile interne cu contactele.
Căutarea acoperă un an în urmă și doi ani înainte, maximum 200 rezultate.
Aniversările continuă să fie adăugate explicit din contact, fără alarmă nouă implicită.

## Galerie și muzică

Galeria păstrează favoritele ca referințe locale MediaStore. Maximum 50 de imagini
pot fi marcate simultan. Selectorul comun trimite `ACTION_SEND_MULTIPLE`, cu
ClipData și acces doar la citire, numai după alegerea destinației. Destinația trebuie
să suporte mai multe imagini. Nu există ștergere în masă în această pagină.
La zoom, D-pad deplasează imaginea în limitele ei, iar apăsarea ținută accelerează.

Editorul listelor modifică referințe, niciodată fișierele muzicale originale.
Verificarea se desfășoară în worker; rezultatele separă fișierele negăsite de
accesul necunoscut. **Un SD scos poate produce referințe aparent lipsă:** eliminarea
rămâne o alegere explicită, cu opțiunea de păstrare implicită.
Coperta aleasă este o copie JPEG de maximum 192 px, salvată privat și afișată
în lista playlisturilor. Playerul păstrează coperta încorporată a piesei.
Fișierele de copertă înlocuite sunt curățate; există maximum 30 de liste a câte 500 piese.
Adăugarea din editor folosește indexul audio Android, maximum 500 rezultate.

## Notificări și sunet

Gruparea folosește data locală a notificării. Sunt afișate numai notificările
încă active în Android; nu construim un istoric persistent cu mesaje private.
Pentru SMS Leo, „Deschide conversația” execută intenția deja asociată conversației.
Notificările altor aplicații folosesc acțiunea originală și nu sunt reinterpretate.

Programul sonor este **dezactivat inițial**, cu un singur interval zilnic configurabil,
inclusiv peste miezul nopții. Poate fi limitat la luni–vineri sau weekend. Ziua
selectată este ziua de început a intervalului. Se replanifică după pornire,
schimbarea orei/fusului sau actualizarea aplicației. Nu face polling.

La intrare salvează volumul muzicii, soneriei, alarmei, radioului activ și politica
Nu deranjați. La ieșire le restaurează. Noapte pune media pe zero; Silențios păstrează
nivelul media. Excepția apelurilor folosește contactele marcate favorite în Android.
Alarmele pot fi permise, oprite ori limitate la anumite alarme Leo; temporizatorul
rămâne permis când alarmele sunt permise. Alarmele excluse Leo se afișează fără sunet.
Selectarea unui profil manual suspendă programul până la următoarea limită.
Modificările manuale ale volumelor în interiorul intervalului sunt înlocuite de
valorile salvate la ieșire.

Android 8.1 permite alarmele externe în modul Prioritate; selecția pe alarmă este
aplicată de serviciul Leo doar alarmelor Leo. Oprirea globală a alarmelor reduce și
fluxul audio Alarmă la zero. Nu pretindem filtrare individuală pentru alte aplicații.
Este necesar accesul Nu deranjați. Aplicarea/restaurarea fizică a sunetului și
execuția după reboot rămân de verificat pe Nokia.

## Diagnostic și date locale

Raportul afișează versiunea aplicației, conectarea serviciilor și aceleași câmpuri
tehnice limitate ca înainte. Lista erorilor conține maximum 20 de intrări:
ora, componenta Leo și tipul excepției. Nu salvează mesajul excepției,
stack trace, logcat, numere de telefon, parole sau conținut de mesaje.
Acoperă erorile necapturate ale procesului Leo și erorile înregistrate explicit de
noile fluxuri; nu colectează toate erorile native ori ale sistemului.

Exportul deschide un editor: poți șterge rânduri, apoi confirma salvarea locală
în `Documente Leo/leo-diagnostic.txt`. Nu există încărcare automată pe GitHub.
Din Opțiuni poți șterge lista de tipuri de erori.

Favoritele galeriei, programul sonor, copertele și noile preferințe de notificări
**nu sunt încă incluse în Backup Leo**. Actualizarea APK păstrează datele aplicației,
dar acest lucru nu echivalează cu un backup complet după formatare.

## Validare

- Compilare Leo Visual și APK de teste Android: trecută.
- 36 verificări algoritmice noi pe PC: ICS, Unicode, duplicate, date invalide,
  limite, program nocturn, weekend și schimbarea orei.
- 10 verificări host ale etapei precedente și 5 teste ale pachetului: trecute.
- `OrganizerTests`: **16 verificări trecute pe Nokia, 22 octombrie 2026**, cu date locale, fără audio,
  apeluri, SMS, email sau alegerea unei destinații externe de partajare.
- `ProjectFlowTests`: 32 verificări trecute pe Nokia.
- Instalat în pachetul 0.4.0-dev. Probele hardware ale programului sonor
  și capturile noi rămân în așteptare.

Reexecutare offline după build:

```bash
bash android81/run-host-organizer-checks.sh
bash android81/run-host-project-checks.sh
python -m unittest discover -s android81/tests -p test_app_bundle.py
```

Testele ICS folosesc implementarea JSON din arborele AOSP fixat, nu stub-urile SDK.
Instrucțiunile pachetului comun sunt în [RELEASE-APPS](RELEASE-APPS.md).
