# KaiOS → Leo · instalare curată și revenire

Procedură pentru **Nokia 6300 4G TA-1286 de dezvoltare, serial `695d`**, cu
geometria verificată în acest proiect. Pachetele sunt private și conțin runtime
proprietar din copia acestui telefon. Starea: **construite și verificate offline;
instalarea curată și revenirea completă nu sunt încă probate pe hardware**.

## Ce înseamnă „de la zero” aici

Punctul de plecare este KaiOS funcțional **cu root și ADB deja obținute**.
Procedura nu include obținerea root pe un telefon stock blocat, deblocarea
bootloaderului sau recuperarea unui telefon care nu mai pornește în recovery.
Fișierele sunt verificate pentru acest exemplar; instalatoarele refuză alt serial.

ZIP-ul Leo este un pachet pentru **instalare prin ADB din recovery root**, cu
Python pe PC. Nu este semnat pentru „Apply update” din recovery-ul stock.
Boot și system sunt suficiente împreună cu o partiție userdata nouă: aplicațiile
și serviciile necesare sunt integrate în system, iar setările se generează la
prima pornire. Datele personale nu sunt incluse.

## Fișierele pregătite

În `android81/output/`, ignorat de Git:

| Fișier / director | Rol |
| --- | --- |
| `recovery-bundles/final/leo-fresh-0.14.0-dev-20261027-232615.zip` | Boot, system adaptat și userdata ext4 nouă, goală |
| `recovery-entry-kit/leo-root-recovery-695d.zip` | Recovery cu ADB root + instalator pentru intrarea din KaiOS |
| `recovery-entry-kit/kaios-original-restore-695d.zip` | Boot/system KaiOS originale pentru revenire |
| `recovery-entry-kit/stock-backups/` | Originalele boot/system/recovery, verificate SHA-256 |
| `recovery-entry-kit/checksums.json` | Hash-urile pachetelor de intrare și revenire |

Recovery-ul din kit corespunde copiei `recovery-adbshell.img` păstrate în
`Desktop/good 6300 files`, SHA-256
`2e4dee03305c5b617d5b0d51c064af350c817f3dc93f669ec302bdd97adf6fdc`.
Recovery-ul SD de diagnostic aflat ulterior pe telefon poate porni framework-ul
Android; acesta nu satisface condițiile instalatorului. Este necesar un proces
`recovery` real, root ADB și partițiile țintă demontate.

## 1. Copii și verificări pe PC

Păstrează fișierele originale și o copie a datelor personale **în afara telefonului**.
Instalarea curată șterge contactele, mesajele, conturile, aplicațiile instalate și
fișierele utilizatorului din memoria internă. Cardul extern este folosit pentru
staging și trebuie să aibă **cel puțin 3,3 GiB liberi**, fără a-l formata.
Directorul temporar de pe PC are nevoie de același spațiu.

Cu KaiOS încă stock și root, `backup-kaios-internal.py` poate citi boot/system
prin ADB sync. Nu îl rula pe Leo pentru a produce o presupusă copie KaiOS.
Kitul de mai sus păstrează originalele deja verificate ale acestui exemplar.

Comenzile următoare se rulează în PowerShell, din rădăcina proiectului:

```powershell
$adbLeo = 'D:/ROMS/platform-tools/adb.exe'
$freshLeo = 'android81/output/recovery-bundles/final/leo-fresh-0.14.0-dev-20261027-232615.zip'
$stockLeo = 'android81/output/recovery-entry-kit/stock-backups'
python android81/install-recovery-bundle.py $freshLeo
```

Această verificare este exclusiv locală: structură ZIP, SHA-256 pentru fiecare
intrare, dimensiuni și antete boot/ext4. Instalarea cere suplimentar cele trei
originale din `$stockLeo`, cu hash-urile exacte.

## 2. Intrarea în recovery root din KaiOS

Extrage `leo-root-recovery-695d.zip` într-un director de pe PC. Verifică imaginea:

```powershell
python android81/install-root-recovery.py CALE/recovery-adbshell.img
python android81/install-root-recovery.py CALE/recovery-adbshell.img --preflight --serial 695d --adb $adbLeo
```

