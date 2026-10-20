# 🦁 LEO · Android pe Nokia 6300 4G

**Un ecran mic. Taste adevărate. O viață nouă pentru un Nokia.**

Leo este un port experimental de **Android 8.1** pentru **Nokia 6300 4G TA-1286**,
cu o interfață construită pentru D-pad și tastatura fizică. A început cu un dump
KaiOS și a crescut până la un telefon care pornește din memoria internă,
primește apeluri, redă muzică și ascultă radio FM.

Ideea proiectului: să păstrăm plăcerea unui telefon cu taste și să construim în
jurul lui un sistem cu personalitate. **Leo Visual** aduce meniuri colorate,
bare pentru softkey-uri, aplicații native și detalii inspirate de KaiOS și Symbian.

`Android 8.1 / API 27` · `ARMv7 / 32-bit` · `240 × 320` · `Dual SIM` · `Leo Visual`

**Actualizat: 20 octombrie 2026** · [Tema flat](android81/LEO-FLAT-THEME.md)

[Ce funcționează](#ce-poți-face-deja) · [Capturi](#leo-în-imagini) ·
[Audit](#auditul-proiectului) · [Ce urmează](#ce-mai-avem-de-construit) ·
[Recompilare](#pentru-cei-care-vor-să-construiască) · [Documentație](#harta-proiectului)

> **Starea proiectului: prototip funcțional, utilizare experimentală.**
> Sursele și uneltele sunt disponibile; un ROM release instalabil și o procedură
> publică de flash complet validată sunt încă de construit.

## Leo în imagini

Capturi reale de pe telefon, făcute pe **20 octombrie 2026**, cu noul aspect flat.
Culori uniforme, pictograme fără reflexe și selecții clare pentru D-pad.
Apasă o imagine pentru dimensiunea originală.

| Acasă · ambele SIM-uri | Aplicații · din taste | Setări cu taburi |
| :---: | :---: | :---: |
| <a href="android81/screenshots/leo-flat-20261020-home.png"><img src="android81/screenshots/leo-flat-20261020-home.png" width="180" alt="Home Leo flat"></a> | <a href="android81/screenshots/leo-flat-20261020-apps.png"><img src="android81/screenshots/leo-flat-20261020-apps.png" width="180" alt="Pictograme flat"></a> | <a href="android81/screenshots/leo-flat-20261020-settings.png"><img src="android81/screenshots/leo-flat-20261020-settings.png" width="180" alt="Setări Leo flat"></a> |
| **Player muzical** | **Calculator** | **Meniul de opțiuni** |
| <a href="android81/screenshots/leo-flat-20261020-music.png"><img src="android81/screenshots/leo-flat-20261020-music.png" width="180" alt="Player în pauză, fără piesă selectată"></a> | <a href="android81/screenshots/leo-flat-20261020-calculator.png"><img src="android81/screenshots/leo-flat-20261020-calculator.png" width="180" alt="Calculator Leo flat"></a> | <a href="android81/screenshots/leo-flat-20261020-menu.png"><img src="android81/screenshots/leo-flat-20261020-menu.png" width="180" alt="Opțiunile playerului din taste"></a> |

## Ce poți face deja

„Confirmat” înseamnă că proba descrisă a trecut pe aparatul de dezvoltare.
Limitele și scenariile rămase sunt indicate separat.

| Funcție de bază | Ce merge astăzi |
| --- | --- |
| 🏠 **Home și navigare** | Leo este launcherul principal. D-pad, softkey-uri, meniu global, notificări, blocare taste și mouse din taste. |
| ⌨️ **Scriere** | Multi-tap și T9 local de bază, majuscule, diacritice, simboluri și ștergere din tastatura fizică. |
| 📞 **Apeluri pe SIM 1** | Apel primit și două apeluri inițiate cu sunet în ambele sensuri, confirmate real; răspuns și închidere din taste. USB s-a întrerupt la apelurile inițiate. |
| 👥 **Contacte și SMS** | Contacte locale, căutare, favorite; trimitere și recepție SMS pe SIM 1 confirmate în probele anterioare. Conversații Leo cu bule și ciorne locale. |
| 🌐 **Conectivitate** | Date LTE pe SIM 1, Wi-Fi și hotspot cu Internet verificat de pe un alt telefon. Ambele SIM-uri sunt afișate și înregistrate pentru voce. |
| 🔊 **Audio** | Difuzor, receptor, căști stereo cu fir și boxă Bluetooth A2DP. Bipuri pe boxă confirmate după integrarea modulului audio lipsă. |
| 🎵 **Muzică** | Player cu vizualizator real și copertă; artiști, albume, liste salvate, coadă editabilă, shuffle/repeat persistente. Fișiere de probă MP3, FLAC, AAC, OGG și WMA v2 redate. |
| 📻 **Radio FM** | Acordaj, căutare înainte/înapoi, canale scanate, favorite, căști/difuzor și nume RDS afișat, confirmate manual. |
| 🎙️ **Reportofon** | Înregistrări AAC/M4A, pauză, salvare intern/SD, indicator care reacționează la voce și redenumire confirmată. |
| 📂 **Fișiere și transfer** | Fișiere interne și SD, copiere/mutare verificate prin hash, redenumire; transfer USB MTP confirmat. |
| 🧰 **Unelte zilnice** | Calculator, Notițe, Calendar local, Ceas, alarmă și temporizator; sunetul temporizatorului confirmat. |
| 🎨 **Personalizare** | Teme, fundaluri, reordonarea aplicațiilor, scurtături, trei favorite în dialer și configurări rapide cu volum. |
| 🌙 **Profiluri sonore** | Normal, Silențios, Exterior, Noapte și profil personal. Revenirea sunetului după Noapte → Normal confirmată. |

**Scurtătura nouă:** din Home, apasă **0** pentru **Acum se redă** — un singur loc
pentru muzică și FM, volum și acces la player. În meniul principal sunt **26 de intrări**;
aplicațiile Android instalate au propria listă, iar compatibilitatea cu tastele variază.

Pentru telefonia pe SIM 2, DTMF, VoLTE/VoWiFi și probe după repornire, consultă
limitele de mai jos. [Matricea tehnică și probele anterioare](README.archive-2026-10-20.md#tabelul-funcțiilor)
păstrează detaliile fiecărei funcții.

## Auditul proiectului

Audit din **20 octombrie 2026**, pe baza codului de la `827a478`, a probelor
consemnate și a unor verificări noi, doar de citire. O captură arată interfața;
confirmarea unei funcții se bazează pe proba ei separată.

| Zonă | Concluzia auditului | Dovezi și limite |
| --- | --- | --- |
| **Sistem și hardware** | Android pornește intern; integrarea hardware este încă parțială | Boot complet, Android 8.1.0, profil low-RAM activ și audioserver în funcțiune verificate din nou. Adreno și tastatura au probe documentate. Camera, GPS și vibrația fizică rămân deschise. |
| **Interfața Leo** | Shell nativ adaptat la taste, cu un set consistent de aplicații | Home, bare softkey, T9 și meniuri comune. Serviciul tastelor era legat la procesul curent. Recuperarea sa limitată este implementată; eliminarea tuturor blocajelor nu este demonstrată. |
| **Telefonie** | Funcțiile de bază au rezultate reale, cu acoperire incompletă | Apel primit și două apeluri inițiate bidirecționale, SMS și LTE pe SIM 1. SIM 2, DTMF și IMS au nevoie de probe distincte. [Probe noi](android81/STABILITY-PHONE-20261020.md). |
| **Media** | Una dintre zonele cele mai avansate ale portului | Muzică, căști, FM/RDS și A2DP au confirmări reale. Modulul A2DP este încărcat în politica audio. Microfonul Bluetooth, apelurile prin accesorii și toate variantele de codec nu sunt validate. |
| **Cod și structură** | Sursele Leo sunt în repository, împreună cu scripturi și documentație | 90 fișiere Java în shell, plus 16 clase de instrumentare; tastatura și uneltele native sunt separate. Numărul fișierelor descrie inventarul, nu acoperirea testelor. |
| **Recompilare** | Există o bază reproductibilă documentată, încă netestată integral pe un PC curat | Toate cele **590 de proiecte AOSP** din manifest au revizii SHA fixate. Arborele AOSP, dump-ul și componentele proprietare necesare sunt intrări separate. |
| **Verificare automată** | Există teste locale și probe pe telefon; lipsește încă o rulare continuă centralizată | Testele cozii muzicale au trecut în ultima etapă. Alte rezultate sunt legate în documentație. Repository-ul nu are workflow-uri GitHub Actions; suita completă nu a fost rerulată în acest audit. |
| **Livrare și mentenanță** | Proiect de dezvoltare, încă fără release pentru instalare generală | Scripturile depind de mediul local și includ verificări pentru aparatul de dezvoltare. Lipsesc încă o procedură completă de release și un fișier LICENSE la rădăcină. |

**Concluzie:** avem un prototip utilizabil în probe, cu interfață proprie și funcții
confirmate pe hardware real. Prioritatea următoare este
să facem aceste rezultate repetabile: pornire, taste, apeluri, autonomie și recuperare.

## Ce mai avem de construit

Ordinea de mai jos pune folosirea zilnică înaintea adăugării unor meniuri noi.

| Prioritate | De lucru | Cum știm că este gata |
| --- | --- | --- |
| **1 · Stabilitate** | Blocaje Leo, întreruperi ADB/USB, pornire și repornire; DeskClock stock corectat, STOP fără alarmă activă verificat, reboot încă de testat | Serii documentate de porniri și navigare, cu recuperare verificată și fără pierderea controlului din taste. |
| **2 · Telefonie completă** | Apeluri pe SIM 2, DTMF, respingere, SMS și rute de apel; apelurile inițiate pe SIM 1 au acum două confirmări audio | Matrice de apeluri și mesaje reale, repetată după reboot. VoLTE și VoWiFi validate separat. |
| **3 · Autonomie** | Consum în repaus, suspendare, încărcare și utilizare pe baterie | Măsurători fără alimentare USB și sesiuni mai lungi, cu metodă și rezultate publicate. |
| **4 · Hardware rămas** | Vibrație, cameră, GPS și integrarea senzorilor | Probe pe hardware real; camera rămâne momentan în afara etapei active, la cererea utilizatorului. |
| **5 · Aplicații și accesibilitate** | T9 mai bun, email SMTP, browser/WebView, aplicații Android dificil de folosit din taste | Trimitere reală de email, scenarii T9 mai largi și fluxuri complete controlabile din taste. |
| **6 · Release de proiect** | Build de la zero, teste automatizate, pachet și recuperare documentată, clarificarea licenței codului Leo | Alt mediu poate reconstrui versiunile publicate urmând documentația; intrările și limitele sunt explicite. |

### Limite cunoscute, pe scurt

- **Camera, GPS și integrarea senzorilor Android:** nefuncționale în starea documentată.
- **Vibrații:** control software prezent, fără vibrație fizică confirmată.
- **VoLTE / VoWiFi:** o înregistrare IMS experimentală nu validează apeluri IMS.
- **MMS:** neimplementat. SMS pe SIM 2 și noua cale a mesajelor trimise necesită probe suplimentare.
- **Email / web:** primirea IMAP a funcționat; SMTP este nevalidat. Browserul text nu execută JavaScript, iar WebView a avut crash-uri GPU.
- **FM și accesorii:** căștile sunt antena FM; o sesiune este limitată la o oră. HFP, microfonul Bluetooth și apelurile prin căști mai au nevoie de verificări.
- **Performanță și securitate:** platformă Android 8.1 veche, kernel derivat din stock și build de dezvoltare; actualizările și o configurație de release întărită rămân de planificat.

## Pentru cei care vor să construiască

| Aparatul de dezvoltare | Configurație |
| --- | --- |
| Model | Nokia 6300 4G · **TA-1286** |
| Platformă | Qualcomm msm8909 · Cortex-A7 · ARMv7 / Binder 32-bit |
| Ecran / GPU | 240 × 320 · Adreno 304 |
| Bază software | AOSP `android-8.1.0_r81`, produs `aosp_leo_go-eng` |
| Kernel | Derivat din stock `4.9.240-perf-g4f9e270fd77f`, cu UHID; fără mainline |
| Memorie / stocare | Profil low-RAM, zRAM ~256 MiB; `/system` ~800 MiB, `/data` ~2,3 GiB, pe memoria internă |
| Interfață | Leo Visual nativ + Leo Keypad; fără dependență de WebView pentru shell |

Profilul low-RAM folosește configurația Go a proiectului; certificarea Android Go
și serviciile Google nu fac parte din port.

**[Începe cu ghidul de recompilare →](android81/REBUILD.md)**

Mediul utilizat este Windows + WSL/Linux, cu Java 8 și un checkout AOSP separat.
Repository-ul conține surse și rețete de dezvoltare. Pentru reconstruire sunt
necesare și intrările locale descrise în ghid, inclusiv componentele originale.

```text
android81/
  visual-shell/       Home, aplicații, meniuri, bibliotecă și setări Leo
  keypad-ime/         Tastatură multi-tap și T9
  device/nokia/leo/   Configurație AOSP, init, keylayout și politica audio
  audio-runtime/     Integrarea audio Qualcomm
  fm-runtime/        Backend FM și rutare audio
  radio-runtime/     Integrarea modemului
  repro/             Manifest AOSP fixat și modificări pentru recompilare
  screenshots/       Capturi selectate pentru documentație
  local/, output/    Intrări și rezultate locale, ignorate de Git
```

## Harta proiectului

| Vrei să urmărești… | Începe de aici |
| --- | --- |
| Sursele și recompilarea | [REBUILD](android81/REBUILD.md), [configurația dispozitivului](android81/device/nokia/leo/) |
| Stabilitate și apeluri inițiate | [Probe SIM 1, întreruperea USB și DeskClock](android81/STABILITY-PHONE-20261020.md) |
| Ultimele șase îmbunătățiri | [Centrul media, A2DP și recuperarea tastelor](android81/LEO-MEDIA-CENTER.md) |
| FM, scanare, RDS și difuzor | [Portarea FM](android81/FM-INVESTIGATION.md) |
| Shell-ul și aplicațiile pentru taste | [Leo Visual](android81/VISUAL-SHELL.md), [aplicațiile de sistem](android81/LEO-SYSTEM-APPS.md) |
| T9 și personalizare | [Tastatura](android81/KEYPAD-IME.md), [îmbunătățirile Leo](android81/LEO-UPGRADES-20261020.md) |
| Conectivitatea și setările | [Setări și conectivitate](android81/LEO-CONNECTIVITY-SETTINGS.md), [setări native](android81/LEO-NATIVE-SETTINGS.md) |
| Grafică și timpi de răspuns | [Integrarea Adreno](android81/ADRENO-INTEGRATION.md), [măsurarea latenței](android81/UI-LATENCY.md) |
| Fișiere, ciorne, înregistrări și baterie | [Unelte zilnice](android81/LEO-DAILY-TOOLS.md) |
| Starea tehnică detaliată și evoluția | [STATUS](android81/STATUS.md), [README-ul anterior, arhivat integral](README.archive-2026-10-20.md) |

## Construim mai departe

Sunt utile rapoartele care spun **varianta telefonului, versiunea testată, pașii și
rezultatul observat**. O funcție se poate confirma printr-o probă mică și clară;
problemele rămase merită aceeași vizibilitate ca reușitele.

Leo este încă un proiect în lucru, cu mult loc pentru contribuții: cod, teste,
traduceri, documentație și idei pentru o interfață mai plăcută din taste.

---

[README-ul anterior](README.archive-2026-10-20.md) este păstrat integral, cu inventarul
tehnic și documentele istorice. Această pagină este prezentarea și auditul curent.
