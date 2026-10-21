# Pachetul aplicațiilor Leo · 0.3.0-dev

Acesta actualizează **Leo Visual și Leo Keypad** peste un port Leo Android 8.1
existent. Nu este un ROM instalabil și nu conține boot/recovery, modem, dump,
firmware proprietar sau datele utilizatorului.

Versiunea 0.3.0-dev include și etapele 0.1.0-dev și 0.2.0-dev. Instalarea pe Nokia este încă în
așteptarea conexiunii ADB; [T9 și browser](LEO-KEYPAD-WEB.md), [organizator](LEO-ORGANIZER.md).

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

`leo-apps-0.3.0-dev/` conține:

- `leo-visual.apk` și `leo-keypad-ime.apk`;
- `manifest.json`: versiune, țintă, dimensiuni/hash-uri, commit, starea arborelui,
  hash-urile surselor și ale intrărilor de build;
- `install-app-bundle.py` și `README.txt`.

Se produce și `leo-apps-0.3.0-dev.zip`, al cărui SHA-256 este afișat. Din folderul
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
