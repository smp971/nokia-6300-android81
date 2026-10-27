# Copie privată și ZIP pentru recovery

Constructorul împachetează starea portului Leo existent pe acest Nokia. Nu este
un ROM universal, o migrare verificată din KaiOS nemodificat sau un ZIP semnat
pentru meniul stock „Apply update”. Instalarea este prin **ADB în recovery-ul
personalizat existent**, cu Python pe PC. Flash-ul pe hardware nu a fost validat
în etapa 0.12; nu a fost executat pentru a testa constructorul.

## Copia sursă

Cu Leo pornit complet, fără apel activ, serialul `695d` și `/system` read-only:

```powershell
python -X utf8 android81/snapshot-leo-rom.py
```

Se salvează în directorul ignorat de Git `android81/output/rom-snapshots/`:
boot (32 MiB), system (800 MiB), recovery (32 MiB, exclusiv backup), manifestul și
APK-urile actualizate ale aplicațiilor de sistem. Nu se copiază userdata, conturi,
SMS sau partiții radio. Fiecare imagine este verificată ca dimensiune și SHA-256
față de telefon. Transferul folosește ADB sync pe aliasuri de noduri block;
vechiul adbd nu suportă transportul binar `exec-out`.

Păstrează separat backup-ul KaiOS original. Snapshot-ul curent este o copie Leo,
nu o copie nouă a firmware-ului stock.

## Construire offline pe PC

Necesită Python, WSL `kaios`, `debugfs`/`e2fsck` și helperul ARM static existent
`output/recovery-shell-candidate`. Constructorul nu descarcă firmware.

```powershell
python -X utf8 android81/build-recovery-bundle.py android81/output/rom-snapshots/20261027-164004
```

Boot și system sunt copiate într-un director nou. Numai copia system este
modificată: APK-urile active sunt integrate la locațiile lor de sistem, cu
permisiuni și etichete SELinux; fiecare APK este extras din nou și verificat.
`e2fsck -f -n` și verificatorul ZIP trebuie să treacă. Recovery nu intră în ZIP.
Framework-ul și ART provin din aceeași imagine sursă; nu se schimbă izolat JAR-uri.

Manifestul înregistrează hash-urile, commit-ul, starea dirty a surselor, uneltele
folosite și `hardware_flash_validated=false`. Un build reușit verifică arhiva,
nu demonstrează pornirea unei imagini rescrise. Build-ul de pe o mașină curată
și reproducerea bit cu bit nu sunt încă validate.

## Verificare și instalare explicită

Extrage numai scriptul și README-ul pentru utilizarea pe PC; imaginile rămân în ZIP.

```powershell
python android81/install-recovery-bundle.py CALE_PACHET.zip
python android81/install-recovery-bundle.py CALE_PACHET.zip --preflight --serial 695d --adb D:/ROMS/platform-tools/adb.exe
```

Prima comandă este exclusiv offline. A doua cere recovery root, verifică absența
Android și a partiției system montate, identitatea dispozitivului și geometria
boot/system. Dacă rulează Android, trebuie să refuze fără scrieri de partiții.

Pentru instalare, este necesar un card SD extern deja montat, cu minimum
864 MiB liberi și același spațiu în directorul temporar de pe PC. Se poate preciza
`--staging-parent /sdcard`; directorul trebuie să aparțină efectiv `mmcblk1p1`.

```powershell
python android81/install-recovery-bundle.py CALE_PACHET.zip --install --serial 695d --confirm-target 695d --adb D:/ROMS/platform-tools/adb.exe
```

Această ultimă comandă este distructivă pentru conținutul boot/system. Copiază
mai întâi ambele imagini pe SD și verifică hash-urile, recontrolează starea
recovery, scrie system apoi boot și verifică imaginile scrise. Nu formatează
userdata, nu scrie recovery/modem/NV și nu repornește automat. Fișierele de staging
rămân pe SD. Nu întrerupe alimentarea; păstrează backup-urile în afara telefonului.

Arhivele conțin runtime proprietar al dispozitivului și rămân locale/private.
Pe GitHub sunt publicate sursele uneltelor și documentația, nu imaginile firmware.

## Snapshot construit în această etapă

`output/recovery-bundles/leo-recovery-0.12.0-dev-20261027-164004.zip`
a trecut construcția și verificarea offline, inclusiv citirea APK-urilor din
imaginea rezultată și `e2fsck -f -n`. SHA-256:

```text
3c66dad2f2d1eba59903c053271b0d1366a74d0ebf3befe6cf1779cc0537c707
```

Manifestul indică surse modificate față de commit-ul de bază (`working_tree_dirty=true`),
cu hash-urile exacte ale uneltelor. Nu a fost prezentat drept build dintr-un commit curat.
