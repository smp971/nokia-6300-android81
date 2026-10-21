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

**Actualizat: 21 octombrie 2026** · [T9 și Browser din taste · 0.3.0](android81/LEO-KEYPAD-WEB.md) · [Organizator și media · 0.2.0](android81/LEO-ORGANIZER.md) · [Fluxuri noi · pregătite pentru testare](android81/LEO-PROJECT-FLOWS.md) · [Leo pentru fiecare zi](android81/LEO-COMFORT.md) · [Acces rapid și unelte personale](android81/LEO-DAILY-EXTRAS.md) · [Aplicațiile zilnice Material](android81/LEO-DAILY-POLISH.md) · [Integrare Leo · șase funcții](android81/LEO-SIX-INTEGRATION.md) · [Cinci aplicații îmbunătățite](android81/LEO-FIVE-APPS.md) · [Carduri Home și vibrații](android81/LEO-HOME-HAPTICS.md) · [Ceas și Meteo](android81/LEO-CLOCK-WEATHER.md) · [Telefon Leo Night](android81/LEO-NATIVE-PHONE.md) · [Tema flat](android81/LEO-FLAT-THEME.md)

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

**Telefon Leo Night:** interfață proprie pentru apelare și convorbire, conectată
la Android Telecom. Ecranele de apel din aceste capturi sunt **demo locale**.

| Apelare | Apel primit · demo | Convorbire · demo |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-night-phone.png" width="180" alt="Telefon Leo Night"> | <img src="android81/screenshots/leo-night-incoming-demo.png" width="180" alt="Apel demonstrativ primit"> | <img src="android81/screenshots/leo-night-active-demo.png" width="180" alt="Convorbire demonstrativă"> |

**Ceas și Meteo, pe aparat:** [funcții și probe](android81/LEO-CLOCK-WEATHER.md).

| Ceas · cinci file | Ora pe glob | Meteo București |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-clock-tabs.png" width="180" alt="Ceas Leo"> | <img src="android81/screenshots/leo-clock-world.png" width="180" alt="Ora pe glob"> | <img src="android81/screenshots/leo-weather-current.png" width="180" alt="Meteo București"> |

**Agenda, fișierele și backup-ul:** capturi noi de pe aparat, cu date demonstrative locale.

| Contact · fotografie | Numere multiple | Selecție fișiere |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-five-contact.png" width="180" alt="Contact demonstrativ"> | <img src="android81/screenshots/leo-five-contact-numbers.png" width="180" alt="Două numere pentru un contact"> | <img src="android81/screenshots/leo-five-files.png" width="180" alt="Selecție multiplă din taste"> |
| **Galerie · zoom** | **SMS · contor** | **Backup · previzualizare** |
| <img src="android81/screenshots/leo-five-gallery.png" width="180" alt="Zoom în Galeria Leo"> | <img src="android81/screenshots/leo-five-sms-counter.png" width="180" alt="Ciornă locală, netrimisă"> | <img src="android81/screenshots/leo-five-backup.png" width="180" alt="Previzualizare backup demonstrativ"> |

**Integrare nouă:** căutare globală, stiluri pe aplicații și backup selectiv al agendei/SMS.
Capturile folosesc exclusiv date fictive; [funcții și limite](android81/LEO-SIX-INTEGRATION.md).

| Caută în Leo | Stil pe aplicație · mostră | Restaurare selectivă |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-six-search.png" width="180" alt="Căutare locală cu date fictive"> | <img src="android81/screenshots/leo-six-style.png" width="180" alt="Previzualizare temă pe aplicație"> | <img src="android81/screenshots/leo-six-backup.png" width="180" alt="Alegerea datelor de restaurat"> |

**Material · Noapte** aduce suprafețe închise, accent turcoaz și carduri rotunjite.
„Media curentă” reunește Muzică și FM; capturile următoare sunt demonstrații vizuale,
fără redare audio pornită de probă.

| Media curentă · Muzică | Player muzical | Media curentă · FM |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-material-music.png" width="180" alt="Dashboard muzical Material, demonstrație"> | <img src="android81/screenshots/leo-material-player.png" width="180" alt="Player muzical Material, demonstrație fără redare"> | <img src="android81/screenshots/leo-material-fm.png" width="180" alt="Dashboard FM Material, demonstrație"> |

