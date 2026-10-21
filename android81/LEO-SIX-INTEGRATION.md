# Leo · șase funcții care leagă aplicațiile

21 octombrie 2026 · Android 8.1 / Nokia 6300 4G TA-1286.

## Acces din taste

- **Home → 1** sau Opțiuni → Caută în Leo: aplicații, contacte, notițe și muzică.
  Scriere normală ori cifre T9; jos mută selecția în rezultate. Căutarea este locală,
  la cerere, cu limite de scanare: 2000 rânduri de contacte și 2000 piese, maximum
  20 rezultate per categorie. Fără index persistent sau serviciu nou.
- **Contacte → Apelează / Mesaj SMS**: alege numărul când persoana are mai multe.
  SIM-ul se alege în continuare în Telefon/Mesaje, înaintea confirmării trimiterii.
  Ecranul de apel încarcă fotografia contactului pentru numărul identificat.
- **Mesaje → Opțiuni → Previzualizare notificări**: text opțional, ascuns implicit.
  Notificarea este privată; Răspunde în Leo deschide compozitorul, fără trimitere
  automată. Conversațiile cu mesaje necitite sunt marcate vizibil.
- **Notificări**: grupare implicită pe aplicații; intră cu OK pentru notificările
  acelui grup. „Toate” comută la lista completă, iar Opțiuni permite regruparea.
  Deschiderea, acțiunile și eliminarea individuală rămân disponibile din taste.
- **Personalizare → Accente și teme pe aplicații**: șase accente, resetare la
  accentul temei, teme individuale pentru aplicațiile enumerate și mostră înainte
  de aplicare. Telefonul și ecranul de apel păstrează Leo Night implicit. Stilurile
  se reîncarcă la redeschiderea aplicației. Imaginea Home poate fi ascunsă la blocare.
- **Backup Leo → Contacte și SMS · selectiv**: export și import într-un fișier
  `.leo-people.json`, separat de backup-ul preferințelor.

## Navigare circulară

Sus de pe primul element ajunge la ultimul, iar jos de pe ultimul revine la primul.
Regula se aplică listelor Leo comune, meniurilor de opțiuni, Notițelor, listei de
aplicații Android din Leo și meniului principal. Galeria are și trecere circulară
între imagini. Câmpurile de text, deplasarea imaginii mărite și controalele audio
nu sunt transformate în liste. Aplicațiile Android externe își păstrează navigarea.

## Livrarea SMS

SMS-urile noi cer un raport per segment. „Trimis” înseamnă acceptarea trimiterii;
„Livrat · raport operator” apare numai când toate segmentele au raport de succes.
Un raport temporar nu confirmă livrarea; eroarea permanentă este afișată distinct.
Redirecționarea fără confirmarea destinatarului rămâne „livrare neconfirmată”.
Callback-urile duplicate nu dublează istoricul. Nu se retrimite automat niciun SMS.

Arhiva locală migrează la schema 5 prin adăugare de coloane: stări per segment,
stare de livrare și timestamp pentru mesajele importate. Testele folosesc stări
simulate; livrarea reală depinde de operator și nu a fost verificată în această etapă.

## Backup selectiv de contacte și SMS

Exportul permite contacte, SMS sau ambele. Importul începe cu ambele selecții
oprite; alegi datele, verifici duplicatele și confirmi explicit adăugarea.

Contactele includ numere/tipuri, nume, favorit și fotografie micșorată. Se importă
ca date locale; nu reprezintă sincronizare cu un cont. Sunt omise doar potrivirile
exacte de nume normalizat și set de numere normalizate. Alte nume, numere scrise cu
prefixe diferite ori contacte existente parțial pot produce intrări distincte.
Nu sunt eliminate automat contactele originale și nu se schimbă înregistrarea SIM.

SMS-urile din furnizorul Android și arhiva Leo sunt exportate, apoi restaurate
**în arhiva Leo**, ca mesaje istorice citite. Nu sunt scrise în aplicația SMS stock
și nu sunt retrimise. Identitatea de deduplicare include adresa normalizată,
timestamp-ul, direcția, textul și ID-ul abonamentului. ID-ul SIM este păstrat, fără
remapare automată pe alt aparat. Trimiterile neterminate devin istoric eșuat la import.

Limite explicite: 500 contacte cu cel mult 10 numere fiecare, 2000 SMS, 8 MiB.
Depășirea limitelor oprește exportul în loc să trunchieze datele. Fișierul este
personal, **necriptat**, versionat și verificat prin SHA-256 împotriva coruperii.
Nu include MMS, email, conturi, parole, jurnal de apeluri sau câmpurile VCF complete.

