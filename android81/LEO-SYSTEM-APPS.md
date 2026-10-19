# Contacte, Telefon, Mesaje, alarme și temporizator Leo

19 octombrie 2026. Implementări Android native, în același APK cu Leo Visual.
Intrările Contacte, Telefon și Mesaje din meniul principal deschid acum aplicațiile
Leo. Ceas → fila Ora → Opțiuni oferă Alarme și Temporizator.

## Contacte

- Citește agenda Android prin ContactsContract, cu nume și număr, în ordine alfabetică.
- Opțiuni: contact nou, căutare după nume/număr, revenire la toate contactele.
- Detaliu: apelare prin Telefon Leo, compunere SMS, editare și ștergere confirmată.
- Contactele noi sunt locale, fără cont de sincronizare. Editarea modifică numărul
  selectat și numele raw-contactului asociat, nu toate numerele agregate.
- Salvările folosesc batch-uri ContentProvider; eșecul păstrează formularul.
- Lista afișează până la 500 de numere; căutarea se execută în provider înaintea limitei.
- Sus/jos schimbă câmpurile la marginea textului. Leo Multi-tap rămâne IME.

Proba locală: contactul sintetic `Leo_Port_Test`, număr numai din zerouri, a fost
salvat, apoi numele și ultima cifră au fost editate, fără un apel real. Căutarea
l-a găsit, iar ștergerea confirmată a eliminat numai acest contact de probă.
Salvarea și editarea sunt confirmate prin afișarea datelor de către provider în lista Leo.
Contactele sincronizate, cele fără număr și structurile complexe nu sunt validate.

## Telefon

- Tastatură numerică, ștergere prin soft dreapta, tasta verde pentru apel.
- Opțiuni: apelare, istoric (ultimele 100), Contacte, Telefon Android original.
- TelecomManager furnizează conturile disponibile; utilizatorul alege SIM-ul,
  apoi confirmă apelul. Nu se schimbă aplicația implicită pentru ecranul de apel.
  Interfața de apel Android adaptată anterior rămâne responsabilă de apelul activ.
- Numerele malformate sau alcătuite numai din zerouri sunt refuzate.

Ambele conturi SIM au fost afișate în proba inițială. Nu a fost inițiat niciun
apel în aceste verificări; ambele stări de apel au rămas idle. Efectuarea unui
apel pe fiecare SIM și istoricul complet necesită probe separate.

## Mesaje SMS

- Conversația folosește bule pentru SMS-urile primite (stânga) și trimise
  (dreapta), în ordine cronologică. Intrarea selectată are contur deschis;
  la deschidere este selectat ultimul mesaj. Ziua, ora, SIM-ul și starea de
  trimitere sunt afișate separat. „Trimis” nu înseamnă raport de livrare.
- Sus/jos selectează SMS-ul, OK deschide detaliul complet, Înapoi revine la
  aceeași selecție. Bulele afișează maximum șase rânduri, cu elipsă; detaliul
  oferă textul integral într-un ecran derulabil. Soft stânga: Răspunde,
  Detalii mesaj, Redirecționează, Copiază textul, Copiază numărul, Caută în
  conversație, Reîmprospătează. Același meniu este disponibil din detaliu.
  Meniul fizic global rămâne separat.
- Este o implementare nativă inspirată de stilul KaiOS, fără preluarea Gaia sau
  a resurselor proprietare. Rândurile sunt reutilizate, iar schimbarea selecției
  recolorează doar cele două bule implicate.
- Citește SMS-urile din providerul Android; conversații după adresă, detaliu
  complet și compunere/răspuns din taste, cu eticheta SIM-ului când este disponibilă.
- Imaginea testată nu are aplicație SMS implicită. Receiver-ul protejat de
  `BROADCAST_SMS` păstrează și o copie privată a SMS-urilor primite prin
  `SMS_RECEIVED`. Nu schimbă aplicația SMS implicită și nu scrie în inbox-ul
  sistemului. Copia locală și providerul sunt reunite, cu deduplicare după
  adresă, data expeditorului, corp și subscription ID.
- Notificările nu afișează textul sau numărul; deschid conversația.
- Compunere: număr, mesaj de maximum 1.000 caractere, alegere SIM, afișarea
  numărului de segmente SMS și confirmare. `%`, caracterele românești și alte
  texte sunt segmentate de SmsManager, nu printr-o lungime fixă presupusă.
- Ciorna este păstrată la ieșire prin Home. PendingIntent pentru fiecare segment
  urmărește răspunsul sistemului; o cerere fără răspuns nu se retrimite automat.
  Un rezultat parțial păstrează ciorna și cere verificare înainte de reîncercare.
- Nu sunt implementate MMS, grupuri, atașamente, rapoarte de livrare sau
  ștergerea/editarea mesajelor din providerul Android.

