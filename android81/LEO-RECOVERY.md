# Leo Recovery · 0.1 prototype

Un recovery separat de Android, scris pentru ecranul **240 × 320** și tastele
Nokia 6300 4G TA-1286. Interfață nativă RGB565, fără framework Android, touchscreen
sau aplicație Java. Culori Leo Night: fundal închis și selecție turcoaz.

**Validat pe telefon la 28 octombrie 2026:** imaginea este instalată în recovery,
pornește direct, afișează meniul și răspunde la tastele fizice. ADB root rămâne
disponibil. Kernelul, init-ul și ADB-ul bazei root au fost păstrate.
**Instalarea unui ROM, backup-ul și restaurarea din meniu rămân netestate.**
Proba a scris numai partiția recovery; boot, system și userdata nu au fost modificate.

![Leo Recovery pe telefon](screenshots/leo-recovery-rgb565-main.png)

Prima probă a identificat un crash în rendererul minui de 32 biți: acest telefon
folosește RGB565 de 16 biți și un pas de linie de 512 octeți. Rendererul dedicat
respectă acest format, limitează scrierile la framebuffer și desenează fără GPU.
Imaginea corectată a trecut verificarea integrală SHA-256 după scriere și o nouă
pornire directă. Dovezile sunt legate de hash-ul exact în
`leo-recovery/hardware-validation.json`; un build diferit nu moștenește validarea.

## Meniul implementat

| Acțiune | Comportament în cod |
| --- | --- |
| Install Leo | Verifică sau instalează pachetul curat aprobat de pe SD |
| Backup partitions | Copiază boot/system/recovery pe SD, cu verificarea SHA-256 |
| Restore KaiOS | Scrie boot/system originale din pachetul de revenire aprobat |
| Diagnostics | Montare SD, informații și salvarea jurnalului pe card |
| Stock tools | Deschide recovery-ul original păstrat în ramdisk, cu textul afișat |
| Reboot system / Power off | Confirmare din taste, sync, apoi acțiunea cerută |

D-pad sus/jos schimbă selecția, inclusiv între primul și ultimul rând.
OK, verde sau soft stânga selectează; Înapoi/soft dreapta sau roșu revine.
Acțiunile sunt declanșate la eliberarea tastei; repetările unei taste ținute nu
confirmă instalarea. Fiecare confirmare de scriere începe pe **Cancel**.

Acest prototip acceptă **numai cele două ZIP-uri exacte verificate la construcție**.
Dimensiunea și SHA-256 ale arhivei, apoi hash-urile imaginilor sunt compilate în
executabil. Un ZIP schimbat, altă versiune sau un ZIP OTA generic este refuzat.
Pentru alt build trebuie regenerat catalogul și recompilat recovery-ul.
Nu sunt implementate sideload, executarea update-binary sau actualizări automate.
Comenzile OTA/BCB/cache nu sunt executate automat la pornire.

## Pachete și fișiere pe SD

Nu formata cardul. Copiază următoarea structură pe un card FAT32 sau ext4:

```text
LeoRecovery/
  packages/
    leo-fresh-0.14.0-dev-20261027-232615.zip
    kaios-original-restore-695d.zip
  stock-backups/
    boot-kaios.img
    system-kaios.img
    recovery-device.img
```

Sursele locale sunt în `output/recovery-bundles/final/` și
`output/recovery-entry-kit/`. Manifestul imaginii recovery indică exact hash-urile
acceptate. Recovery cere cardul real `mmcblk1p1` (`179:65`), montat read/write la
`/sdcard`; un director cu acest nume pe memoria internă nu este suficient.

Instalarea cere cel puțin **3,3 GiB liberi suplimentar** pe card pentru imaginile
de staging, după copierea ZIP-urilor și a originalelor. Fișierele temporare sunt
păstrate pentru diagnostic, inclusiv la eroare. Backup-ul boot/system/recovery
cere separat aproximativ 940 MiB liberi și **nu include date personale sau radio**.

## Protecțiile instalării

- Serialul trebuie să fie exact `695d`, recovery-ul trebuie să ruleze ca root și
  procesul `system_server` trebuie să lipsească.
- Geometria și major/minor sunt verificate din sysfs și pe nodurile block.
- System și, la instalarea curată, userdata trebuie să fie demontate atât în
  namespace-ul procesului, cât și al PID 1. Recovery nu le demontează forțat.
