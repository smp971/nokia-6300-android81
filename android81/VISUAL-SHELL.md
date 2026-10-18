# Leo Visual — primul prototip de interfață

**19 octombrie 2026.** Aplicație Android separată, `ro.leo.visual`, inspirată de
navigarea KaiOS. Nu înregistrează HOME, servicii de telefonie sau modificări de
boot. Leo Shell rămâne interfața HOME implicită.

## Implementat

- Home cu ceas, dată în română, fundal vectorial și două variante de culori.
- Două rânduri SIM: operator și stare citite separat pentru fiecare slot.
- Grilă 3×3: Telefon, Contacte, Mesaje, Ceas, Calculator, Galerie, Muzică, Fișiere, Setări.
- Selecție din D-pad, etichete pentru tastele soft și opțiuni native.
- Lansarea aplicațiilor Android instalate; mesaj „indisponibil” dacă nu există o țintă.
- Desenare nativă Canvas; iconuri și fundal originale, fără resurse copiate din KaiOS.
- Actualizare la schimbarea stării telefoniei, la revenirea în aplicație și la minut pentru ceas. Fără animație sau buclă continuă de randare.

Acesta este un prototip de shell, nu portarea tuturor aplicațiilor KaiOS. Tile-ul
Mesaje nu instalează o aplicație SMS. Aplicațiile Android lansate își păstrează
interfețele existente și nu devin automat adaptate complet la taste.

## Comenzi

| Tastă | Home | Grila de aplicații |
| --- | --- | --- |
| OK | Deschide grila | Deschide aplicația selectată |
| Săgeți | Deschide grila | Deplasează selecția, cu limite la margini |
| Soft stânga / Meniu | Opțiuni | Opțiuni |
| Soft dreapta / Înapoi | Iese din prototip | Revine la Home-ul prototipului |
| Verde | Deschide Dialer, fără a efectua un apel | La fel |
| 1–9 | Fără acțiune specială | Selectează tile-ul corespunzător; OK îl deschide |
| Roșu | Politica Android ENDCALL; revine în Leo în repaus | La fel |

Opțiunile includ schimbarea fundalului, explicația comenzilor și ieșirea în Leo.
În timpul unui apel, tasta roșie păstrează comportamentul global de închidere a
apelului. Prototipul nu interceptează această tastă.

## Starea ambelor SIM-uri

Folosește `SubscriptionManager`, câte un `TelephonyManager` pentru fiecare
abonament și `ServiceState`, cu permisiunea Android `READ_PHONE_STATE`.
Nu citește numerele de telefon, IMSI sau ICCID și nu le salvează în jurnal.
Permisiunea este folosită doar pentru citirea stării, nu pentru modificarea rețelei.

Captura verificată arată **SIM 1: Vodafone RO · 4G** și **SIM 2: DIGI RO · conectat**.
„Conectat” descrie înregistrarea în serviciu, nu o probă de internet. Eticheta 4G
provine din tehnologia de date raportată de Android; nu confirmă trafic activ sau VoLTE.
Sunt prevăzute și stări pentru SIM lipsă, PIN/PUK necesar, fără serviciu și radio oprit.
Aceste stări alternative nu au fost provocate fizic în această probă.

## Validare pe telefon

Pachetul s-a compilat și instalat. Au fost inspectate capturile Home și grilă,
inclusiv ambele rânduri SIM. Scriptul de probă a verificat:

- Deschiderea grilei cu OK și selecția Calculatorului prin D-pad.
- Lansarea Calculatorului instalat și revenirea în prototip prin Înapoi.
- Revenirea din grilă la Home și deschiderea/închiderea opțiunilor.
- Păstrarea `ro.leo.shell` ca HOME implicit; nu au fost efectuate apeluri.

O primă probă a fost întreruptă de pierderea conexiunii și repornirea telefonului,
nesolicitată de aceste scripturi. Proba completă a trecut după revenirea sistemului.
Nu atribuim repornirea prototipului și nici nu o considerăm rezolvată prin acest test.

Nu au fost verificate toate cele nouă lansări, consumul de baterie, latența față
de Leo sau funcționarea completă a fiecărei aplicații deschise. Nu au fost
înlocuite serviciile Android sau rescrise partițiile boot/recovery.

| Home cu două SIM-uri | Grila 3×3 |
| --- | --- |
| ![Home](screenshots/visual-shell-home.png) | ![Grilă](screenshots/visual-shell-grid.png) |

## Compilare și instalare în mediul proiectului

Surse: `visual-shell/MainActivity.java` și `visual-shell/AndroidManifest.xml`.
În WSL rulează `build-visual-shell.sh`; rezultatul este APK-ul local ignorat
`output/leo-visual.apk`. Folosește un director nou de compilare pentru a evita
clase rămase din build-uri precedente. Arborele AOSP și Java 8 trebuie pregătite.

Instalarea folosește fluxul existent ADB + `framework-command.py`: transfer APK,
`pm install -r`, apoi `pm grant ro.leo.visual android.permission.READ_PHONE_STATE`.
Se lansează explicit `ro.leo.visual/.MainActivity`. Permisiunea a fost acordată
prin ADB pentru a evita un dialog tactil pe telefonul cu taste.

`verify-visual-shell.py` păstrează capturi locale în `output/visual-shell-test/`
și verifică lansarea Calculatorului fără a apela numere. Nu este o probă de
compatibilitate pentru toate aplicațiile KaiOS.
