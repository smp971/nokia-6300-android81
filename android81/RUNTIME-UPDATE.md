# Pachet local de actualizare Leo

`runtime-bundle.py` produce un ZIP verificabil pentru **portul Leo deja instalat
în memoria internă**, cu kernelul `4.9.240-perf-g4f9e270fd77f` și wrapper-ul root
existent. Nu instalează Android peste KaiOS și nu este ZIP flashabil în recovery.

## Conținut și proveniență

- Leo Visual și Leo Keypad recompilate local, cu versiunea din `release-version.json`.
- Runtime-urile private radio, audio, cameră, IMS și GPS instalate pe telefon.
- Fișierele init Leo și configurațiile audio/GPS incluse explicit în lista permisă.
- Manifest: dimensiuni, SHA-256, commit, hash-uri ale surselor și starea directorului Git.

Captura citește numai fișiere de sistem permise. Nu citește `/data`, cardul SD,
partițiile modem/NV, conturile sau cheile. Include biblioteci proprietare din dump;
ZIP-ul rămâne local și nu se comite automat pe GitHub.

Acesta este un **snapshot al configurației instalate**, cu o procedură repetabilă
de împachetare. Nu demonstrează o recompilare completă pe un PC curat sau rezultate
identice octet cu octet. Boot-ul, framework-ul modificat, serviciile FM/MTP,
calibrarea audio și configurația de bază Leo rămân premise ale actualizării.

## Pregătire

Recompilează aplicațiile prin `build-keypad-ime.sh` și `build-visual-shell.sh` în
mediul descris în [REBUILD.md](REBUILD.md). GNSS se construiește prin
`build-gps-runtime.sh`; celelalte runtime-uri au scripturile și instrucțiunile
din documentele lor. Păstrează aceiași semnatari pentru actualizarea APK-urilor.

```text
python android81/runtime-bundle.py OUTPUT.zip --capture --serial SERIAL --adb PATH_TO_ADB
python android81/runtime-bundle.py OUTPUT.zip
python android81/runtime-bundle.py OUTPUT.zip --check-device --serial SERIAL --adb PATH_TO_ADB
```

Verificarea fără opțiuni este offline. `--check-device` compară toate hash-urile
cu telefonul și nu scrie nimic. ZIP-urile cu fișiere suplimentare, hash-uri greșite,
destinații duplicate, căi în afara listei sau traversări `..` sunt respinse.

## Instalare și revenire

```text
python android81/runtime-bundle.py OUTPUT.zip --install --backup NEW_BACKUP_FOLDER --serial SERIAL --adb PATH_TO_ADB
python android81/runtime-bundle.py OUTPUT.zip --rollback NEW_BACKUP_FOLDER --serial SERIAL --adb PATH_TO_ADB
```

Instalatorul cere un serial explicit, verifică platforma și absența apelurilor,
salvează originalele într-un director nou pe PC, transferă și verifică toate
fișierele înaintea scrierilor. Fișierele de sistem sunt înlocuite prin redenumire;
`/system` revine read-only. APK-urile sunt actualizate cu datele aplicațiilor păstrate.
Nu schimbă rolurile HOME/IME/dialer, nu formatează și nu repornește automat.

Instalarea multor fișiere nu este o tranzacție atomică. O întrerupere USB sau o
eroare la un APK poate lăsa o actualizare parțială. Directorul de backup permite
revenirea explicită după reconectare; nu există recuperare automată garantată.
Nu șterge backup-ul până după verificarea unei porniri complete.

Probele offline se rulează cu `python android81/test-runtime-bundle.py`.
Instalarea și revenirea unui pachet întreg necesită o probă separată; verificarea
hash-urilor și testele offline nu sunt o validare a unui flash complet.
