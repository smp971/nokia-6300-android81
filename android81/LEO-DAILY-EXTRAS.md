# Leo · lucruri mici care fac telefonul al tău

21 octombrie 2026 · Nokia 6300 4G TA-1286 · Android 8.1 / API 27.

Șase completări pentru folosirea zilnică, integrate în aplicațiile existente.
Interfața rămâne nativă și controlabilă din taste.

| Unde | Ce primești | Cum îl folosești |
| --- | --- | --- |
| Home | Acces rapid, până la 12 aplicații favorite, ultimele 8 aplicații Leo lansate, configurarea celor patru scurtături | Apăsare scurtă pe **5**, sau Meniu fizic → Acces rapid Home. Apăsarea lungă pe 5 păstrează apelarea rapidă existentă. |
| Ceas | Zile de repetare individuale pentru alarme, amânare de 5/10/15 minute, oraș preferat în primul loc | Alarme → Opțiuni → Alarmă nouă / Editează; durata amânării este în Opțiunile filei Alarme. Glob → Opțiuni → Mută orașul în primul loc. |
| Notițe | Fixare sus, căutare text/T9, liste de bifat și export TXT | În listă: **#** pentru opțiuni. În editor: soft stânga → listă de bifat sau bifarea rândului unde este cursorul. Salvarea este prima opțiune a editorului. |
| Galerie | Slideshow cu interval 3/5/10 secunde, rotire de afișare 90°, partajare | Deschide o imagine → Opțiuni. OK oprește slideshow-ul; ieșirea din aplicație îl oprește automat. Partajarea deschide selectorul Android, inclusiv Bluetooth când există o țintă compatibilă. |
| Backup | Favorite muzicale, favorite/ascunderi aplicații Android, meniu rapid, orașe și amânare Ceas, Calendar local Leo; restaurare pe categorii | Backup Leo → Restaurează → previzualizare → Restaurează → bifează categoriile → Aplică → confirmare. |
| Stocare | Categorii de fișiere, top 20 fișiere mari, deschiderea folderului și locație pentru exporturi/înregistrări noi | Setări Leo → Stocare Leo → Analiză fișiere sau Salvare Leo. |

## Date, limite și resurse

**Home.** Recentele sunt lansările făcute prin Home Leo, fără citirea istoricului
sarcinilor Android. Favoritele și recentele sunt preferințe locale; aceeași aplicație
apare o singură dată. Scurtăturile folosesc în continuare ID-urile stabile din catalog.

**Ceas.** Selecția zilelor folosește un bit pe zi, de luni până duminică. Nicio zi
bifată înseamnă o singură declanșare. Alarmele zilnice vechi sunt interpretate drept
șapte zile selectate. Următoarea alarmă din Home/blocare respectă aceleași zile.
Amânarea este separată de recurență și se anulează la editare, dezactivare sau ștergere.
După o repornire se reprogramează doar amânările încă viitoare. Timerul rămâne separat.
Verificarea sonoră la termen, după repaus prelungit și după reboot rămâne necesară.

**Notițe.** Listele sunt text lizibil: `[ ]` și `[x]`, fără format proprietar ascuns.
Textul T9 este potrivit cu aceeași normalizare românească folosită la căutarea Leo.
Fixarea se păstrează în `notes.json` și în backup. Exportul creează un fișier nou,
cu nume unic, în `Notes` din locația de salvare aleasă. Limitele existente sunt
100 de notițe și 8192 de caractere per notiță. În listă nu se pierd intrările care
nu corespund căutării; „Arată toate” elimină filtrul.

**Galerie.** Rotirea este numai pentru vizualizare: fișierul original rămâne identic.
Slideshow-ul așteaptă decodarea imaginii și apoi intervalul ales, fără coadă de
cadre, efecte animate sau serviciu în fundal. Când deschizi Opțiuni, slideshow-ul
se oprește. Transferul Bluetooth complet al unei imagini nu a fost probat în această etapă.

