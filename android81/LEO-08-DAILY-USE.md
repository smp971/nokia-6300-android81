# Leo 0.8 · mai comod în fiecare zi

27 octombrie 2026 · Android 8.1 / API 27 · Nokia 6300 4G TA-1286.

## Cele șase direcții

| Direcție | Ce aduce etapa | Cum se folosește |
| --- | --- | --- |
| Contacte | Detectarea aceluiași număr în contacte diferite, cu acces la fiecare contact. Grupele, favoritele și apăsarea lungă pe 2–9 pentru apelare rapidă se păstrează. | Contacte → Opțiuni → Numere duplicate. Doar analiză și consultare; nu unește și nu șterge automat contacte. |
| Mesaje | Fixare, arhivare, filtrarea expeditorilor în Leo și extragerea numerelor, adreselor email și linkurilor. | Conversație → Opțiuni. Din lista Mesaje → Opțiuni → Main / Archived / Filtered. Fixatele apar primele. Arhivarea păstrează SMS-urile; filtrarea ascunde notificările Leo și separă conversația, fără să blocheze livrarea operatorului sau notificările altor aplicații. |
| Calendar | Card Agenda pe Home, cu următoarea instanță de eveniment, inclusiv recurențe. | `*` schimbă cardul, `#` îl deschide. Poate fi ascuns/reordonat din Personalizare → Cardurile Home. Aniversările, evenimentele recurente și mementourile existente se păstrează. |
| Muzică | Poziție separată pentru fiecare piesă de minimum 10 minute; maximum 200 de poziții locale. Egalizator cu presetări oferite de efectul audio disponibil. Coperțile, aleatoriul, repetarea și reluarea sesiunii existente se păstrează. | Player → Opțiuni → Auto resume / Equalizer. Aproape de sfârșit, poziția se resetează. Egalizatorul se atașează exclusiv sesiunii Leo; lipsa efectului sau refuzul rutei sunt raportate fără oprirea playerului. |
| Personalizare | Patru profiluri personale pentru culori, dimensiunea textului, opțiuni de afișare și fundal, cu revenire la aspectul anterior. | Personalizare → My profiles. Colecțiile cu previzualizare și scurtăturile Home rămân disponibile. Aplicarea profilului păstrează ordinea aplicațiilor și scurtăturile. |
| Aplicații | Listă de pachete instalate, spațiu ocupat, permisiuni periculoase, lansare, dezinstalare și selecția aplicațiilor SMS/Telefon implicite. | Personalizare → App center; sau Aplicații Android → Opțiuni → Informații aplicație. Lista și acțiunile Leo sunt accesibile din taste. Schimbarea rolurilor păstrează confirmarea Android; celelalte roluri folosesc pagina Android. |

Interfața nouă are traduceri română/engleză. Versiunea shell-ului și a tastaturii
este `0.8.0-dev / cod 8`; comportamentul tastaturii nu a fost modificat în această etapă.

## Date și limite

- Numerele duplicate sunt comparate după normalizare și echivalența `00`/`+`.
  Nu se ghicește țara unui număr local și nu se confundă numere cu ultimele cifre identice.
- Expeditorii alfanumerici SMS își păstrează identitatea. Filtrarea este locală Leo,
  reversibilă; nu este o listă de blocare în modem sau operator.
- Numerele din SMS deschid editorul Telefon, fără apel automat; emailul deschide
  compunerea Leo, fără trimitere. Linkurile web se deschid numai la selecția utilizatorului.
- Agenda citește CalendarProvider pe fir de lucru, cel mult o dată pe minut la
  afișare; nu face interogări de provider în desenarea interfeței și nu accesează rețeaua.
- Progresul pieselor se salvează la pauză/schimbare și periodic. Nu este un egalizator
  global și nu se garantează suportul efectelor pe toate ieșirile audio.
- Centrul de aplicații nu permite eliminarea aplicațiilor de sistem, a pachetelor Leo,
  IMS, tastaturii active sau aplicațiilor SMS/Telefon implicite. Permisiunile protejate
  și ale aplicațiilor de sistem se consultă prin pagina Android. Comenzile de modificare
  sunt limitate și executate numai după o acțiune explicită; funcțiile necesită helperul
  root existent în acest port. Refuzurile sistemului sunt afișate.
- Spațiul APK este disponibil direct. Datele/cache folosesc StorageStats dacă este
  permis, apoi o citire locală prin helperul root; dacă accesul nu este disponibil,
  informația este marcată indisponibilă.
- Backup-ul selectiv include organizarea SMS și preferințele playerului în categoria
  Preferințe aplicații. Cele patru fișiere de profil vizual și pozițiile per piesă rămân
  locale; ele nu sunt incluse automat în export. Fundalul și aspectul curent continuă
  să fie incluse în backup-ul obișnuit.

## Verificare

- `DailyUse08Tests`: 34 verificări locale trecute pentru normalizare, organizare SMS,
  extragerea linkurilor, cardurile Home, progresul pieselor, profiluri, protecția
  aplicațiilor, permisiuni și deschiderea noilor pagini.
- `PhoneTabsTests`: 10 verificări trecute. Pagina încarcă imediat antetul filei;
  o apăsare Jos în timpul încărcării este păstrată până la apariția listei.
- `SixPolishTests`: 26 contracte trecute; testul de clic se raportează la categoria inițială și o restaurează.
- `verify-ims-runtime.py`: voce IMS înregistrată și date LTE validate.

Probele de navigare țin ecranul aprins: un ecran adormit producea vederi fără
layout și o eroare falsă de focus în verificarea anterioară.

Suita folosește preferințe și fișiere izolate pentru arhivare, filtrare, profiluri,
poziții de redare și restaurarea aspectului. Permisiunea Cameră este acordată și
revocată numai pachetului `ro.leo.visual.tests`, iar starea inițială este restaurată.
Nu se trimit SMS/emailuri, nu se inițiază apeluri, nu se șterg contacte și nu se
rescriu boot/recovery sau drivere în această etapă.

Egalizatorul are protecție verificată pentru o sesiune inactivă; calitatea efectului
și reluarea unei piese reale rămân de verificat prin ascultare. Revenirea temelor și
păstrarea scurtăturilor au probe în context izolat; nu au înlocuit aspectul personal.