**Istoricul probei inițiale:** ciorna și alegerea celor două SIM-uri au fost
verificate. În proba de confirmare s-a emis neașteptat o cerere către numărul
invalid `0000000000`; nu există confirmare de trimitere reușită sau livrare.
Cererea a fost inițial în așteptare, apoi callback-ul Android a raportat eșec.
Nu s-a repetat. Ulterior au fost blocate numerele numai din zerouri,
iar dialogurile execută alegerea la eliberarea unei apăsări OK începute în
dialogul respectiv, pentru a evita transferul apăsării între dialoguri.
Trimiterea reușită, recepția reală și confirmările modemului rămân nevalidate;
nu marcăm SMS drept funcțional integral. Regresia cu același număr invalid a
fost refuzată înainte de alegerea SIM-ului, fără o cerere nouă. Ciorna de probă
a fost eliminată din interfață; starea de eșec rămâne disponibilă.

Ulterior, trimiterea pe SIM 1 și recepția răspunsului cu emoji au fost confirmate
în probe reale; vezi starea curentă din [README](../README.md). Schimbarea vizuală
a conversației a fost verificată separat, pe telefon, cu trei mesaje fictive:
selecție, detaliu SIM 2, revenire la aceeași selecție și meniul Opțiuni.
Nu s-a trimis niciun SMS în această verificare vizuală.

Previzualizare locală pentru dezvoltare:

```text
python android81/framework-command.py /system/bin/sh /system/bin/am start -f 0x14000000 -n ro.leo.visual/.MessagesActivity --ez preview true
```

Modul demo nu citește inbox-ul, nu marchează conversații ca citite, nu salvează
ciorne și blochează răspunsul/trimiterea. Redirecționarea permite verificarea
editorului cu date fictive, dar trimiterea rămâne blocată. Înapoi din editor
revine la demonstrație. Înapoi din conversație o închide. Deschiderea
normală din Home folosește conversațiile reale și noul aspect.

### Căutare, redirecționare și clipboard

Din lista Mesaje, soft stânga oferă SMS nou și Caută în mesaje. Căutarea folosește
textul sau adresa expeditorului/destinatarului, ca subșir literal, fără diferențe
de majuscule sau diacritice (`maine` găsește `mâine`). Filtrarea rulează în fundal,
iar părăsirea paginii anulează afișarea rezultatului vechi. Domeniul este arhiva
încărcată: cel mult 1.000 intrări din fiecare sursă (provider, inbox local,
arhivă locală de ieșire). Nu promitem căutare în tot istoricul nelimitat.
Rezultatele sunt în ordine descrescătoare a datei; OK deschide textul integral.
O simplă căutare nu marchează conversațiile ca citite.

Redirecționează copiază corpul în editor și lasă destinatarul gol. Dacă există
o ciornă, utilizatorul alege păstrarea ei sau înlocuirea; nu se suprascrie implicit.
Pentru texte peste limita de 1.000 caractere, redirecționarea este refuzată cu
explicație, iar copierea integrală rămâne disponibilă. Se păstrează alegerea SIM,
segmentarea și confirmarea explicită înainte de trimitere.

Clipboard-ul Android primește exact textul selectat sau adresa sa (un expeditor
alfanumeric este copiat ca atare). Nu este o arhivă permanentă Leo. Verificat pe
telefon: căutare fără diacritice, deschiderea rezultatului, editor de redirecționare
cu destinatar gol, blocarea trimiterii demo, copiere text/adresă și lipire în
editor. Preferințele reale de ciornă/trimitere au rămas identice în proba demo.

### Corecție: mesajele trimise nu apăreau

Confirmarea veche elimina corpul din preferințele temporare fără să-l salveze
local. La audit, tabela SMS din providerul Android era goală. Mesajele vechi ale
căror corpuri au fost eliminate nu pot fi reconstruite din simpla stare „trimis”.

`SmsStore` are acum schema 3: inbox-ul existent rămâne intact, iar tabela
`outgoing` păstrează tokenul, destinatarul, textul, data, SIM-ul și starea.
Înregistrarea se salvează **înaintea** cererii modemului. Dacă salvarea inițială
eșuează, nu se trimite SMS-ul. Callback-urile multipart actualizează aceeași
înregistrare: trimis numai dacă toate segmentele au reușit; eșuat la finalizarea
cu eroare; altfel rămâne în așteptare. „Trimis” confirmă operația de trimitere,
nu livrarea la destinatar.

Rezultatul se arhivează înainte de ștergerea preferințelor temporare. Eșecul
arhivării păstrează datele pentru recuperare. Callback-urile duplicate nu dublează
istoricul, iar ciorna se șterge numai dacă este încă identică mesajului trimis.
Starea aflată deja în așteptare în versiunea veche poate fi importată, dacă textul
încă există; data originală poate lipsi. Nu se retrimite nimic automat.

Conversația se actualizează la schimbarea confirmărilor și la revenirea în aplicație.
Arhiva locală este citită și dacă providerul Android nu răspunde. Dacă există și o
copie în provider, se încearcă asocierea unu-la-unu după adresă, text, SIM și dată
(fereastră de 10 secunde). Nu există încă un identificator comun garantat pentru
toate implementările de provider.