Preflight-ul cere root, serialul și geometria corecte, apoi hash-urile stock ale
boot/system. Dacă rulează deja Leo, refuză. După verificare, **următoarea comandă
scrie numai recovery**, cu backup pe PC și verificare înainte/după scriere:

```powershell
python android81/install-root-recovery.py CALE/recovery-adbshell.img --install --serial 695d --confirm-target 695d --adb $adbLeo
& $adbLeo -s 695d reboot recovery
```

Instalatorul nu repornește singur și nu restaurează automat recovery-ul original.
Dacă recovery-ul root potrivit este deja instalat, se folosește direct; nu se
rescrie inutil. Confirmă `uid=0` în ADB și existența procesului `recovery`.

## 3. Pregătirea cardului în recovery

Țintele `system` și `userdata` trebuie să fie demontate în namespace-ul recovery
și al PID 1. Cardul trebuie montat separat, din `mmcblk1p1` (`179:65`). Folosește
mai întâi comenzile de citire:

```powershell
& $adbLeo -s 695d shell '/system/bin/busybox cat /proc/1/mountinfo'
& $adbLeo -s 695d shell '/system/bin/busybox cat /sys/class/block/mmcblk1p1/uevent'
```

Dacă recovery le-a montat, demontează `/system` și `/data` din meniul lui sau
prin `busybox umount`, verificând apoi ambele liste `mountinfo`. Nu demonta
cardul folosit pentru staging. Pentru card FAT32 deja identificat, dacă nu este
montat, se poate folosi `busybox mkdir -p /sdcard` și
`busybox mount -t vfat /dev/block/mmcblk1p1 /sdcard`. Dacă nodul sau tipul
filesystemului diferă, inspectează-l înainte; nu formata cardul pentru această probă.

## 4. Preflight și instalare curată

```powershell
python android81/install-recovery-bundle.py $freshLeo --preflight --serial 695d --adb $adbLeo
```

Acesta nu scrie partiții. Refuză Android activ, absența procesului recovery,
geometrie diferită sau system/userdata montate. Poate crea numai helperul în
`/tmp` și aliasurile de noduri din `/dev`.

**Comanda de mai jos șterge toate datele interne. Nu a fost executată în etapa
de pregătire a kitului:**

```powershell
python android81/install-recovery-bundle.py $freshLeo --install --serial 695d --confirm-target 695d --wipe-data --stock-backups $stockLeo --staging-parent /sdcard --adb $adbLeo
```

Ordinea este: verificarea originalelor → copierea tuturor imaginilor pe SD →
verificarea SHA-256 a stagingului → reverificarea montărilor → system → userdata
nouă → **boot ultimul**. Fiecare partiție scrisă este citită integral și verificată.
La orice eroare se oprește; păstrează recovery și fișierele de staging pentru
diagnostic. Nu există tranzacție atomică între partiții și nu se repornește automat.

Modem, modemst/NV, persist, GPT și recovery nu sunt ținte ale acestui instalator.
Calibrarea și identitatea radio rămân pe telefon.

După mesajul de verificare completă:

```powershell
& $adbLeo -s 695d reboot
```

Prima pornire poate dura mai mult pentru compilarea ART. Validarea reală rămasă:
Home Leo, tastatură, rețea/IMS, audio, FM și permisiuni după userdata complet goală.
Nu considera această etapă validată doar pentru că ZIP-ul trece verificarea locală.

## 5. Revenire la KaiOS

Intră în același recovery root, fără Android activ și fără system montat.
Verifică și instalează pachetul original:

```powershell
$restoreLeo = 'android81/output/recovery-entry-kit/kaios-original-restore-695d.zip'
python android81/install-recovery-bundle.py $restoreLeo
python android81/install-recovery-bundle.py $restoreLeo --preflight --serial 695d --adb $adbLeo
python android81/install-recovery-bundle.py $restoreLeo --install --serial 695d --confirm-target 695d --staging-parent /sdcard --adb $adbLeo
```