**Stocare.** Analiza pornește la cerere, pe worker, în locația publică sau Documente
Leo aleasă. Sunt maximum 12000 de intrări și aproximativ 10 secunde de lucru;
raportul indică o analiză parțială și numărul de directoare inaccesibile.
Nu sunt urmărite legături simbolice. Se clasifică după extensie; valorile reprezintă
fișierele accesibile parcurse, nu întregul consum al partiției ori spațiul aplicațiilor.
Capacitatea/liberul volumului rămân afișate separat. Nu există ștergere automată.
„Salvare Leo” afectează exporturile Notițe și înregistrările noi; înregistrările
existente nu sunt mutate. Descărcările DownloadManager păstrează folderul Download.
SD-ul folosește dosarele private ale aplicației, care pot fi șterse la dezinstalare.
Selectarea unei locații verifică scrierea printr-un fișier temporar; o locație SD
absentă produce eroare, fără redirecționare ascunsă către alt volum.

## Backup versiunea 2

Exporturile noi sunt `leo-backup`, versiunea **2**, cu SHA-256 și limită de 8 MiB.
Cititorul acceptă și arhivele vechi versiunea 1. Grupurile noi absente dintr-o
arhivă veche sunt păstrate la restaurare. Conturile, parolele, fișierele muzicale,
APK-urile și mediile propriu-zise nu sunt incluse: favoritele sunt referințe la URI-uri
sau componente Android și pot deveni indisponibile pe alt sistem.

Categoriile selectabile sunt Aspect/Ceas, Favorite, Notițe, Tastatură și Calendar.
Categoriile nebifate își păstrează valorile actuale. Arhiva de Contacte/SMS rămâne
separată. Fișierele de backup sunt personale și necriptate.

Calendarul include cel mult 500 de evenimente **din Calendar Leo**, cu titlu,
început/sfârșit sau durată, fus orar, zi întreagă, repetare simplă și maximum cinci
notificări ALERT per eveniment. Sunt acceptate regulile generate de editorul Leo:
DAILY, WEEKLY și MONTHLY; excepțiile de recurență și conturile sincronizate nu sunt
portate prin această arhivă. Restaurarea Calendarului înlocuiește evenimentele
locale Leo într-o singură tranzacție a furnizorului și reprogramează notificările;
ID-urile evenimentelor se pot schimba. Nu atinge calendarele altor conturi.

Validarea are loc înaintea aplicării. Se păstrează copia de revenire și jurnalul
existent pentru recuperarea unei restaurări întrerupte. Restaurarea pe mai multe
subsisteme folosește acest mecanism de rollback, nu o tranzacție globală Android.

## Probe pe aparat

`NextSixTests` folosește date demonstrative și un calendar separat de probă pentru
înlocuirea tranzacțională. Nu înlocuiește Calendar Leo real. Verifică inclusiv
aplicarea reală a categoriei Favorite, copia de siguranță și păstrarea Calendarului
și Notițelor nebifate. Preferințele folosite de probe sunt restaurate la final.

- 35 verificări noi: zile, amânare/programare fără sunet, T9, fixare, comanda de
  rotire din taste, slideshow, oprirea lui la ieșire, categorii de fișiere,
  compatibilitate backup vechi, seturi de favorite, selecție și restaurare.
- 10 verificări existente Ceas/DST/Meteo/catalog.
- Compilare reușită, APK instalat, baza privilegiată sincronizată și `/system`
  read-only. Fără modificări de kernel, boot/recovery, audio sau USB.

Cele șase capturi `leo-next-*.png` sunt de pe telefon, cu date demonstrative.
Alarma din captură este doar un model vizual; nu a fost programată să sune.
Aceste probe nu înlocuiesc verificarea fizică a sunetului alarmei, a repornirii,
a unui transfer Bluetooth sau a fiecărui model de card SD.

Recompilare: `build-visual-shell.sh`, apoi `build-six-feature-tests.sh`.
Runner: `ro.leo.visual.tests/ro.leo.visual.NextSixTests`.
