# Pachetul aplicațiilor Leo · 0.13.0-dev

Versiunea curentă este `0.13.0-dev`, cod 13. Ambele aplicații au fost compilate
individual în această etapă; pachetul colectează exact APK-urile verificate pe
telefon cu `--package-only` și înregistrează corect `rebuilt=false`.
[Rezultatele etapei](LEO-13-DAILY-TOOLS.md).
Pentru reconstruire completă folosește `--build`, după `--check`.

Acesta este pachetul celor două aplicații. Cele trei APK-uri Android adaptate
se construiesc separat cu `build-framework-keypad.sh` și se instalează prin
`install-system-keypad.py --only all`; nu sunt incluse aici. Pentru boot/system
există separat [constructorul privat recovery](RECOVERY-BUNDLE.md).

## Context istoric · 0.4–0.5

Acesta actualizează **Leo Visual și Leo Keypad** peste un port Leo Android 8.1
existent. Nu este un ROM instalabil și nu conține boot/recovery, modem, dump,
firmware proprietar sau datele utilizatorului.

Versiunea 0.4.0-dev include și etapele 0.1.0-dev, 0.2.0-dev și 0.3.0-dev și este
instalată pe Nokia. Ulterior au fost integrate [camera BF20A1](CAMERA-RUNTIME.md)
și [finisarea UI și derularea mouse-ului](LEO-UI-FOUNDATION.md), păstrând codul 4.
Commit-ul și hash-urile din manifest identifică snapshot-ul exact; eticheta
0.4.0-dev singură nu distinge toate aceste build-uri de dezvoltare.

Snapshot-ul local din 23 octombrie a fost generat cu `--package-only`, din APK-urile
construite anterior, și verificat prin instalatorul offline. Manifestul său indică
`rebuilt=false`. Runtime-ul proprietar al camerei se instalează separat și nu este
inclus în pachetul celor două aplicații.

## Etapa istorică · 0.5.0-dev / cod 5

Include [meniul pe categorii, ferestrele recente, dosarele SMS și controlul
blițului](LEO-MATURITY.md), pe lângă etapele anterioare. Ambele APK-uri sunt
reconstruite și numerotate 0.5.0-dev; codul funcțional al tastaturii este păstrat.
Runtime-urile hardware sunt separate de pachetul aplicațiilor.

## Recompilare

Sunt necesare intrările descrise în [REBUILD](REBUILD.md): arborele AOSP fixat,
Java 8, SDK 27, host tools AOSP și biblioteca `com.android.vcard` deja construită.
Scriptul nu descarcă dependențe și nu construiește întregul AOSP în mod ascuns.
În Linux/WSL, din rădăcina repository-ului:

```bash
python3 android81/release-apps.py --check
python3 android81/release-apps.py --build
```

Pentru alte locații:

```bash
python3 android81/release-apps.py --build \
  --aosp-root /cale/aosp --java-home /cale/jdk8 \
  --output /cale/pachete
```

Variabilele `LEO_AOSP_ROOT` și `LEO_JAVA_HOME` sunt acceptate și de cele două scripturi
individuale de build. Directorul de versiune nu este suprascris: alege alt `--output`
sau mută pachetul precedent înaintea unei noi generări.

`release-version.json` și ambele manifeste Android trebuie să aibă aceeași versiune
și același cod numeric. Pentru o versiune nouă, mărește codul și schimbă toate trei.
Nu se schimbă cheile de semnare: build-ul folosește cheia de dezvoltare AOSP deja
folosită de proiect, pentru compatibilitatea actualizărilor.

## Rezultat și verificare

`leo-apps-0.13.0-dev/` conține:

- `leo-visual.apk` și `leo-keypad-ime.apk`;
- `manifest.json`: versiune, țintă, dimensiuni/hash-uri, commit, starea arborelui,
  hash-urile surselor și ale intrărilor de build;
- `install-app-bundle.py` și `README.txt`.

Se produce și `leo-apps-0.13.0-dev.zip`, al cărui SHA-256 este afișat. Din folderul
extras, pe Windows sau Linux:

```bash
python install-app-bundle.py
```

Comanda implicită verifică exclusiv cele patru fișiere din manifest, fără ADB.
`--package-only` din generator colectează APK-uri existente și marchează `rebuilt=false`;
nu reprezintă o recompilare verificată. `--build` reconstruiește ambele APK-uri și
înregistrează hash-urile SDK-ului, uneltelor, certificatului și bibliotecii vCard.
Manifestul SHA-256 verifică integritatea, nu este o semnătură de release.

## Actualizare explicită

```bash
python install-app-bundle.py --install --serial SERIA_TELEFONULUI --adb CALE_ADB
```

Instalatorul cere serial explicit, API 27 și prezența ambelor pachete Leo. Modul
implicit `leo` folosește `/sbin/leo-framework-exec` și citește exporturile Java din
`/init.environ.rc`, fără arhive locale sau căi Windows fixe. Pentru un namespace ADB
Android convențional există `--mode android`.

Actualizează întâi tastatura, apoi shell-ul. Nu schimbă HOME, dialerul sau IME-ul,
nu formatează și nu scrie partițiile sistemului. Nu este un instalator pentru un
KaiOS nemodificat și nu realizează migrarea Android. Nu activează privilegii noi în
baza `/system`; instalatorul local existent de sincronizare rămâne separat.
Dacă al doilea APK eșuează, primul poate rămâne actualizat; nu există rollback automat.

## Limita reproductibilității

Build-ul a rulat în mediul de dezvoltare existent. Recompilarea completă de la zero
pe un PC curat rămâne nevalidată. Intrările și hash-urile sunt urmărite; APK-uri
identice octet cu octet între două build-uri nu sunt promise, inclusiv din cauza
metadatelor ZIP. Instalatorul nou nu a fost executat pe telefon în această etapă,
deoarece ADB nu este conectat.

Probe offline:

```bash
python -m unittest discover -s android81/tests -p test_app_bundle.py
bash android81/run-host-project-checks.sh
```

Al doilea test cere mai întâi compilarea shell-ului. Probele Android pentru noile
fluxuri sunt compilate, dar trebuie rulate separat pe aparat.

## Actualizatorul din telefon

Leo 0.4.0 adaugă verificarea pachetului ZIP și sesiuni Android pentru cele două
APK-uri, cu păstrarea copiei precedente. Nu execută scriptul Python din arhivă.
Fluxul și revenirea rămân de verificat pe Nokia: [stare și limite](LEO-POCKET-SUITE.md).
Prima instalare a acestei versiuni poate fi făcută în continuare prin ADB, folosind
comanda explicită de mai sus.
