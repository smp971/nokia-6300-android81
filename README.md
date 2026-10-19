# Nokia 6300 4G (nokia-leo)

**Port Android 8.1 / Leo · TA-1286 · experimental**

Ultima actualizare: **19 octombrie 2026**

Android pornește din memoria internă, cu accelerare Adreno și interfață pentru
taste. Datele LTE, redarea audio și un apel primit cu sunet în ambele sensuri
au fost confirmate în probele descrise mai jos.

> **Starea portului: pornește, utilizare experimentală.** Există un ANR la boot,
> hardware încă neintegrat și aplicații instabile. Repository-ul nu oferă încă
> un ROM complet instalabil. Acesta este un proiect Android, independent de portul postmarketOS.

| Dispozitiv | Nokia 6300 4G |
| --- | --- |
| Producător / variantă testată | HMD Global / **TA-1286** |
| Nume de cod | `leo` / `nokia-leo` |
| Tip | Telefon cu taste, D-pad, două SIM-uri; fără touchscreen |
| Platformă | Qualcomm `msm8909`, Cortex-A7; ARMv7, userspace/Binder 32-bit |
| GPU | Adreno 304, GLES 3.0 |
| Ecran | 240 × 320, SPI, ieșire RGB565 |
| Sistem original | KaiOS 2.5.4, Gecko 48.0a2 |
| Sistem portat | AOSP `android-8.1.0_r81`, produs `aosp_leo_go-eng` |
| Kernel utilizat | Stock `4.9.240-perf-g4f9e270fd77f`, nu mainline |
| Profil memorie | `ro.config.low_ram=true`, zRAM aproximativ 256 MiB |
| Partiții utilizate | Intern: `/system` aproximativ 800 MiB, `/data` aproximativ 2,3 GiB |
| Interfață | **Leo Visual**, HOME nativ; **Leo Multi-tap**, IME implicit |
| Configurație dispozitiv | [device/nokia/leo](android81/device/nokia/leo/) |
| Pachet de instalare | Indisponibil; surse, patch-uri și scripturi de dezvoltare |

Profilul pentru memorie redusă nu reprezintă certificare Android Go. Serviciile
Google nu sunt incluse. Kernelul și componentele Qualcomm necesare provin din
sistemul original; shell-ul nu depinde de WebView.