**Aplicațiile zilnice, mai coerente:** coperți și favorite în bibliotecă, albume
foto, conversații cu SIM-uri colorate, carduri la blocare și Calendar recurent.
[Detalii și limite](android81/LEO-DAILY-POLISH.md).

| Muzică · albume | Galerie · foldere | Mesaje · SIM-uri |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-polish-music.png" width="180" alt="Bibliotecă muzicală cu date demo"> | <img src="android81/screenshots/leo-polish-gallery.png" width="180" alt="Album foto demonstrativ"> | <img src="android81/screenshots/leo-polish-messages.png" width="180" alt="Conversație demonstrativă"> |
| **Calendar · zi cu eveniment** | **Blocare · informații** | |
| <img src="android81/screenshots/leo-polish-calendar.png" width="180" alt="Calendar cu eveniment fictiv"> | <img src="android81/screenshots/leo-polish-lock.png" width="180" alt="Carduri demonstrative la blocare"> | |

**Mai personal, în fiecare zi:** acces rapid Home, alarme pe zile, notițe fixate,
slideshow, restaurare pe categorii și analiză de stocare.
[Comenzi, probe și limite](android81/LEO-DAILY-EXTRAS.md). Datele din capturi sunt demonstrative.

| Acces rapid | Alarme pe zile | Notițe fixate |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-next-home.png" width="180" alt="Acces rapid Leo"> | <img src="android81/screenshots/leo-next-clock.png" width="180" alt="Alarmă demo în zilele lucrătoare"> | <img src="android81/screenshots/leo-next-notes.png" width="180" alt="Notițe demonstrative fixate"> |
| **Galerie · rotire** | **Categorii fișiere** | **Backup pe categorii** |
| <img src="android81/screenshots/leo-next-gallery.png" width="180" alt="Imagine demonstrativă rotită"> | <img src="android81/screenshots/leo-next-storage.png" width="180" alt="Fișiere de probă pe categorii"> | <img src="android81/screenshots/leo-next-backup.png" width="180" alt="Restaurare selectivă Leo"> |

**Mai aproape de un telefon complet:** contacte recunoscute cu fotografie, răspuns
rapid și șabloane, ceas de birou, colecții vizuale și versuri locale.
[Comenzi și limite](android81/LEO-COMFORT.md).

| Telefon · contact fictiv | Șabloane SMS | Ceas de birou |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-comfort-phone.png" width="180" alt="Contact demonstrativ cu fotografie"> | <img src="android81/screenshots/leo-comfort-templates.png" width="180" alt="Șabloane demonstrative, fără trimitere"> | <img src="android81/screenshots/leo-comfort-desk.png" width="180" alt="Ceas de birou Leo"> |
| **Colecție · previzualizare** | **Versuri locale originale** | **Ajutor · taste** |
| <img src="android81/screenshots/leo-comfort-themes.png" width="180" alt="Previzualizare Nordic"> | <img src="android81/screenshots/leo-comfort-lyrics.png" width="180" alt="Versuri originale de probă"> | <img src="android81/screenshots/leo-comfort-help.png" width="180" alt="Harta tastelor Home"> |

**Pregătit pe PC · 0.3.0-dev, încă neinstalat pe Nokia:** include atât
[fluxurile comune](android81/LEO-PROJECT-FLOWS.md), cât și [cele șase îmbunătățiri
pentru organizare și media](android81/LEO-ORGANIZER.md): Calendar cu ICS,
Galerie cu favorite, editor de playlisturi, notificări pe zile, program sonor
și diagnostic cu previzualizare editabilă. Pachetul adaugă și [T9 îmbunătățit,
dicționar editabil și navigare web în mod text din taste](android81/LEO-KEYPAD-WEB.md).

Pe lângă verificările T9 și cele 17 verificări de URL/navigare locală,
51 de verificări offline ale etapei anterioare au trecut: 36 noi, 10 algoritmice anterioare și 5 pentru
pachet. APK-ul de probe Android compilează; **probele pe aparat și instalarea
așteaptă reconectarea ADB**. Nu există capturi noi ale acestei versiuni.
[Recompilare și pachet](android81/RELEASE-APPS.md).

## Ce poți face deja