Validare: 20 verificări pe SQLite Android și receiver-ul de producție, într-un
director `/tmp` separat, cu notificările înlocuite de un stub de test. Include
migrare din schema 2, persistență, Unicode/SIM, confirmări multipart, duplicate,
eșec parțial, ciornă nouă și indisponibilitatea stocării. Proba nu include
`SmsManager` și nu folosește datele reale ale aplicației. Separat, interfața a
afișat trei înregistrări locale marcate DEMO (trimis/eșuat/în așteptare), apoi
numai acele înregistrări au fost eliminate. Nu s-a trimis un SMS real nou pentru
această corecție; proba radio completă rămâne de repetat.

```text
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-sms-archive-probe.sh
adb -s 695d push android81/output/leo-sms-archive-probe.jar /tmp/leo-sms-archive-probe.jar
python android81/framework-command.py /system/bin/env CLASSPATH=/tmp/leo-sms-archive-probe.jar /system/bin/app_process /system/bin ro.leo.visual.SmsArchiveProbe /tmp/leo-sms-probe-UNIQUE
```

Folosește un nume nou de director la fiecare rulare. Proba refuză un director
existent și șterge doar fișierele proprii. Stub-ul din `sms-tests` este compilat
exclusiv în probă; APK-ul folosește receiver-ul de notificări original.

## Alarme și temporizator

- Până la 32 de alarme locale, cu oră, minut, nume și repetare zilnică opțională.
  Alarmele fără repetare se dezactivează după declanșare. Ștergerea cere confirmare.
- AlarmManager `setAlarmClock` programează ora locală; timpul și fusul orar sunt
  recalculate la broadcasturile sistemului. Receiver-ul reface programările la boot.
- Temporizator unic între o secundă și 24 de ore; termen monotonic în aceeași
  pornire, cu reper wall-clock pentru recuperare după reboot.
- La declanșare pornește un serviciu foreground, cu notificare, wake-lock limitat,
  ecran vizibil și redare pe fluxul audio ALARM. Dacă fișierul implicit nu poate fi
  deschis, este folosit un ton de rezervă. Volumul sistemului nu este forțat.
- OK, soft stânga sau Înapoi opresc semnalul; există și acțiune Oprește în notificare.
  Oprire automată după trei minute. Nu există încă amânare/snooze.
- Temporizatorul continuă când activitatea este închisă. Force-stop, ștergerea
  datelor și oprirea completă a telefonului au regulile Android obișnuite; nu se
  promite alarmă cât timp telefonul este oprit.

Verificat: 14 cazuri JVM pentru ore viitoare, trecerea în ziua/anul următor,
limitele duratei și intrări invalide. Temporizatorul de 12 secunde a declanșat
receiver-ul, serviciul foreground și ecranul de alarmă după Home; OK a oprit
serviciul. Alarma RTC creată din formular s-a declanșat după închiderea ecranului
și terminarea procesului Leo: receiver-ul a rulat într-un proces nou, a pornit
serviciul și a dezactivat alarma fără repetare. OK a oprit-o.
Utilizatorul a confirmat că temporizatorul de 12 secunde s-a auzit după revenirea
la Home. Aceasta validează ruta sonoră folosită de serviciul comun al alarmelor;
nu confirmă toate volumele, accesoriile sau alarmele în timpul unui apel.
Recuperarea după reboot și cazurile DST necesită probe distincte.
Modul mouse și meniul overlay sunt închise la deschiderea ecranului de alarmă,
pentru ca OK să oprească semnalul în loc să injecteze un clic.
Alarma de probă a fost ștearsă ulterior; temporizatorul este inactiv.

## Permisiuni și integrare

`READ_CONTACTS`, `WRITE_CONTACTS`, `READ_CALL_LOG`, `CALL_PHONE`, `READ_SMS`,
`RECEIVE_SMS`, `SEND_SMS`, `READ_PHONE_STATE`; pentru alarme, `WAKE_LOCK` și
`RECEIVE_BOOT_COMPLETED`. Nu se adaugă INTERNET sau acces general la stocare.
Datele rămân în providerii Android sau în stocarea privată a aplicației.

`activate-visual-home.py` acordă permisiunile pentru această imagine de dezvoltare
și adaugă atomic blocul lor în scriptul existent de pornire. `/system` revine
read-only; boot/recovery și datele utilizatorului nu sunt rescrise.
Și sursa `leo-first-setup.sh` include blocul pentru imaginile viitoare.

```sh
bash android81/verify-alarm-math.sh
bash android81/verify-calculator-engine.sh
bash android81/build-visual-shell.sh
```

Nu publicăm agenda, istoricul apelurilor, mesajele reale, bazele private sau loguri
brute. Capturile selectate trebuie să conțină numai datele sintetice ale probelor.

## Capturi de pe dispozitiv

| Contact sintetic găsit | Telefon Leo |
| --- | --- |
| ![Căutare contact de probă](screenshots/leo-contacts-search.png) | ![Telefon](screenshots/leo-phone.png) |

| Ciornă sintetică, fără trimitere în această captură | Alarmă de probă |
| --- | --- |
| ![SMS de probă](screenshots/leo-messages-compose.png) | ![Alarmă declanșată în proces nou](screenshots/leo-alarm.png) |