**Cuprins:** [Funcții](#tabelul-funcțiilor) · [Capturi](#capturi) ·
[Instalare](#instalare) · [Pornire și recuperare](#pornire-și-recuperare) ·
[Probleme](#probleme-cunoscute) · [Dezvoltare](#dezvoltare) ·
[Documentație](#documentație) · [Contribuții](#contribuții)

## Tabelul funcțiilor

Stările se referă exclusiv la portul Android și la probele efectuate pe TA-1286.
Nu sunt stările portului postmarketOS și nu garantează toate scenariile de utilizare.

| Stare | Semnificație |
| --- | --- |
| **Funcțional** | Proba descrisă a trecut pe telefon, în limitele menționate |
| **Parțial** | Numai o parte funcționează sau există o problemă cunoscută |
| **Nefuncțional** | Componentă neintegrată sau probă eșuată |
| **Netestat** | Nu există încă o probă suficientă |
| **Experimental** | Rezultat într-o probă temporară, fără validare ca funcție permanentă |

### Sistem, interfață și stocare

| Funcție | Stare | Ce am verificat / limită |
| --- | --- | --- |
| Android pe memoria internă | **Funcțional** | `/system` read-only, `/data` read-write; sistemul nu mai depinde de cardul SD. |
| Pornire și repornire | **Parțial** | Android și serviciile revin după reboot; ANR-ul NetworkPolicy poate necesita „Așteaptă”. |
| USB / ADB / root | **Funcțional** | Diagnostic, instalare și control din PC; s-au observat și întreruperi USB temporare. |
| Leo Visual / HOME | **Funcțional** | Shell implicit nativ; listă/grilă, preferințe salvate și bară de acțiuni. Leo clasic eliminat pentru utilizatorul activ. |
| Meniu global | **Funcțional** | Tasta Meniu dedicată deschide comenzile peste alte aplicații; stil comun „Alege / Înapoi”. |
| Mouse din D-pad | **Parțial** | Deplasare cu accelerare la menținere, oprire la eliberare și clic verificate; compatibilitatea tuturor aplicațiilor și derularea nu sunt complet validate. |
| Listă Aplicații Android | **Funcțional** | 12 activități lansabile detectate, iconuri și sortare; Calculator și revenire în listă verificate. Nu validează toate aplicațiile. |
| Tastatură fizică / D-pad | **Funcțional** | Mapare adaptată și confirmări pe telefon; aplicațiile Android nu sunt toate adaptate la taste. |
| Introducere multi-tap din taste | **Funcțional** | IME implicit, inclus în împachetare; hook nou la boot încă neverificat prin reboot; 11 probe în editor local: litere, diacritice, majuscule, ștergere și câmp numeric. Compatibilitatea tuturor aplicațiilor nu este încă verificată. |
| Predicție T9 | **Nefuncțional** | Motorul predictiv nu este încă integrat; dicționarul românesc și integrarea IMEConnect din dump sunt identificate pentru analiză. |
| Bară de stare Android | **Funcțional** | Vizibilă împreună cu Leo Visual; nu confirmă singură funcționarea fiecărui serviciu afișat. |
| Accelerare Adreno | **Funcțional** | Renderer Qualcomm și test Android HARDWARE bitmap verificate, inclusiv după reboot. |
| Fluiditate generală | **Parțial** | Sincronizarea HWC1 a fost corectată; limitele panoului SPI și întârzierile unor aplicații rămân. |
| Profil pentru memorie redusă | **Funcțional** | `low_ram` și zRAM verificate software; nu înseamnă că orice aplicație rulează fluent. |
| Baterie / alimentare USB | **Parțial** | Nivel, tensiune, temperatură și alimentare citite; autonomia și ciclul complet de încărcare sunt netestate. |
| Card microSD / MTP | **Netestat** | Nu avem o probă completă de utilizare și transfer pentru aceste funcții. |

### Telefonie, sunet și conectivitate

| Funcție | Stare | Ce am verificat / limită |
| --- | --- | --- |
| Modem și două SIM-uri | **Funcțional** | Ambele SIM-uri READY și ambele sloturi înregistrate pentru voce, inclusiv după reboot. |
| Date mobile LTE / 4G | **Funcțional** | SIM 1: conexiune Android validată și două răspunsuri ping externe după reboot. Nu confirmă datele pe SIM 2 sau toate rețelele/APN-urile. |
| Setări rețea mobilă / opțiune 4G | **Funcțional** | Pagina și taburile SIM verificate; opțiunea LTE este expusă. Statisticile de trafic indisponibile sunt dezactivate. |
| Ecran de apel pentru taste | **Funcțional** | Apelul primit deschide interfața completă; răspunsul și închiderea din taste au fost confirmate pe apel real. |
| Apel primit și audio în ambele sensuri | **Funcțional** | Un apel real a trecut: ringing → off-hook → idle; utilizatorul confirmă conversația în ambele sensuri. Nu validează toate SIM-urile sau rutele audio. |
| Apel inițiat de pe Nokia | **Netestat** | Deschiderea Dialer nu confirmă efectuarea unui apel către rețea. |
| Respingere apel real / apel în așteptare / conferință | **Netestat** | Respingerea a trecut în simulare; scenariile reale necesită probe separate. |
| Tasta roșie | **Parțial** | Mapată ENDCALL; revenirea Acasă în repaus a fost verificată prin codul fizic. Apelul real a fost închis cu soft dreapta. |
| DTMF, microfon mut, difuzor și volum în apel | **Parțial** | Comenzi implementate; DTMF și comutările microfon/difuzor au trecut în simulare. Transportul DTMF și toate efectele audio în apel real sunt netestate. |
| SMS / MMS | **Netestat** | Nu avem mesaje reale trimise/primite confirmate; auditul inițial nu avea o aplicație SMS dedicată. |
| Difuzor / AudioTrack | **Funcțional** | Bipuri prin HAL-ul Qualcomm, confirmate auditiv înainte și după reboot. |
| MediaPlayer / fișiere OGG | **Funcțional** | Decodorul Vorbis și configurația codec corectate; redare OGG confirmată auditiv. Nu validează toate formatele sau aplicațiile media. |
| Microfon în convorbire | **Funcțional** | Confirmat prin conversația în ambele sensuri; înregistrarea audio separată nu este testată. |
| Căști / alte rute audio | **Netestat** | Fără validare completă pentru accesorii și schimbarea rutelor. |
| Client IMS | **Experimental** | Port compilat, cu înregistrare IMS voce pe SIM 1 într-o probă controlată. Activarea a fost retrasă; nu pornește automat. |
| VoLTE | **Netestat** | Înregistrarea IMS nu dovedește un apel VoLTE; apelul primit reușit nu a fost identificat ca VoLTE. |
| VoWiFi | **Nefuncțional** | Neintegrat; Wi-Fi nu este încă operațional. |
| Wi-Fi | **Experimental** | În probele curente, bootstrap WCNSS și modulul stock creează wlan0/p2p0; HAL înregistrat. Fără asociere, Internet sau persistență la boot confirmate. |
| Bluetooth | **Experimental** | HAL HCI înregistrat și BLE_ON în proba temporară; fără activare clasică completă, împerechere sau accesorii validate. |
| Hotspot | **Netestat** | Nu există o probă de partajare a conexiunii. |

### Aplicații și alte componente hardware

| Funcție | Stare | Ce am verificat / limită |
| --- | --- | --- |
| Setări Android | **Parțial** | Se deschide după corecții; unele categorii nu au suport hardware funcțional. |
| Calculator | **Funcțional** | Pornire și `2 + 2 = 4` verificate; introducerea din proba inițială a fost prin ADB. |
| Calculator Leo | **Funcțional** | Instalat; 29 cazuri JVM trecute; priorități, zecimale și împărțire la zero verificate pe telefon. |
| Notițe Leo | **Funcțional** | Scriere multi-tap, salvare/redeschidere, editare fără duplicare, ciornă și ștergere confirmată testate pe nota de probă; fără teste de volum sau pierdere a alimentării. |
| Ceas Leo | **Parțial** | Oră și cronometru native; start, tură, pauză, resetare și stare după relansare verificate. Alarmele proprii și temporizatorul nu sunt implementate. |
| Calendar / Contacte / Fișiere Android | **Parțial** | Interfețele se deschid; salvarea evenimentelor/contactelor și operațiile complete nu sunt validate. |
| Galerie / Muzică | **Parțial** | Pornire verificată; proba MediaPlayer separată nu confirmă toate funcțiile acestor aplicații. |
| Browser / WebView | **Parțial** | HTML local afișat, dar ulterior crash GPU în audit. Fără navigare stabilă pe internet validată. |
| Email | **Nefuncțional** | Se închide la pornire din cauza unei erori de resurse. |
| Cameră | **Nefuncțional** | Serviciul Android raportează zero dispozitive. |
| GPS / localizare reală | **Nefuncțional** | Fără provider GPS funcțional sau fix real confirmat. |
| Senzori Android | **Nefuncțional** | SensorService nu detectează senzori; descrie integrarea software, nu inventarul fizic. |

## Capturi

Capturi reale la 240 × 320. Ecranele de test IME și mouse nu reprezintă aplicații
complete; capturile istorice ale apelurilor sunt simulări. Apelul real este documentat separat.

| HOME / dual SIM | Listă aplicații | Meniu global |
| --- | --- | --- |
| ![Leo HOME](android81/screenshots/visual-shell-home.png) | ![Listă Leo](android81/screenshots/visual-shell-list.png) | ![Meniu din tasta fizică](android81/screenshots/visual-shell-global-menu.png) |

| Aplicații Android | Ceas / cronometru | Multi-tap românesc |
| --- | --- | --- |
| ![Aplicații Android](android81/screenshots/visual-shell-android-apps.png) | ![Ceas Leo](android81/screenshots/leo-clock-stopwatch.png) | ![Editor de test IME](android81/screenshots/keypad-ime-romanian.png) |

[Toate capturile păstrate în proiect](android81/screenshots/).

## Instalare

Nu există o imagine release, un pachet OTA sau o procedură de flash validată
pentru public. Scripturile actuale depind de arborele AOSP, intrările proprietare
locale, configurația WSL și verificările aparatului de dezvoltare.

Sistemul testat rulează din memoria internă: `/system` read-only și `/data`
read-write. Etapele inițiale de pe microSD sunt păstrate în documentație ca istoric,
nu ca instrucțiuni pentru configurația curentă. Nu folosim `pmbootstrap` pentru acest port Android.

Puncte de plecare pentru dezvoltare:

- [Configurația AOSP a dispozitivului](android81/device/nokia/leo/).
- [Pregătirea inițială — document istoric](android81/README.md).
- [Rezultate și istoric tehnic](android81/STATUS.md).

## Pornire și recuperare

Android, ADB și serviciile de bază au revenit după repornire în probele efectuate.
Un ANR NetworkPolicy poate bloca interfața până la alegerea „Așteaptă”. USB poate
dispărea temporar; lipsa ADB nu dovedește singură că telefonul nu a pornit.

Recovery-ul de diagnostic cu ADB a fost folosit în dezvoltare. Nu există aici o
procedură universală de deblocare/flash/recovery pentru toate variantele Nokia.
Comenzile și combinațiile de taste de pe paginile altor dispozitive nu sunt
instrucțiuni validate pentru acest port.

## Probleme cunoscute

- ANR NetworkPolicy la pornire; revenirea după boot nu este complet autonomă.
- Wi-Fi și Bluetooth sunt în bring-up; nu avem încă conectare Wi-Fi sau împerechere confirmate.
- Cameră, GPS și integrarea senzorilor nefuncționale în auditul documentat.
- WebView a prezentat crash GPU; Email are o eroare de resurse la pornire.
- Aplicațiile Android nu sunt toate controlabile complet din taste; cursorul este o opțiune de acces.
- SMS/MMS, apeluri inițiate, accesorii audio, autonomia și încărcarea completă necesită probe.
- Înregistrarea IMS experimentală nu validează VoLTE sau VoWiFi.

Corecția DispSync/HWC1 a redus mediana **tastă → începutul desenării** de la
aproximativ **1005 ms la 18 ms** în două probe de câte 16 apăsări. Nu este o
măsurare optică sau un test FPS. [Metodă și limite](android81/UI-LATENCY.md).

## Dezvoltare

Mediul actual: Windows + WSL (`kaios`), Java 8 și AOSP separat în
`/root/android81-leo/src`. Arborele AOSP și componentele proprietare nu sunt
incluse. Clonarea repository-ului nu furnizează singură toate intrările de compilare.

```text
android81/
  device/nokia/leo/    Configurație AOSP, init, keylayout, componente native
  visual-shell/       HOME, aplicații Android, meniuri, pointer și Ceas Leo
  keypad-ime/         Introducere text multi-tap din taste
  call-ui/            Adaptarea comenzilor de apel și demonstrații locale
  audio-runtime/      Integrarea audio
  radio-runtime/      Integrarea radio
  screenshots/        Capturi selectate
  local/              Copii KaiOS și intrări locale, ignorate de Git
  output/             Artefacte și rezultate de test, ignorate de Git
analysis/             Unelte de analiză a dump-ului
kaios31/              Investigația unui sistem donator
```

### Aplicațiile KaiOS și shell-ul Leo

Copia locală păstrează 63 de arhive și registrul celor 64 de intrări, cu verificare
SHA-256. Aplicațiile privilegiate KaiOS nu rulează prin simpla copiere: folosesc
API-uri Gecko ce necesită înlocuitori Android. Codul nou Leo este o adaptare nativă
a fluxurilor analizate. **Ceas Leo**, **Calculator Leo** și **Notițe Leo** sunt
instalate. Ceasul are încă alarmele proprii și temporizatorul neimplementate.
Multi-tap funcționează, predicția T9 încă nu.

- [Planul proiectului și criterii de acceptare](android81/PROJECT.md).
- [Portarea aplicațiilor: copie, matrice și rezultate](android81/KAIOS-APP-PORTS.md).
- [Inventarul KaiOS](android81/KAIOS-UI-INVENTORY.md).

### Următoarele etape

1. Uniformizarea dialogurilor, completarea Ceasului și continuarea aplicațiilor pentru taste: Contacte, Mesaje, Calendar.
2. Eliminarea ANR-ului la boot și repetarea probelor de stabilitate/performanță.
3. Finalizarea conectivității și continuarea integrării hardware.
4. Probe reale separate pentru apeluri inițiate, SMS, ambele SIM-uri și rutele audio.
5. Investigarea predicției T9 și a IMS/VoLTE/VoWiFi, cu validare distinctă pentru fiecare.

## Documentație

| Subiect | Document |
| --- | --- |
| Stare și istoric | [STATUS](android81/STATUS.md), [audit inițial](android81/FUNCTION-AUDIT-2026-10-18.md) |
| Shell, meniu, mouse și aplicații Android | [VISUAL-SHELL](android81/VISUAL-SHELL.md) |
| Calculator și Notițe Leo | [LEO-CALCULATOR-NOTES](android81/LEO-CALCULATOR-NOTES.md) |
| Tastatură multi-tap | [KEYPAD-IME](android81/KEYPAD-IME.md) |
| Grafică / performanță | [ADRENO-INTEGRATION](android81/ADRENO-INTEGRATION.md), [UI-LATENCY](android81/UI-LATENCY.md) |
| Radio și date | [RADIO-BRINGUP](android81/RADIO-BRINGUP.md), [LTE-BRINGUP](android81/LTE-BRINGUP.md) |
| Audio / codec-uri / setări mobile | [AUDIO-BRINGUP](android81/AUDIO-BRINGUP.md), [PHONE-AUDIO-FOLLOWUP](android81/PHONE-AUDIO-FOLLOWUP.md) |
| Apeluri din taste | [CALL-KEYPAD](android81/CALL-KEYPAD.md) |
| IMS experimental | [IMS-PORT](android81/IMS-PORT.md) |
| Analiza interfeței KaiOS | [KAIOS-ALTERNATIVE](android81/KAIOS-ALTERNATIVE.md), [KAIOS-UI-INVENTORY](android81/KAIOS-UI-INVENTORY.md) |

## Contribuții

Dezvoltarea și probele sunt urmărite în [commit-uri](https://github.com/smp971/nokia-6300-android81/commits/main).
Un raport util include varianta telefonului, commit-ul, pașii de reproducere,
rezultatul observat și jurnalul relevant fără date personale. O simulare sau
prezența unei iconițe nu justifică marcarea unei funcții hardware drept funcțională.

Repository-ul păstrează surse, patch-uri, scripturi și documentație. Dump-urile,
binarele proprietare, dicționarele vendor, imaginile generate, APK-urile, cheile
și datele personale rămân locale. Documentele istorice pot descrie blocaje rezolvate;
tabelul de mai sus este rezumatul actual. Experimentele anterioare inițializării
Git nu au commit-uri retroactive.

## Referințe

Structura fișei este inspirată de paginile Devices din postmarketOS, de exemplu
[Nokia N900](https://wiki.postmarketos.org/wiki/Nokia_N900_%28nokia-n900%29):
identificare, funcții, instalare, pornire și documentație. Stările și rezultatele
de aici provin din proiectul Android Leo, nu din acea pagină.