**Home cu informații utile:** carduri opționale, fără servicii noi de polling.

| Meteo | Următoarea alarmă | Muzică / FM |
| :---: | :---: | :---: |
| <img src="android81/screenshots/leo-home-cards-weather.png" width="180" alt="Card Meteo pe Home"> | <img src="android81/screenshots/leo-home-cards-alarm.png" width="180" alt="Card alarmă pe Home"> | <img src="android81/screenshots/leo-home-cards-music.png" width="180" alt="Card media pe Home"> |

„Confirmat” înseamnă că proba descrisă a trecut pe aparatul de dezvoltare.
Limitele și scenariile rămase sunt indicate separat.

| Funcție de bază | Ce merge astăzi |
| --- | --- |
| 🏠 **Home și navigare** | Leo este launcherul principal. Carduri opționale Meteo, următoarea alarmă și Muzică/FM; `*` schimbă cardul, `#` îl deschide. D-pad, softkey-uri, notificări grupate, blocare și mouse din taste. Listele și meniurile Leo au navigare circulară; `1` pe Home deschide căutarea globală locală; `5` scurt deschide Acces rapid, cu favorite și recente Leo. |
| ⌨️ **Scriere** | Multi-tap și T9 românesc extins, dicționar personal, memorare opțională a alegerilor, majuscule, diacritice și simboluri. |
| 📞 **Apeluri pe SIM 1** | Apel primit și două apeluri inițiate cu sunet în ambele sensuri, confirmate real; răspuns și închidere din taste. USB s-a întrerupt la apelurile inițiate. |
| 👥 **Contacte și SMS** | Contacte locale cu numere multiple, fotografie, favorite și previzualizare import VCF; alegerea numărului înainte de apel/SMS. Telefonul prioritizează numerele recunoscute și arată fotografia/inițialele; căutare T9 în nume. Răspuns rapid pe 5, șabloane editabile și căutare T9 în conversație; contor GSM-7/Unicode, necitite evidențiate, SIM/stare și cerere de raport de livrare; primirea raportului real încă nevalidată. Trimitere și recepție SMS pe SIM 1 confirmate în probele anterioare. Conversații Leo cu bule și ciorne locale. |
| 🌐 **Conectivitate** | Date LTE pe SIM 1, Wi-Fi și hotspot cu Internet verificat de pe un alt telefon. Ambele SIM-uri sunt afișate și înregistrate pentru voce. |
| 🔊 **Audio** | Difuzor, receptor, căști stereo cu fir și boxă Bluetooth A2DP. Bipuri pe boxă confirmate după integrarea modulului audio lipsă. |
| 📳 **Vibrații** | Calea haptică audio KaiOS este activată: trei impulsuri confirmate fizic. API-ul Android este adaptat și verificat prin HAL/PCM; proprietățile și biblioteca se încarcă după reboot. |
| 🎵 **Muzică** | Player Material în același stil cu Media curentă: copertă mare, titlu pe două rânduri, progres, comenzi 4/5/6 și vizualizator audio real; artiști și albume cu miniaturi, favorite, căutare T9/text, liste salvate, coadă editabilă, shuffle/repeat persistente, versuri locale LRC, oprire temporizată și reluarea ultimei sesiuni la cerere. Fișiere de probă MP3, FLAC, AAC, OGG și WMA v2 redate. |
| 📻 **Radio FM** | Acordaj, căutare înainte/înapoi, canale scanate, favorite, căști/difuzor și nume RDS afișat, confirmate manual. |
| 🎙️ **Reportofon** | Înregistrări AAC/M4A, pauză, salvare intern/SD, indicator care reacționează la voce și redenumire confirmată. |
| 📂 **Fișiere și transfer** | Fișiere interne și SD, selecție multiplă cu `5`, căutare, sortare și spațiu disponibil; imagini deschise în Galeria Leo cu zoom și deplasare. Copiere/mutare verificate prin hash anterior; USB MTP confirmat. Analiză la cerere pe categorii, top 20 fișiere mari și locație pentru exporturi/înregistrări noi. |
| 🧰 **Unelte zilnice** | Calculator, Notițe cu fixare, liste de bifat, export TXT și căutare T9; Calendar local cu agenda pe 90 de zile, recurențe și notificări, plus Ceas cu cinci file: Ora, Alarme, Glob, Timer și Crono. Ceas de birou cu luminozitate locală și baterie. Alarme pe zile ale săptămânii și amânare 5/10/15 minute; Timer cu pauză/continuare și cronometru cu ture. Programarea amânării este verificată; sunetul ei la termen rămâne de probat. |
| 🌤️ **Meteo** | București implicit, oraș configurabil, vreme modelată Open-Meteo, șapte zile, răsărit/apus și cache local cu data actualizării. Prognoză reală afișată pe telefon. |
| 🎨 **Personalizare** | Material · Noapte, colecții complete Leo Night/Nordic/Solar cu moștenire în Telefon, teme și accente, teme individuale pentru aplicațiile acceptate cu previzualizare, fundal opțional la blocare, reordonarea aplicațiilor, scurtături, trei favorite în dialer și configurări rapide cu volum. |
| 💾 **Backup Leo** | Export și restaurare locală pentru preferințele acceptate, teme/fundal, ordinea aplicațiilor, notițe și dicționar personal. Versiunea 2 include favorite muzicale/aplicații, Calendar Leo, șabloane SMS și restaurare pe categorii, cu validare și copie de revenire; citește și v1. Contactele și SMS-urile au acum o arhivă separată cu restaurare selectivă și omitere a duplicatelor; conturile rămân excluse. |
| 🖼️ **Galerie și aplicații Android** | Albume foto pe foldere, șase miniaturi mari, informații originale, slideshow și rotire de afișare; aplicații Android cu favorite și intrări ascunse reversibil. |
| 🌙 **Profiluri sonore** | Normal, Silențios, Exterior, Noapte și profil personal. Revenirea sunetului după Noapte → Normal confirmată. |