- Bateria trebuie să raporteze minimum 30%, sau alimentarea USB să fie prezentă.
- Cele trei originale stock trebuie să existe și să treacă verificarea SHA-256.
- Toate imaginile sunt extrase și verificate pe SD înaintea primei scrieri.
- Ordinea este **system → userdata goală → boot**, cu verificare după fiecare
  scriere. La revenirea stock sunt scrise numai system și boot, în această ordine.
- Recovery, modem, NV, persist și GPT nu sunt ținte ale instalatorului din meniu.
- Nu există reboot automat. O eroare după prima scriere cere păstrarea recovery-ului
  pentru diagnostic/restaurare; operația între partiții nu este atomică.

După revenirea la boot/system KaiOS, **resetarea datelor este o etapă separată în
Stock tools**, înaintea pornirii normale. Kitul nu restaurează contactele,
conturile sau mesajele utilizatorului.

## Imaginea construită și intrarea în recovery

Fișiere private locale:

```text
output/leo-recovery/leo-recovery-0.1.img
output/leo-recovery/leo-recovery-0.1-private.zip
output/leo-recovery/manifest.json
```

SHA-256 al imaginii de **33.554.432 octeți**:

```text
e9407739db7d7f17a57a1da19e67612d1047c34c53fc9bf97631da7cfce9ff5e
```

Verificarea exclusiv pe PC:

```powershell
python android81/install-root-recovery.py android81/output/leo-recovery/leo-recovery-0.1.img --leo
```

`--leo --preflight --serial 695d --adb D:/ROMS/platform-tools/adb.exe` verifică
telefonul fără scrieri de partiții. Pentru această variantă de instalator este
necesar portul Leo intern cunoscut, cu markerul `/leo-framework-internal`.
Modul implicit al scriptului rămâne bootstrap-ul recovery root din KaiOS stock.
Din recovery root cunoscut se folosește `--from-recovery` în loc de `--leo`;
acest mod verifică markerul bazei, PID 1 și absența framework-ului Android.

Comanda de instalare din portul Leo intern:

```powershell
python android81/install-root-recovery.py android81/output/leo-recovery/leo-recovery-0.1.img --leo --install --serial 695d --confirm-target 695d --adb D:/ROMS/platform-tools/adb.exe
```

Aceasta salvează și verifică recovery-ul existent pe PC, apoi scrie **numai p33**,
cu verificare completă. Nu formatează datele, nu schimbă boot/system și nu repornește
singură. După instalarea verificată, pornirea recovery-ului se cere separat prin
`adb -s 695d reboot recovery`. Dacă adbd din namespace-ul timpuriu Leo refuză
comanda, folosește wrapperul framework din proiect:

```powershell
python android81/framework-command.py --timeout 15 /system/bin/setprop sys.powerctl reboot,recovery
```

Nu se restaurează automat recovery-ul original.

## Reconstruire și verificări

```powershell
python android81/build-leo-recovery.py
python -m unittest discover -s android81/tests -p 'test_*.py'
wsl -d kaios -- bash -c 'g++ -std=c++14 -Wall -Wextra -Werror /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/leo-recovery/policy-test.cpp -o /tmp/leo-recovery-policy-test && /tmp/leo-recovery-policy-test'
```

Necesită WSL/AOSP 8.1 configurat local și kiturile private anterioare. Constructorul
compilează binarul static, păstrează kernelul și intrările ramdisk nelegate de UI,
recalculează antetul boot și verifică CPIO prin citire. Extinderea ramdiskului poate
ocupa numai zero-uri sau reziduul 8110 identificat byte cu byte; alte date sunt refuzate.
Kernelul de recovery rămâne identic celui KaiOS original. Fontul AOSP 12×22 este
redistribuit cu licența OFL inclusă. Imaginea nu este un ROM universal.

Testele host verifică politica de ținte, montările, ordinea boot-last, limitele
de peste 2 GiB, tastele, structura CPIO și protecția cozii imaginii. Aceste teste
nu înlocuiesc proba pe framebuffer, input și cardul telefonului.

Pentru KaiOS de pe card vezi [planul separat și starea pregătirii](KAIOS-SD.md).
