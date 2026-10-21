# Recompilare Leo Android 8.1

Repository-ul păstrează sursele aplicațiilor Leo, configurația dispozitivului,
uneltele native și scripturile de portare. `repro/aosp-manifest.xml` fixează cele
590 de proiecte upstream din checkout-ul de dezvoltare prin SHA de commit.
`repro/sources.json` și patch-urile asociate păstrează modificările locale AOSP.
Sursele AOSP se descarcă din Android Git; nu sunt duplicate ca arhivă în GitHub.

**Limite:** acesta este un pachet de surse și rețete de dezvoltare. Reconstruirea
de la zero pe un PC nou nu a fost validată integral. Nu oferă încă un ROM complet
instalabil, iar sistemul folosit pe telefon include componente proprietare din
dump-ul original. O recompilare nu înseamnă că imaginile rezultate pot fi scrise
direct pe partiții. Nicio comandă din `repro/` nu folosește ADB și nu flashează.

## Recompilarea numai a aplicațiilor Leo

Există un [generator de pachet numerotat](RELEASE-APPS.md), cu preflight, două APK-uri,
manifest SHA-256 și instalator explicit. Scripturile aplicațiilor acceptă acum
`LEO_AOSP_ROOT` și `LEO_JAVA_HOME`; valorile implicite rămân cele de mai jos.
Acesta nu înlocuiește pregătirea arborelui AOSP și nu construiește un ROM complet.

## Mediul utilizat

Build-urile actuale rulează în WSL/Linux x86-64, Ubuntu 22.04.5, cu Git 2.34.1,
OpenJDK 8 (1.8.0_504), Python 2.7.18 pentru uneltele Android vechi și Python
3.10.12 pentru scripturi. Nu folosi Java 17 în locul Java 8 pentru acest arbore.
Sunt necesare și repo, make, GCC/G++, multilib, rsync, zip/unzip, curl, bison,
flex, gperf, bc și bibliotecile host cerute de AOSP 8.1. Arborele complet și
intermediarii ocupă mult mai mult spațiu decât acest repository.

Scripturile existente folosesc intenționat `/root/android81-leo/src` și
`/usr/lib/jvm/java-8-openjdk-amd64`. Folosește aceste căi în prima recompilare.
`stage-device.sh` găsește acum sursele dispozitivului relativ la repository,
indiferent unde ai clonat proiectul. Unele unelte istorice de instalare/diagnostic
au încă trasee Windows și WSL specifice PC-ului de dezvoltare.

## Obținerea surselor

Clonează repository-ul privat cu autentificarea ta GitHub. În WSL/Linux:

```bash
git clone https://github.com/smp971/nokia-6300-android81.git
cd nokia-6300-android81
bash android81/repro/checkout.sh
```

Checkout-ul folosește manifestul din clona locală Git. Remote-urile AOSP din
manifest sunt absolute, astfel încât sursele Android să vină din Android Git.
Un checkout existent nu este resincronizat sau resetat automat.

Pentru verificarea și aplicarea modificărilor pe o bază exactă:

```bash
python3 android81/repro/apply.py --check-only
python3 android81/repro/apply.py
```

Scriptul verifică reviziile și hash-urile, refuză conflictele cu editări locale
și recunoaște patch-urile deja aplicate. Configurația `device/nokia/leo` este
stagiată separat din sursele canonice din acest repository.

## Fișiere locale ale dispozitivului

`device/nokia/leo/prebuilt/kernel` nu este în Git. Ai nevoie de kernelul potrivit
TA-1286, extras din propria copie de boot, sau de un kernel recompilat și validat.
Nu îl înlocui cu o imagine goală doar ca să treacă build-ul.

Pentru hardware sunt necesare bibliotecile/firmware-ul/modulele originale KaiOS,
extrase în structura locală a proiectului. Driverele Qualcomm din dump nu au cod
sursă disponibil în acest repository. Partițiile modem/EFS, datele utilizatorului,
cheile ADB private și credențialele nu se publică. Configurația opțională de ADB
folosește numai cheia **publică** proprie în `device/nokia/leo/local/adb_keys`.

