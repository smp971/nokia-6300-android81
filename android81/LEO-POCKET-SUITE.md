# Leo · șase lucruri de construit, într-un singur pachet

**0.4.0-dev / cod 4 · 22 octombrie 2026**

O bibliotecă în buzunar, jocuri pentru taste și un aspect pe care îl alegi tu.
Această versiune include și toate etapele **0.1, 0.2 și 0.3**.

**Stare: instalat pe Nokia, cu 18 verificări locale ale acestei etape trecute.**
Camera este o interfață experimentală peste API-ul Android. Pe 23 octombrie,
inițializarea izolată a detectat BF20A1 și o cameră în HAL-ul privat; Android încă
nu o expune aplicațiilor. Nu sunt publicate capturi noi pentru această etapă.

| Funcție | Ce este pregătit | Ce rămâne de demonstrat pe aparat |
| --- | --- | --- |
| Colecții vizuale | Trei pachete; patru mostre Home/Meniu/Player/Blocare; aplicare completă sau doar culori; revenire la ultimul aspect | Aspectul efectiv și aplicarea/restaurarea pe Nokia |
| Cărți Leo | TXT UTF-8, EPUB în ordinea capitolelor, reluare, 12 semne de carte, text reglabil și mod noapte | Parserul XML Android, navigarea și reluarea după închiderea aplicației |
| Jocuri Leo | Snake, Sudoku și Puzzle 15; reguli verificate offline; progres local pentru ultimele două | Randare și taste fizice, pauză și consum în utilizare |
| Conexiuni Leo | Wi-Fi, hotspot, profiluri Bluetooth, USB, trafic și descărcările Leo | Starea reală a serviciilor și accesul la profiluri pe acest port |
| Actualizări Leo | Verificare ZIP/SHA-256/certificate, copie a APK-urilor precedente, sesiuni Android secvențiale și jurnal de stare | Confirmarea instalării, actualizarea propriei aplicații, revenirea și recuperarea sesiunilor |
| Cameră Leo | Diagnostic fără deschiderea senzorului; vizor și declanșare pe OK pregătite pentru un senzor expus de Android | **Hardware nefuncțional la ultima probă; fotografia nu este confirmată** |

Cele șase intrări sunt adăugate la finalul meniului, cu pictograme proprii.
ID-urile vechi rămân neschimbate. Meniul are acum **35 de aplicații** și poate fi
reordonat ca înainte. Panoul de conexiuni și actualizatorul sunt disponibile și
în Setări. Colecțiile vizuale păstrează și accesul existent din Personalizare.

## Un aspect complet, cu posibilitatea de revenire

Stânga/dreapta schimbă cele patru mostre ale fiecărei colecții. Mostrele sunt desene
locale, marcate ca atare, nu capturi ale unor servicii active.

„Pachet complet” aplică paleta, elimină suprascrierile de culoare pe aplicații,
readuce dimensiunea globală a textului la normal și aplică un fundal stock original.
„Doar culori” păstrează fontul și fotografia/fundalul curent.

Înaintea aplicării se păstrează privat un checkpoint al setărilor vizuale și al
fundalului. „Restabilește aspectul anterior” folosește acea copie, fără să schimbe
ordinea aplicațiilor, contactele favorite sau conținutul utilizatorului.
Cititorul, jocurile, conexiunile, camera și actualizatorul au și intrări în
Setări → Aspect → teme pe aplicații.

## Cărți Leo

Deschide o carte din aplicație sau din Fișiere → Opțiuni → Citește cartea TXT/EPUB.
Fișierele EPUB se pot deschide direct; TXT păstrează și editorul existent.

| Tastă | Acțiune |
| --- | --- |
| D-pad sus/jos | Derulare |
| Stânga/dreapta sau 4/6 | Capitol/partea precedentă ori următoare |
| * / # | Micșorează / mărește textul |
| 0 | Semn de carte în poziția curentă |
| 5 | Lista semnelor de carte |
| Meniu | Capitole, font, mod noapte și altă carte |