Înainte de import, o copie a datelor locale selectate se salvează în
Documente Leo/People-rollback. Fiecare contact este inserat printr-un lot atomic,
iar SMS-urile într-o tranzacție SQLite. Importul complet nu este o singură tranzacție
între furnizorii Android: la o eroare pot rămâne contacte deja adăugate. Interfața
explică acest lucru; reîncercarea omite duplicatele deja importate.

## Verificare și recompilare

`build-visual-shell.sh` compilează aplicația; `build-six-feature-tests.sh` compilează
probele. Runner nou: `ro.leo.visual.tests/ro.leo.visual.SixIntegrationTests`.
Sunt folosite contacte, SMS, notițe și notificări fictive locale, eliminate la final.
Nu se inițiază apeluri, SMS, emailuri sau redare audio în aceste probe.

Sunt verificate deduplicarea la import repetat, restaurarea fotografiei și a numerelor,
selecția datelor, checksum-ul și validarea înainte de scriere, rapoartele SMS simulate,
căutarea T9/diacritice, notificările grupate/eliminarea individuală, temele și
navigarea circulară din taste. Fotografia într-un apel real și rapoartele de livrare
ale operatorului rămân de verificat separat; probele de apel nu sunt repetate aici.

## Material și Media curentă

Interfața folosește un stil vizual inspirat de Material, desenat nativ în
componentele Leo existente: suprafețe întunecate, accent turcoaz, colțuri rotunjite
și titluri mai ferme. Nu este o migrare la biblioteca Material Components sau
la Compose. Tema „Material · Noapte” poate fi schimbată din Fundaluri și teme.

„Media curentă”, accesibilă cu 0 din Home, înlocuiește lista veche cu un dashboard
pentru Muzică și Radio FM: copertă ori disc original desenat vectorial, titlu,
poziția în coadă, timp/progres, volum și comenzi explicite. FM afișează frecvența,
numele RDS disponibil și starea radioului. Nu se adaugă o nouă sesiune audio ori
un vizualizator simulat; redarea continuă prin serviciile existente.

- OK / 5: redare-pauză pentru muzică; oprire pentru FM activ.
- Stânga/dreapta ori 4/6: piesa/postul anterior și următor.
- Sus/jos: volum; *: schimbarea sursei; #: playerul sursei.
- Opțiuni: deschidere player, schimbare sursă, anterior/următor și oprire.

Când nu există o sesiune, comanda de redare deschide playerul pentru alegerea
conținutului. Timpul se actualizează o dată pe secundă în redarea muzicală, iar
pagina nu este reconstruită periodic. Actualizarea se oprește când ecranul este
ascuns. Nu este un agregator al tuturor MediaSession din aplicațiile Android externe.

`MediaDashboardTests` verifică separat cele două pagini, schimbarea sursei,
comanda OK și izolarea volumului într-un mod demo explicit, fără audio real.
Capturile publicate sunt marcate DEMO. Verificările de redare reală din etapele
anterioare rămân documentate separat.

Rezultatele etapei: **34 probe de integrare + 19 probe de date + 8 probe UI/din
taste + 6 probe ale dashboard-ului = 67 verificări locale trecute**. Cele șase
probe media folosesc demonstrația silențioasă; nu reprezintă o nouă confirmare
fizică a redării audio. Selecția rapidă în Fișiere citește acum poziția curentă
a listei înainte de marcarea cu 5, fără a aștepta callback-ul de desenare.
Baza APK privilegiată a fost sincronizată, cu sistemul remontat doar pentru
copiere și readus read-only. Nu s-au scris imagini boot/recovery și nu s-au
schimbat drivere. Bufferul crash era gol la încheierea verificărilor.

### Playerul muzical, în același stil

Playerul Muzică Leo folosește acum aceleași suprafețe Material, copertă de
76×76 în coordonate logice, titlu pe două rânduri, progres și comenzi vizibile
ca Media curentă. Vizualizatorul FFT existent a fost păstrat, într-un card
separat; continuă să folosească sesiunea playerului, fără captură de microfon.

Comenzile anterioare rămân: OK redare/pauză, stânga/dreapta deplasare de 10 secunde,
sus/jos volum, soft stânga Opțiuni și soft dreapta Bibliotecă. Au fost adăugate
4 = piesa anterioară, 5 = redare/pauză, 6 = piesa următoare; apăsarea lungă
nu repetă schimbarea piesei. Coada, shuffle, repeat și salvarea listelor folosesc
în continuare serviciul existent. Nu s-au schimbat decodorul ori ruta audio.

Extinderea `MediaDashboardTests` verifică cele două dashboard-uri și noul player:
**9 verificări silențioase**, inclusiv împărțirea titlului lung, deschiderea
playerului nativ și comenzile într-o sesiune goală. Captura nouă este demonstrativă,
cu titlu fictiv și fără redare. Vizualizatorul activ și redarea reală nu au fost
retestate fizic în această modificare vizuală.