Sursa GPL a kernelului este referită prin `fetch-leo-kernel.sh`: proiectul
`bmndc/nokia-leo`, commit `949a9f7b2c109f307e72b25fec2dc5cb4d119dec`, directorul
`kernel/msm-4.9`. Configurația originală este păstrată sub
`device/nokia/leo/reference-stock/`; `build-kernel-uhid.sh` documentează build-ul
UHID și dependența de certificatul public extras cu uneltele locale. Aceasta nu
oferă automat un boot.img pentru instalare.

## Aplicațiile Leo

După configurarea hostului și furnizarea kernelului local:

```bash
bash android81/repro/build-apps.sh
```

Acesta pregătește uneltele host și biblioteca vCard, apoi compilează:

- `android81/output/leo-visual.apk`: HOME și aplicațiile Leo;
- `android81/output/leo-keypad-ime.apk`: tastatura fizică multi-tap / T9.

Dacă arborele AOSP a fost deja compilat, editările de UI necesită doar:

```bash
bash android81/build-visual-shell.sh
```

Ultima variantă Leo a fost recompilată, instalată și verificată pe telefon.
Rețeta `repro/build-apps.sh` a fost rulată cu succes pe checkout-ul actual: a
pregătit uneltele și a recompilat ambele APK-uri. Separat, cele zece patch-uri
au trecut o probă de aplicare pe fișierele upstream originale; rezultatul pentru
toate cele 25 de fișiere modificate a coincis cu sursele de dezvoltare. Această
probă nu înlocuiește validarea completă pe un host nou.
`build-visual-shell.sh` include automat toate fișierele Java din `visual-shell/`.
Semnarea folosește cheile de dezvoltare AOSP din checkout, nu chei private ale
utilizatorului. Nu înlocui semnatarul unei aplicații instalate păstrând datele.

## Alte componente

| Componentă | Rețetă |
| --- | --- |
| Email AOSP, resurse corectate și comenzi din taste | `build-email-resources.sh` |
| Interfața Android pentru apeluri din taste | `build-call-keypad.sh` |
| Reînnoirea ADB la schimbarea modului USB | `build-usb-adbd-renew.sh` |
| Lumina tastelor | `build-keylight.sh` |
| Decodor WMA Leo | `build-wma-decoder.sh` |
| Kernel UHID | `fetch-leo-kernel.sh`, `build-kernel-uhid.sh` |
| Sistemul AOSP experimental | `build-framework-diagnostic.sh` |

`build-six-feature-tests.sh` compilează separat verificările pe dispozitiv,
inclusiv UpgradeTests pentru reordonare, scurtături, T9 și notificări/blocare.
APK-ul de producție nu include aceste date sau puncte de intrare de test.
Instrumentarea pornește/oprește procesul Leo; după ea trebuie verificată
reconectarea serviciilor de notificări și accesibilitate. Probele folosesc
contacte/notificări sintetice și restaurează preferințele locale.

FFmpeg 4.4.8 este descărcat de rețeta WMA și verificat cu SHA-256 fixat în script;
opțiunile de configurare și notificarea LGPL sunt documentate în
[MEDIA-RUNTIME.md](MEDIA-RUNTIME.md). Nu este necesar un pachet binar WMA ascuns.

Build-ul complet necesită și configurarea serverului Jack vechi. Rețetele
`environment.sh` și `configure-jack-framework.py` descriu heap-ul și concurența;
certificatele locale Jack se generează pe noul host. Fișierul opțional de
compatibilitate TLS locală nu este furnizat ca o modificare globală de securitate.

Rețeta de sistem rămâne parte a procesului experimental. Asamblarea runtime-ului
hardware, verificarea ABI-urilor și instalarea sunt etape separate, documentate
în auditurile dispozitivului. Nu rula scripturi de flash ca etapă implicită de
recompilare. Notele istorice descriu și experimente abandonate; începe cu README-ul
principal și tabelul actual al funcțiilor.
