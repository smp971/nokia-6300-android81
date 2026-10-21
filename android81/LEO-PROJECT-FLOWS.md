# Leo · aplicațiile lucrează împreună

21 octombrie 2026 · prima versiune numerotată a aplicațiilor: **0.1.0-dev / cod 1**.

Etapa este inclusă acum și în pachetul **0.2.0-dev**: [Organizator Leo](LEO-ORGANIZER.md).

**Stare: implementat și compilat pe PC. Instalarea și probele Android sunt în
 așteptare: Nokia nu este disponibil în ADB în această etapă.** Nu sunt publicate
capturi noi pentru interfețe care nu au fost încă inspectate pe aparat.

| Funcție | Implementare pregătită | Acces |
| --- | --- | --- |
| Fișiere vizuale | Miniaturi proporționale în liste, progres pe octeți la copiere și verificare SHA-256, cale/MIME/acces în proprietăți | Fișier → Opțiuni; 5 păstrează selecția multiplă |
| Partajare Leo | Selector nativ de destinații compatibile, același pentru imagini, muzică, înregistrări, notițe și fișiere | Partajează; în Fișiere există și Deschide cu |
| Contacte | Grupe locale Leo, aniversare cu/fără an, eveniment anual în Calendar, legături către conversații și istoricul filtrat | Contact → Grupe, aniversare și legături; Grupe locale și în meniul listei |
| Home | Ordinea și activarea cardurilor; card cu până la cinci contacte favorite și fotografii; control media din card | Personalizare → Ordinea și selecția cardurilor Home; 4/5/6 pe cardul Media |
| Aplicații | Dimensiunea APK-urilor de bază și split, versiune, permisiuni, dezinstalare protejată; previzualizare APK local | Setări → Aplicații; Fișiere → APK → Instalează |
| Livrare | Două APK-uri, versiune, manifest SHA-256, verificarea dependențelor, build și instalator explicit | [Pachetul aplicațiilor](RELEASE-APPS.md) |

## Fișiere și partajare

Miniaturile folosesc cache-ul comun limitat la 24 de bitmap-uri, decodate în worker,
la maximum 64 px. Navigarea invalidează cererile vechi de listare. Încărcarea unei
miniaturi actualizează rândurile, fără reconstruirea antetului.

Copierea păstrează limitele existente: fișiere obișnuite, maximum 2 GiB fiecare,
fără suprascriere, verificare a sursei și SHA-256 înaintea publicării destinației.
Progresul indică separat copierea și cele două citiri pentru verificare. Mutarea
șterge sursa doar după verificarea copiei. Actualizarea vizuală este limitată în
worker la aproximativ una la 160 ms, plus terminarea fazelor. Pe durata transferului,
Înapoi anunță operația în curs; această pagină nu oferă anulare la jumătatea mutării.
O oprire forțată a procesului poate lăsa un fișier temporar `.leo-copy-*.part`.

Selectorul comun citește aplicațiile instalate care declară tipul MIME și sunt
exportate/accesibile. Nu presupune existența Bluetooth sau a unei aplicații de email.
Nu transmite nimic la simpla deschidere. La alegerea destinației, îi acordă acces
**doar pentru citire**, prin URI și ClipData. Ecranul următor aparține aplicației
alese și poate avea nevoie de modul mouse dacă nu este adaptat tastelor.

Notițele se pregătesc ca TXT-uri temporare în cache-ul privat partajabil; copiile
vechi de peste o zi pot fi curățate la următoarea pregătire. Fișierele media sunt
sursele originale, nu WMA-ul decodat ori o copie cu pierderi. Înregistrarea trebuie
oprită înaintea partajării. Destinațiile externe și transferul Bluetooth complet
nu sunt încă validate pentru acest selector.

## Contacte și Calendar

Grupele `Leo · nume` sunt stocate în ContactsProvider. Apartenența și aniversarea
se editează numai pe raw-contacte locale, fără cont sincronizat. Lista grupelor
arată cel mult 100 de intrări, iar lista membrilor cel mult 500 de persoane.
Un contact poate aparține mai multor grupe. Alegerea unei grupe din pagina
contactului adaugă sau elimină apartenența; din lista globală deschide membrii.