**Scurtătura nouă:** din Home, apasă **0** pentru **Media curentă** — un singur loc
pentru muzică și FM, volum și acces la player. În meniul principal sunt **29 de intrări**, inclusiv Ajutor Leo;
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
| **Sistem și hardware** | Android pornește intern; integrarea hardware este încă parțială | Boot complet, Android 8.1.0, low-RAM activ, Adreno și tastatură documentate. Camera și GPS rămân deschise; vibrațiile au primit ulterior confirmare fizică și adaptor Android, în [etapa 21 octombrie](android81/LEO-HOME-HAPTICS.md). |
| **Interfața Leo** | Shell nativ adaptat la taste, cu un set consistent de aplicații | Home, bare softkey, T9 și meniuri comune. Serviciul tastelor era legat la procesul curent. Recuperarea sa limitată este implementată; eliminarea tuturor blocajelor nu este demonstrată. |
| **Telefonie** | Funcțiile de bază au rezultate reale, cu acoperire incompletă | Apel primit și două apeluri inițiate bidirecționale, SMS și LTE pe SIM 1. SIM 2, DTMF și IMS au nevoie de probe distincte. [Probe noi](android81/STABILITY-PHONE-20261020.md). |
| **Media** | Una dintre zonele cele mai avansate ale portului | Muzică, căști, FM/RDS și A2DP au confirmări reale. Modulul A2DP este încărcat în politica audio. Microfonul Bluetooth, apelurile prin accesorii și toate variantele de codec nu sunt validate. |
| **Cod și structură** | Sursele Leo sunt în repository, împreună cu scripturi și documentație | Inventar actualizat: 145 fișiere Java în shell, plus 33 fișiere Java de probe Android și 4 fișiere Java host; tastatura și uneltele native sunt separate. Numărul fișierelor descrie inventarul, nu acoperirea testelor. |
| **Recompilare** | Există o bază reproductibilă documentată, încă netestată integral pe un PC curat | Toate cele **590 de proiecte AOSP** din manifest au revizii SHA fixate. Arborele AOSP, dump-ul și componentele proprietare necesare sunt intrări separate. |
| **Verificare automată** | Există teste locale și probe pe telefon; lipsește încă o rulare continuă centralizată | Testele cozii muzicale au trecut în ultima etapă. Alte rezultate sunt legate în documentație. Repository-ul nu are workflow-uri GitHub Actions; suita completă nu a fost rerulată în acest audit. |
| **Livrare și mentenanță** | Proiect de dezvoltare, încă fără release pentru instalare generală | Scripturile depind de mediul local și includ verificări pentru aparatul de dezvoltare. Lipsesc încă o procedură completă de release și un fișier LICENSE la rădăcină. |