Poziția se salvează la plecarea din aplicație. Identitatea cărții folosește calea,
mărimea și data modificării: mutarea ori înlocuirea fișierului poate începe o sesiune
nouă de lectură. Maximum 12 semne sunt păstrate pentru aceeași identitate;
adăugarea aceluiași semn nu consumă încă un loc.

TXT trebuie să fie UTF-8, maximum 2 MiB, și este împărțit în părți de aproximativ
12.000 caractere. EPUB este afișat ca text, fără CSS, imagini, audio, JavaScript,
DRM ori formulare. Limite: arhivă 32 MiB, 2.000 intrări, 100 capitole în spine,
512 KiB per capitol și 4 MiB de text XHTML în total.

Arhiva nu este extrasă în memoria publică. Căile externe și traversarea sunt
refuzate. Declarațiile DOCTYPE simple sunt ignorate local; subseturile DTD interne
și declarațiile ENTITY sunt refuzate. Nu se rezolvă entități externe. Sunt acceptate
și câteva entități HTML uzuale; XML-ul rămâne limitat la adâncime 128 și 20.000
noduri element. Nu este un motor EPUB complet și nu promite compatibilitatea cu
orice carte sau codificare.

## Jocuri fără touchscreen

**Snake:** D-pad schimbă direcția; OK pornește sau pune pauză. Nu poate întoarce
șarpele direct în propriul gât. Scorul maxim se păstrează local. Jocul se oprește
la ieșire și nu continuă să consume CPU în fundal.

**Sudoku:** D-pad alege celula, 1–9 completează, 0 șterge. Celulele inițiale sunt
protejate, iar conflictele de linie, coloană și pătrat sunt refuzate. Se folosește
un puzzle de bază cu permutări de cifre, nu un generator cu niveluri de dificultate.
Progresul se păstrează la plecare; jocul nou cere confirmare.

**Puzzle 15:** D-pad mută spațiul gol. Amestecarea folosește numai mutări legale;
restaurarea refuză permutări imposibil de rezolvat sau date invalide. Progresul și
numărul mutărilor se păstrează local. Jocurile nu redau automat sunete.

## Conexiuni, fără schimbarea automată a rețelei

Panoul citește conexiunea activă, Wi-Fi și semnalul, starea hotspotului, profilurile
Bluetooth HID/A2DP/apeluri, modul USB și numărul descărcărilor Leo active, în
 așteptare ori eșuate. Rândurile deschid paginile de control existente.

Nu pornește scanări, nu deconectează dispozitive și nu activează hotspotul la
simpla deschidere. API-ul folosit nu raportează lista clienților hotspot, deci nu
inventăm un număr. Traficul este totalul tehnic de la pornire, nu consumul facturat
în abonament. Profilurile și descărcările se actualizează la evenimente ori manual.

## Actualizări locale și copia precedentă

Deschide ZIP-ul produs de `release-apps.py` sau alege-l din Actualizări Leo.
Verificarea acceptă exact structura pachetului: două APK-uri, manifest, README
și scriptul de instalare. Sunt verificate mărimile și hash-urile tuturor fișierelor.
**Scriptul este doar verificat și apoi ignorat; nu este extras ori executat.**

Cele două APK-uri trebuie să corespundă pachetelor instalate `ro.leo.keypad` și
`ro.leo.visual`, cu aceleași certificate, versiune și cod ca în manifest.
Arhiva este limitată la 64 MiB; fiecare APK la 32 MiB. Fișierele sunt pregătite
într-un director privat, nu instalate direct dintr-un SD modificabil.

Instalarea este o acțiune separată, confirmată explicit. Se salvează întâi APK-urile
instalate, cu verificare SHA-256. Sesiunile Android instalează **întâi tastatura,
apoi Leo Visual**; instalatorul Android păstrează confirmarea utilizatorului.
Permisiunea „instalări din Leo” poate necesita activare în Setările Android.
Aceste ecrane Android nu au fost încă verificate din taste pe Nokia.