Aniversarea acceptă `AAAA-LL-ZZ` sau `--LL-ZZ`, cu validare calendaristică strictă.
Adăugarea în Calendar este separată și cere confirmare: eveniment **anual la 09:00,
durată o oră**, fără notificare nouă. O aniversare pe 29 februarie începe la următorul
an bisect. Repetarea anuală este disponibilă și în editorul Calendarului.

Legătura contact–eveniment folosește cheia lookup a contactului. Repetarea comenzii
actualizează evenimentul existent, fără a crea încă unul; Backup Calendar păstrează
această legătură. După editarea/ștergerea aniversării din contact, Calendarul nu este
modificat automat: actualizează explicit sau șterge evenimentul din Calendar.
Arhiva separată Contacte/SMS și VCF-ul existent nu au fost extinse aici pentru toate
câmpurile de grup/aniversare; nu trebuie considerate copii complete ale acestor date.

Legăturile către conversație și istoric cer alegerea numărului când este cazul.
Deschiderea lor nu inițiază apel și nu trimite SMS.

## Home și aplicații

Ordinea cardurilor folosește ID-uri stabile, independent de ordinea aplicațiilor.
Cardul nou Favorite este implicit ascuns. Fotografiile se încarcă în worker, maximum
cinci la 48 px, când Home revine în prim-plan. OK deschide contactul; 4/6 schimbă
persoana. Cardul Media folosește 4/6 pentru piesa/postul anterior/următor și 5 pentru
pauză/redare Muzică sau oprire FM. Nu pornește automat o sesiune media absentă.
Pe aceste carduri, comenzile scurte sunt prioritare față de Acces rapid/apelare rapidă.

APK-ul ales este verificat ca fișier local în rădăcinile acceptate. Leo arată numele,
pachetul, versiunea, dimensiunea, permisiunile și SHA-256 al certificatului de semnare.
Această informație nu este o analiză antivirus. Instalarea necesită permisiunea de
instalări din Leo și confirmarea instalatorului Android. Nu se instalează silențios.
Pachetele de sistem, Leo Visual și tastatura Leo sunt protejate de acțiunea proprie
de dezinstalare. Pentru alte aplicații urmează confirmarea Android. Pagina de spațiu
Android rămâne sursa pentru date/cache; valoarea nativă reprezintă APK și split-uri.

## Verificare efectuată

- Compilarea shell-ului și a tastaturii pe SDK 27 / Java 8 a trecut.
- Compilarea runnerului `ProjectFlowTests` și a țintei locale de partajare a trecut.
- **10 verificări host**: ordine carduri, date/an bisect și copiere/mutare cu hash,
  progres și refuzul suprascrierii.
- **5 teste Python**: manifest valid, APK modificat, căi nepermise, format necunoscut
  și refuzul instalării fără serial explicit înainte de apelarea ADB.
- Pachetul construit are manifest, două APK-uri și un instalator verificabil offline.

**Nerulate încă:** probele pe Android, verificarea vizuală, accesul URI între UID-uri,
ContactsProvider/CalendarProvider, cardurile Home, instalarea/dezinstalarea prin UI
și confirmarea după reboot. Nu se afirmă că cele șase funcții sunt deja funcționale
pe telefon. Nu au fost inițiate apeluri, mesaje, înregistrări sau transferuri externe.

Comenzi pentru dezvoltator, după instalare: activează temporar componenta de probă
`ro.leo.visual.tests/ro.leo.visual.ShareProbeActivity`, apoi rulează
`ro.leo.visual.tests/ro.leo.visual.ProjectFlowTests`. Componenta se dezactivează
singură după proba de citire; dezactiveaz-o manual și dacă runnerul este întrerupt.
Proba folosește exclusiv date fictive și un fișier local; nu trimite către Bluetooth/email.
