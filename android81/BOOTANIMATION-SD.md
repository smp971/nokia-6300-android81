# Animație LEO și card microSD · 19 octombrie 2026

## Animație

Grafică originală generată de `make-bootanimation.py`: LEO alb, trei puncte mov
pe negru, 240×320, 6 cadre/s, 4 cadre de intrare și 6 în buclă. ZIP-ul are
aproximativ 37 KiB, cu intrări ZIP_STORED și PNG comprimate. Nu folosește fonturi
sau imagini din firmware.

```text
python android81/make-bootanimation.py
python android81/install-bootanimation.py --enable-on-boot
```

Generatorul necesită Pillow. Rezultatele sunt locale, în `output/`. Instalatorul
salvează originalele și verifică hashurile. `--restore` le restabilește.
Fișierele instalate sunt `/system/media/bootanimation.zip` și
`/system/etc/init/leo-bootanimation.rc`; boot/recovery nu sunt rescrise.

Ramdisk-ul diagnostic dezactiva animația din cauza vechiului renderer
SwiftShader. Hook-ul o reactivează pentru configurația Adreno actuală și o
oprește la `sys.boot_completed=1`, fără întârziere artificială. Utilizatorul a
confirmat logo-ul și punctele animate în proba de 30 s, apoi și la o **repornire
completă**, urmate de meniul Leo. Software: boot_completed=1, bootanim=stopped.
Sigla inițială a bootloaderului KaiOS rămâne separată.

## De ce lipsea cardul

Kernelul detecta cardul de aproximativ 32 GB, cu o partiție FAT32. Vold nu avea
o sursă de disc pentru el. În acest ramdisk există `/sbin/recovery`, iar Oreo
fs_mgr citește astfel `/etc/recovery.fstab`, nu `/vendor/etc/fstab.qcom`.
`/etc` este un link către `/system/etc`.

`leo-storage.fstab` păstrează intrările interne existente și adaugă exclusiv
controlerul SD `7864900.sdhci`. `install-sd-storage.py` verifică telefonul, modul
intern și conținutul original, salvează backup/hash și instalează fișierul în
`/system/etc/recovery.fstab`. Este necesară o repornire.

```text
python android81/install-sd-storage.py
```

Flagul `encryptable=userdata` oferă capabilitatea adoptable de care Android 8.1
are nevoie pentru a marca volumul public secundar VISIBLE. **Cardul rămâne
public FAT32**; instalatorul nu execută adoptare, criptare sau formatare.
Memoria internă emulată rămâne stocarea principală. Restaurare configurație:
`python android81/install-sd-storage.py --restore`, apoi reboot; nu restabilește
conținutul cardului.

Înaintea primei montări s-au salvat local MBR-ul și primii 16 MiB ai partiției
(metadatele FAT ale acestui card). Acesta este un backup de metadate, **nu al
fișierelor**. Verificarea `fsck_msdos -n` a găsit un indicator FSInfo de cluster
liber neconcordant. Verificarea standard vold (`-p -f`) l-a corectat la montare.
Nu s-a formatat sau repartitionat cardul. Back-up-urile și numele fișierelor
personale rămân excluse din Git.

## Acces din Leo

Fișiere Leo descoperă cardul prin `getExternalFilesDirs` și derivă rădăcina
volumului numai din structura verificată `Android/data/<package>/files`.
Verificările de cale canonică și granturile URI numai pentru citire rămân active.
Aplicația de sistem solicită explicit privilegiul `WRITE_MEDIA_STORAGE`, inclus
în allowlist, pentru operațiile de fișiere pe SD secundar în Oreo. Nu se acordă
acest privilegiu altor aplicații; APK-ul obișnuit fără instalare privilegiată nu
oferă aceleași drepturi.

Confirmat după reboot: vold raportează volumul PUBLIC, MOUNTED, VISIBLE, iar
Fișiere Leo îl listează. Din interfața aplicației s-au creat un folder și un
fișier UTF-8 de probă; octeții au fost verificați după `sync`. Citirea aceluiași
fișier prin providerul Leo a returnat textul așteptat. Datele sintetice au fost
îndepărtate la final, fără modificarea fișierelor existente.
MTP, scoaterea/reintroducerea la cald și alte carduri
sau sisteme de fișiere necesită probe distincte.
