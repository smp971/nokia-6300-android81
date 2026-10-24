# Leo 0.5 · meniul își găsește locul

**0.5.0-dev / cod 5 · 24 octombrie 2026 · TA-1286**

Un meniu pentru aplicații, Setări pentru configurare și un meniu global care
rămâne la îndemână peste aplicațiile Android. Această etapă continuă finisarea
interfeței; nu reprezintă certificarea unui ROM final.

## Ce include

| Zonă | Schimbare |
| --- | --- |
| UI comună | Formulare cu margini și spațiu între câmpuri, conținut derulabil pe ecranul mic; antetul și bara stabilă din etapa anterioară sunt păstrate |
| Meniu | 22 de intrări vizibile din catalogul de 35 de identificatori stabili; șase categorii, grilă/listă și selecție limitată corect în categoriile mici |
| Cameră | Deschidere directă în vizor, progres de captură/salvare, ultima fotografie în Galerie și acces la diagnostic din Opțiuni |
| Telefon / Contacte | Numele și fotografia din agenda curentă în istoric; detalii cu durată, dată și SIM; acces de acolo la Telefon, SMS sau contact; istoricul unui număr și din fișa contactului |
| Mesaje | Dosare Toate, Primite, Trimise, În așteptare și Eșuate; nume de contact în listă; căutarea globală în mesaje, ciornele și rapoartele existente sunt păstrate |
| Ferestre | Meniu global → Aplicații recente, cu recente Leo și ferestre Android; comutare și închiderea unei ferestre Android cu confirmare |
| Configurare inițială | Zece pagini, inclusiv temă, tastatură și comenzile globale; setările se schimbă numai în paginile deschise explicit |
| Bliț | Oprit / Auto / Pornit / Lanternă, dacă sunt raportate de driver; LED aprins confirmat fizic în modul Lanternă |

## Capturi reale

| Media | Comenzile telefonului | Recente |
| --- | --- | --- |
| ![Media](screenshots/leo-maturity-media.png) | ![Ghid](screenshots/leo-maturity-setup.png) | ![Recente](screenshots/leo-maturity-recents.png) |

## Meniul pe categorii

| Categorie | Aplicații |
| --- | --- |
| Comunicare | Telefon, Contacte, Mesaje, Email |
| Media | Galerie, Muzică, Video, Reportofon, Radio FM, Cameră |
| Unelte | Ceas, Calculator, Notițe, Calendar, Convertor, Browser text, Meteo |
| Relaxare | Cărți, Jocuri |
| Sistem | Setări, Aplicații Android, Terminal |

**Toate** reunește cele 22 de aplicații. `*` / `#` schimbă categoria în meniu;
Opțiuni → Categorii aplicații oferă aceeași alegere. Aceste taste își păstrează
rolul de carduri când este afișat Home. Reordonarea folosește numai aplicațiile
vizibile; ID-urile și scurtăturile existente sunt păstrate.

Fișierele, Descărcările, Fundalurile, Favoritele/apelarea rapidă, Blocarea,
Configurările rapide, Ghidul, Diagnosticul, Backup-ul, Ajutorul, Colecțiile,
Conexiunile și Actualizările sunt accesibile din Setări, subpaginile sale sau
meniul global, fără pictograme duplicate în grila principală. Nu sunt dezinstalate.

## Ferestre și comutare

Meniul fizic → **Aplicații recente** arată lansările recente Leo și task-urile
Android disponibile. Recentele Leo reprezintă scurtături la aplicații, nu
instantanee complete ale stării fiecărei pagini. Leo folosește un task comun,
deci nu este eliminat ca o aplicație externă.

**Închide aplicația curentă** cere confirmare pentru o fereastră externă.
În interiorul Leo revine la Home. Operația utilizează `removeTask`, nu
`force-stop`; comportamentul proceselor externe după eliminarea task-ului este
decis de Android. Apelurile active și ferestrele de telefonie/SystemUI sunt
protejate. Nu există o comandă de închidere a tuturor aplicațiilor.

Listarea și închiderea rulează în fundal prin serviciul root Leo existent,
restricționat la UID-ul shell-ului. Un helper Java în APK folosește API-urile
Oreo fixate ale proiectului. Aceste funcții depind de serviciul `leo_terminal_root`;
nu reprezintă o implementare portabilă pe un Android obișnuit fără drepturi.
Serviciul de accesibilitate continuă să citească doar evenimente de fereastră,
fără acces la conținutul ferestrelor.

## Blițul camerei

Modurile sunt selectate din Cameră → Opțiuni → Bliț, prin API-ul camerei;
nu se introduc scrieri globale în sysfs pentru funcționarea aplicației.

- Lanternă: utilizatorul a confirmat LED-ul aprins; driverul indica nivelul 127.
- Pornit: în timpul capturii, citirea pasivă a LED-ului a înregistrat trecerea
  între 0 și 255. La revenirea la Home, ambele ieșiri LED indicau 0.
- Lanternă are oprire temporizată la 30 s și nu este memorată ca mod implicit.
- Auto este expus de HAL, dar declanșarea în funcție de lumină nu a fost validată.
- Expunerea cu bliț, calitatea în diferite scene și sesiunile lungi rămân de evaluat.

Fotografiile rămân în `Pictures/Leo`; accesul la ultima fotografie folosește
providerul local de fișiere. Filmarea nu este implementată.

## Verificări

- Compilare Visual și Keypad la versiunea 0.5.0-dev, fără schimbarea cheii de semnare.
- `MaturityTests`: 86 verificări locale trecute — filtrarea/categoriile meniului,
  selecția pe categorii mici, dosare SMS cu texte fictive, ghidul extins și
  comutarea/eliminarea unui task Settings creat numai pentru probă.
- `UiFoundationTests`: 16 verificări trecute din nou pe 0.5. Meniul global a
  afișat confirmarea de închidere peste Settings și a eliminat task-ul de probă.
- Fluxul real vizor → JPEG → Home a trecut cu PID-urile launcherului,
  providerului și cameraserver păstrate. Blițul are probele separate de mai sus.
- Nicio probă nouă de apel sau SMS trimis. Dosarul Trimise a fost verificat cu
  date locale fictive; rapoartele operatorului și SIM 2 rămân probe distincte.
- Capturile publicate ale meniului și ghidului nu conțin contacte, mesaje sau fotografii personale.

Actualizarea numerotată conține numai cele două APK-uri și instalatorul verificabil.
Runtime-urile hardware existente se păstrează. ZIP-ul pentru recovery rămâne
amânat până la finalizarea proiectului.

[Fundația UI](LEO-UI-FOUNDATION.md) · [Runtime cameră](CAMERA-RUNTIME.md) · [Recompilare](REBUILD.md)
