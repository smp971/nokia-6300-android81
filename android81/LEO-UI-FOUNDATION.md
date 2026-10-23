# Leo Visual · interfață coerentă

Actualizat: 23 octombrie 2026. Prima etapă de maturizare vizuală, instalată pe TA-1286.

## Limbaj vizual

Aspect flat, culorile temei alese de utilizator, pictograme vectoriale și selecție
explicită pentru D-pad. Componente native, fără animații sau imagini decorative
costisitoare. Configurările existente de temă și mărime a textului se păstrează.

| Componentă comună | Comportament |
| --- | --- |
| Antet | 38 dp, pictogramă de 25 dp, titlu de 18 sp, elipsă pentru nume lungi |
| Liste | Rânduri de minimum 48 dp, margini de 6 dp, spațiu de 4 dp între rânduri |
| Informație secundară | Textul de după prima linie are 82% din dimensiunea titlului; culoarea urmează selecția |
| Selecție | Suprafață contrastantă și contur în culoarea accentului |
| Taste | Bară de 32 dp, trei zone egale; text adaptiv 9–12 sp, elipsă la nevoie |
| Listă goală | Mesaj central; nu afișează o acțiune «Deschide» inexistentă |
| Opțiuni | Colțuri flat de 4 unități; lista goală nu afișează «1 / 0» |

`LeoActivity.footer()` actualizează aceeași bară, fără să readauge elemente sau
să reinstaleze conținutul ferestrei. Acest lucru evită inclusiv distrugerea unui
SurfaceView la simpla schimbare a unei etichete. Structura celor trei etichete
este păstrată pentru compatibilitate cu vizorul camerei.

## Captură reală

Ecran demonstrativ randat pe telefon de testul UI; nu este o pagină nouă de setări.
Nu conține date personale.

![Componentele comune Leo](screenshots/leo-ui-foundation.png)

## Verificare

- Compilare Visual și APK de teste reușită; instalare cu verificarea SHA-256.
- `UiFoundationTests`: 16 verificări pe aparat — identitatea conținutului și barei
  după 20 de actualizări, spațiul util, selecția, navigarea circulară și lista goală.
  Include cinci cazuri de direcție/margine pentru derularea mouse-ului.
- `PocketSuiteTests`: 18 verificări locale trecute după instalare, inclusiv
  păstrarea izolării procesului camerei și dezactivarea HWUI pentru vizor.
- Captura inspectată vizual la 240 × 320: antetul, rândurile și bara sunt vizibile.
- Nu s-au repetat probe de apel, înregistrare sau fotografiere.

## Audit și pașii către un build matur

| Zonă | Stare în această etapă |
| --- | --- |
| Liste, formulare, antet și taste bazate pe LeoActivity | Componente comune actualizate |
| Meniul comun KeypadDialog | Aspect aliniat; caz gol corectat |
| Home, Telefon, player, Ceas și alte ecrane desenate special | Își păstrează compoziția; necesită audit vizual individual |
| NotesActivity și alte activități care nu moștenesc LeoActivity | Nu primesc automat această reorganizare |
| Text mărit, toate temele și cele mai lungi traduceri | Disponibile în cod, matricea vizuală completă rămâne de verificat |
| ROM complet, instalabil de la zero | Nu este livrat de această schimbare UI |

Următoarea etapă coerentă: trecere ecran cu ecran prin Home, aplicații, setări,
Telefon și media, cu aceleași reguli de ierarhie și stări de încărcare/eroare.
Testele actuale confirmă fundația comună; nu reprezintă certificarea tuturor
ecranelor, funcțiilor hardware sau a unui release final.

## Meniu global și mouse

Serviciul de accesibilitate Leo era dezactivat pe aparat. A fost activat în setările
persistente Android. Meniul global existent este o fereastră de accesibilitate,
independentă de activitatea din prim-plan: proba peste Setări Android a confirmat
deschiderea lui, activarea cursorului, mișcarea și oprirea din același meniu.

- Tasta fizică Meniu (scan 212): meniul global; soft stânga (139): acțiunea aplicației.
- Mouse: la marginea de sus/jos, continuarea apăsării săgeții produce o glisare
  verticală de 300 ms, cel mult o dată la 700 ms; nu suprapune gesturile.
- Eliberarea tastei oprește repetarea. Gestul deja început se termină.
- `*` păstrează modul manual de derulare; `#` oprește mouse-ul.
- Blocarea tastelor și protecțiile pentru apeluri/alarme rămân active. «Global»
  nu înseamnă ocolirea ecranului de blocare.
- Derularea depinde de existența unei suprafețe care acceptă glisări în aplicația
  deschisă; un ecran fără conținut derulabil nu se va deplasa.

Proba reală peste Setări Android a mutat lista de la «Network & Internet» la
«Storage / Security / System», folosind trei secunde de apăsare pe săgeata jos.
Jurnalul confirmă finalizarea glisărilor. Mouse-ul a fost oprit apoi din meniu.

Captura din testul UI utilizează `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`:
modul implicit de automatizare dezactiva serviciul pe acest port. După ultima
rulare, cele 16 verificări au trecut și serviciul a rămas configurat și conectat.