**Concluzie:** avem un prototip utilizabil în probe, cu interfață proprie și funcții
confirmate pe hardware real. Prioritatea următoare este
să facem aceste rezultate repetabile: pornire, taste, apeluri, autonomie și recuperare.

## Ce mai avem de construit

Etapa nouă leagă aplicațiile Leo prin căutare globală, notificări grupate, personalizare
și backup selectiv al contactelor/SMS. Agenda, galeria și fișierele rămân baza acestor fluxuri.
[Implementare, probe și limite](android81/LEO-FIVE-APPS.md). Telefonia și hardware-ul
rămas au în continuare scenarii separate de validat.
USB, performanța și autonomia sunt amânate momentan la alegerea utilizatorului;
acest lucru nu înseamnă că limitele lor au fost rezolvate.

| Prioritate | De lucru | Cum știm că este gata |
| --- | --- | --- |
| **1 · Stabilitate** | Blocaje Leo, întreruperi ADB/USB, pornire și repornire; DeskClock stock corectat, STOP fără alarmă activă verificat, reboot încă de testat | Serii documentate de porniri și navigare, cu recuperare verificată și fără pierderea controlului din taste. |
| **2 · Telefonie completă · activ** | Telefon și ecran de apel native Leo Night, pe backend Telecom; rol Leo activat, demo și contract de integrare verificate. Probele SIM 2 urmează după maturizarea interfeței | Apeluri reale prin noua interfață, DTMF, respingere și rute audio; ulterior SIM 2 și reboot. VoLTE și VoWiFi validate separat. |
| **3 · Autonomie** | Consum în repaus, suspendare, încărcare și utilizare pe baterie | Măsurători fără alimentare USB și sesiuni mai lungi, cu metodă și rezultate publicate. |
| **4 · Hardware rămas** | Cameră, GPS și integrarea senzorilor | Probe pe hardware real; camera rămâne momentan în afara etapei active, la cererea utilizatorului. |
| **5 · Aplicații și accesibilitate** | T9 mai bun, email SMTP, browser/WebView, aplicații Android dificil de folosit din taste | Trimitere reală de email, scenarii T9 mai largi și fluxuri complete controlabile din taste. |
| **6 · Release de proiect** | Build de la zero, teste automatizate, pachet și recuperare documentată, clarificarea licenței codului Leo | Alt mediu poate reconstrui versiunile publicate urmând documentația; intrările și limitele sunt explicite. |

### Limite cunoscute, pe scurt

- **Camera, GPS și integrarea senzorilor Android:** nefuncționale în starea documentată.
- **Vibrații:** calea audio funcționează fizic și prin API-ul Android adaptat. Amplitudinea variabilă, efectele predefinite și scenariile simultane cu apel/media nu sunt validate. [Corecția și probele](android81/LEO-HOME-HAPTICS.md#vibrații-cauza-și-corecția).
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
| T9 și Browser · în așteptarea probelor Android | [Cuvinte și pagini din taste](android81/LEO-KEYPAD-WEB.md) |
| Organizator · în așteptarea probelor Android | [Calendar, Galerie, playlisturi, notificări, program sonor și diagnostic](android81/LEO-ORGANIZER.md) |
| Fluxuri noi · în așteptarea probelor Android | [Fișiere, partajare, contacte, Home și aplicații](android81/LEO-PROJECT-FLOWS.md) |
| Pachet aplicații numerotat | [Release apps, manifest și verificare](android81/RELEASE-APPS.md) |
| Leo pentru fiecare zi | [Telefon, SMS, ceas de birou, colecții, LRC și ajutor](android81/LEO-COMFORT.md) |
| Acces rapid și unelte personale | [Ceas, Notițe, Galerie, Backup și Stocare](android81/LEO-DAILY-EXTRAS.md) |
| Șase rafinări zilnice | [Home, notificări, T9, stil flat și măsurări](android81/LEO-DAILY-REFINEMENTS.md) |
| Centrul media | [Muzică, A2DP și recuperarea tastelor](android81/LEO-MEDIA-CENTER.md) |
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