Nu există tranzacție atomică pentru cele două pachete pe API 27. O eroare după
primul APK poate lăsa temporar versiuni diferite. Jurnalul, verificarea sesiunii
și copia precedentă permit diagnosticarea/reîncercarea; aceasta nu este o
promisiune de recuperare automată după orice întrerupere. Reîncercarea aceluiași
pachet păstrează checkpointul inițial, inclusiv după o instalare parțială.

Revenirea restaurează **APK-urile, fără datele aplicațiilor**. Pentru downgrade se
cere build Android debuggable (`ro.debuggable=1`), folosind mecanismul existent
Android 8.1. Un APK mai vechi poate avea scheme de date incompatibile. Nu se
șterge aplicația, nu se formatează date și nu se rescriu boot/recovery sau partiții.
Dacă versiunea veche nu conține noul receptor de actualizări, confirmarea finală
poate lipsi din Leo; rămâne necesară verificarea versiunilor după revenire.

Manifestul SHA-256 verifică integritatea; certificatul este comparat cu instalarea
curentă. Proiectul folosește cheia AOSP de dezvoltare, deci acesta nu este un canal
public de actualizări cu autentificarea unui editor printr-o cheie privată de release.
Sesiunile, revenirea și actualizarea propriei aplicații sunt **încă netestate pe aparat**.

## Camera: interfață pregătită, inițializare încă de rezolvat

Deschiderea aplicației doar întreabă Android câte camere expune. Când numărul
este zero, afișează explicația și nu încearcă deschiderea HAL-ului privat.
Dacă un senzor va fi înregistrat, permisiunea Cameră și alegerea explicită a
vizorului preced deschiderea. OK declanșează, iar JPEG-ul este salvat în
`Pictures/Leo` și indexat pentru Galerie. Nu este implementată filmarea.
Vizorul, orientarea și fotografia rămân nevalidate fizic.

Auditul nou al fișierelor stock a identificat markeri de inițializare în daemon
și în modulul de senzori, plus configurația serviciului. Acestea susțin o direcție
de investigație. Pe 23 octombrie, proba izolată a înregistrat BF20A1 și HAL-ul privat
a raportat o cameră. Serviciul Android raportează încă zero; vizorul și fotografia
nu au fost testate.
[Audit și blocajul anterior](CAMERA-PORT-AUDIT.md).

## Validare și date păstrate

- Compilare Leo Visual și APK de probe Android: trecută.
- 28 verificări offline: citire TXT/EPUB, XML și căi, ordinea capitolelor,
  Snake/Sudoku/Puzzle, integritatea și limitele arhivei de actualizare.
- `PocketSuiteTests`: **18 verificări trecute pe Nokia, 22 octombrie 2026**, cu fișiere locale: cititor,
  parser EPUB, taste, palete, metadata/certificate APK și pagini de stare.
  Nu deschide senzorul, nu schimbă rețeaua, nu redă audio și nu creează sesiuni
  de instalare.
- Tastatura și Leo Visual 0.4.0-dev instalate prin pachetul portabil; copia APK-urilor
  anterioare păstrată local. HOME, dialer și IME Leo verificate.
- Probele UI locale au trecut; fotografia, instalarea/revenirea prin actualizator
  și utilizarea îndelungată rămân nevalidate.
- Nu există capturi noi pentru această etapă.

Pozițiile de lectură, semnele de carte, progresul jocurilor, checkpointul temei și
copiile APK nu sunt încă incluse în Backup Leo. Aspectul curent folosește în
continuare preferințele și fundalul deja acoperite de backup. Actualizarea cu
păstrarea datelor nu înlocuiește o copie de siguranță după formatare.

```bash
bash android81/run-host-pocket-checks.sh
python android81/inspect-camera-bootstrap.py
```

Auditul camerei cere dump-ul stock local. Nu urcă biblioteci sau jurnale brute.
[Pachet și recompilare](RELEASE-APPS.md).
