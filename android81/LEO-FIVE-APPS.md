# Leo · cinci aplicații mai utile

Etapă din 21 octombrie 2026, pentru Android 8.1 pe Nokia 6300 4G TA-1286.
Aplicațiile rămân native Android și controlabile din taste; folosesc furnizorii
Android existenți pentru contacte și SMS.

| Aplicație | Ce aduce etapa |
| --- | --- |
| Contacte | O singură intrare pentru fiecare persoană, numere multiple cu tip, editarea unui număr fără pierderea celorlalte, fotografie din Galerie, favorite și previzualizare înainte de import VCF. |
| Mesaje | Contor Android GSM-7/Unicode în compozitor, SIM și stare pentru mesajele trimise în lista conversațiilor; căutarea conversației rămâne disponibilă. |
| Galerie | Miniaturi, zoom 1×/2×/4× cu OK, deplasare cu D-pad când imaginea este mărită, tastele 1/3 pentru imaginea precedentă/următoare. Poate primi imagini direct din Fișiere sau selecta fotografia unui contact. |
| Fișiere | Tasta 5 marchează fișiere, până la 100; căutare, sortare după nume/dată/mărime, spațiu liber și metadate. Imaginile, muzica și filmele se deschid în aplicațiile Leo. |
| Backup Leo | Export local, previzualizare și restaurare pentru preferințele acceptate, teme/fundal, ordinea aplicațiilor, notițe și dicționarul personal al tastaturii. Disponibil în Aplicații și Setări → Stocare. |

## Backup: ce salvează și cum revine

Fișierul `.leo-backup.json` are format versionat și sumă SHA-256 pentru detectarea
coruperii. Este un fișier personal necriptat, care poate fi copiat pe PC sau SD.
Nu este imagine de ROM și nu include conturi, parole, contacte, SMS, alarma sistemului
sau toate preferințele Android. Agenda are separat import/export VCF.

Grupurile acceptate sunt `appearance`, `dialer-favorites`, `speed-dial`,
`notes-draft` și `files-ui`, plus notițe, fundal și setările compatibile ale tastaturii.
Cheile și tipurile sunt validate înainte de restaurare; căile din fișier nu sunt
folosite ca destinații. Dimensiunea maximă este 8 MiB, cu limite separate pentru
notițe și imagine. Exportul poate fi salvat în Documents/LeoBackups pe telefon/SD
sau în documentele private Leo.

Restaurarea cere confirmare, creează întâi o copie locală de revenire și un jurnal.
La eroare încearcă revenirea automată; la o întrerupere, jurnalul este recuperat
când se deschide din nou Backup Leo. După succes se revine la Home pentru a reîncărca
preferințele. Transferul dicționarului folosește un provider IME protejat prin
permisiune de semnătură și verificarea pachetului apelant.

## Probe pe aparat

- 19 verificări de logică/date: GSM-7 și Unicode, numere de contact și fotografie,
  VCF cu două numere, providerul protejat, dicționar, checksum, respingerea cheilor
  necunoscute și restaurarea efectivă a unui set local de preferințe/notițe.
  Starea inițială este restaurată la final.
- 8 verificări UI/din taste: contact unic cu două numere, detaliu stabil în fața
  unei încărcări întârziate, marcarea a două fișiere, deschiderea imaginii în Galerie,
  decodare, zoom/deplasare, ciornă SMS locală și previzualizarea backup-ului.
- Capturile folosesc contacte, imagini și text demonstrativ; fixture-urile sunt
  eliminate la final. Niciun apel, SMS sau email nu este trimis de aceste probe.

Scenarii neacoperite aici: confirmarea de livrare SMS la destinatar, SMS pe SIM 2,
importul tuturor variantelor VCF existente, restaurarea după o întrerupere fizică
în timpul scrierii și o compilare completă pe un PC curat. Starea „trimis” reprezintă
acceptarea de către rețea, nu confirmarea de livrare. Redarea media nu a fost
retestată în această etapă; integrarea imaginii Fișiere → Galerie a fost verificată.

## Recompilare

Mediul și intrările AOSP sunt descrise în documentația de recompilare a proiectului.
În mediul configurat, se rulează `build-keypad-ime.sh`, apoi `build-visual-shell.sh`.
La instalare, tastatura nouă trebuie actualizată înaintea shell-ului, deoarece ea
definește permisiunea `ro.leo.keypad.MANAGE_BACKUP`. Actualizările păstrează datele.

`build-six-feature-tests.sh` compilează probele. Runner-ele noi sunt
`ro.leo.visual.FiveAppsTests` și `ro.leo.visual.FiveAppsUiTests`, în APK-ul
`ro.leo.visual.tests`. Acestea cer mediul privilegiat al aparatului de dezvoltare.