Pachetul restaurează system și boot originale; recovery rămâne disponibil.
**Execută „wipe data/factory reset” din recovery-ul KaiOS înaintea pornirii stock**:
datele Android nu sunt o copie a datelor KaiOS. Datele personale se restaurează
separat din copia utilizatorului. Originalul `recovery-device.img` rămâne pe PC;
restaurarea lui este o etapă separată, după confirmarea pornirii KaiOS.

## Reconstruirea pe acest PC

Necesită snapshot-ul privat verificat, WSL `kaios`, AOSP/SDK/JDK și compilatoarele
locale documentate în proiect, `debugfs`, `e2fsck`, `make_ext4fs` și BusyBox ARM
static. Sursele Git singure nu înlocuiesc bloburile proprietare.

```powershell
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-terminal-root.sh
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-fm-diagnostic.sh
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-visual-shell.sh
python android81/build-recovery-bundle.py android81/output/rom-snapshots/20261027-232615 --fresh --output android81/output/recovery-bundles/ALTA_CONSTRUCTIE
python android81/build-recovery-entry-kit.py
```

Constructorii refuză suprascrierea arhivelor existente. Ultima comandă cere un
director de kit încă neîmpachetat sau mutarea arhivelor vechi într-o arhivă locală.
Uneltele nu accesează telefonul. Constructorul repară offline numai paddingul
bitmapului imaginii userdata goale produs de vechiul formatter Android, apoi cere
`e2fsck -f -n` fără erori pentru ambele filesystemuri.

## Audit `/data` → `system`

| Componentă necesară | Locație în ROM-ul pregătit |
| --- | --- |
| Leo Visual și actualizările aplicațiilor de sistem | Locațiile factory `system/app` și `system/priv-app` |
| Tastatura Leo, anterior doar APK utilizator | `system/app/LeoKeypad/LeoKeypad.apk` |
| FM: executabile, utilitar Qualcomm, bibliotecă QMI, trace și audio.jar | `system/vendor/leo-fm/` |
| Broker root și client | `system/bin/leo-terminal-root`, `system/bin/leo-su` |
| Inițializare Home, IME, permisiuni și autorizație root | `system/etc/leo-first-setup.sh` |
| Armarea inițială audio, o singură dată | `system/etc/leo-clean-audio-seed.sh`, înainte de preload |
| Proprietăți hardware necesare | Valori explicite în `system/build.prop`, cu override utilizator ulterior |

În `/data` rămân UID-ul autorizat generat la boot, markerii primei porniri,
loguri/lock-uri FM, compilarea ART, cache-urile și datele utilizatorului. Nu se
mută conturi, parole, mesaje, baze de date sau calibrări în `system`. Aplicațiile
de probă/instrumentare nu intră în ROM. Manifestul include auditul cu hash-ul
fiecărui fișier adăugat; APK-ul Leo din candidatul curat este recompilat pentru
noua locație FM și diferă de cel încă instalat pe telefon.


## Rezultatul verificării acestui kit

Pachetul curat are **351.257.972 octeți** (aproximativ 335 MiB), SHA-256:

```text
5f65e7e7b0c8ce0ce8b24cf5ff9617e3d078a2a76172c4f4df7628063bdd9478
```

- 29 de teste host trecute: arhive, ținte, confirmarea ștergerii, backup-uri,
  ordinea boot-last și refuzul bootstrapului pe un sistem care nu este stock.
- `e2fsck -f -n`: cod 0 pentru system și userdata; în userdata există doar
  `lost+found`, fără date copiate din telefon.
- 15 fișiere de runtime/configurație integrate în system, fiecare verificat
  prin extragere și SHA-256, suplimentar APK-urilor active din snapshot.
- Preflight-ul real cu Android pornit a refuzat: `Active Android detected`.
  Telefonul și-a păstrat boot ID-ul; nu au fost scrise partiții și nu a fost repornit.
- Rămân de validat: flash-ul propriu-zis, prima pornire după ștergerea datelor
  și revenirea la KaiOS cu kitul nou.

Manifestul candidatului înregistrează commit-ul de bază și
`working_tree_dirty=true`, împreună cu hash-urile uneltelor folosite. Nu este
prezentat drept o reproducere bit cu bit dintr-un checkout curat.
