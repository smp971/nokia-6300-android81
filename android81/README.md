# Android 8.1 pentru Nokia Leo: pregatire

**Stare actuala (16 octombrie 2026):** init, ADB, meniul nativ si stiva grafica
SurfaceFlinger/SwiftShader sunt deja verificate pe telefon si integrate in recovery.
Detaliile actuale si hash-urile sunt in `STATUS.md`; descrierea initiala de mai jos
este pastrata ca istoric. Frameworkul complet si Launcher3Go sunt acum in compilare,
inca fara confirmare de boot. Configuratia Go foloseste AOSP, fara serviciile Google.

Pentru proba frameworkului se pregatesc fisiere ext4 separate pe microSD:
`leo-android81/system.raw.img` (800MiB, montat RO) si `data.raw.img` (512MiB, RW).
Cardul si partitiile KaiOS nu se formateaza. Fisierul de date este dedicat acestei
probe; scriptul initial de transfer refuza sa suprascrie un fisier de date existent.
Succesiune: `build-framework-diagnostic.sh`, `package-framework-runtime.sh`,
`stage-framework-runtime.py`, `build-init-diagnostic-image.py --framework`.
Instalarea foloseste `flash-init-from-ram.py --framework --expected-current-md5 HASH`
numai dupa verificarea pregatirii fisierelor pe card. `inspect-framework-boot.py`
salveaza procesele, serviciile, logcat si starea launcherului prin ADB.

Obiectiv initial: Android init + ADB, apoi SurfaceFlinger si control din taste.
Acest director contine acum o tinta nativa de diagnostic AOSP 8.1, inca nevalidata
prin compilare sau pornire. Nu contine un ROM instalabil.

## Implementarea initiala

- Baza aleasa: AOSP `android-8.1.0_r81`, manifest initializat in
  `/root/android81-leo/src` din distributia WSL `kaios`.
- Produs: `aosp_leo_diag-eng`, bazat pe `embedded.mk` (servicii native, fara ART/Java).
- Produs ulterior: `aosp_leo_go-eng`, cu framework si `go_defaults_512.mk` din AOSP 8.1.
  Este o configuratie pentru memorie redusa, fara serviciile Google, inca netestata.
- Boot stock cu kernel precompilat, Binder 32-bit si SELinux temporar permisiv pentru diagnostic.
- `/system` este montat read-only; `/data` este tmpfs. Nu se monteaza/formateaza userdata,
  cache sau partitiile modem/NV in configuratia noua.
- USB: configfs + FunctionFS, controler `msm_hsusb`, VID/PID Android de diagnostic.
- Cheia publica ADB a hostului este copiata in ramdisk din directorul local ignorat de Git.
  Nu se copiaza cheia privata. Autentificarea ramane activa.
- Arborele de dependinte ELF al graficii: 62 biblioteci, fara DT_NEEDED lipsa in dump.
  Compatibilitatea simbolurilor si a linker namespaces cu Oreo NU este inca verificata.

Scripturi: `init-sources.sh`, `sync-sources.sh`, `stage-device.sh`, `environment.sh`,
`check-environment.sh`, `build-diag.sh`. Se ruleaza prin `wsl -d kaios -- bash CALE_LINUX_SCRIPT`.
Logurile si manifestele sunt in `/root/android81-leo/logs`. Nu exista automatizare de flash.
`finish-first-build.sh` continua sincronizarea deja pornita cu o incercare de build nativ,
actualizeaza first-build.state si copiaza imaginile in output/ numai dupa succes si
verificarea dimensiunilor. Nu se lanseaza o a doua instanta in paralel.

`prepare_android81.py` pastreaza acum BoardConfig existent, pentru a nu pierde editarile portului.

## Confirmat pe telefon, 15 octombrie 2026

- Nokia 6300 4G, firmware 20.00.17.01, acces ADB root.
- Kernel 4.9.240-perf-g4f9e270fd77f, ARMv7.
- MemTotal 402488 KiB, aproximativ 393 MiB disponibili kernelului dupa rezervarile hardware.
- Panou ST7789VX2 Yihua 24 QVGA SPI, confirmat in cmdline si sysfs.
- Framebuffer 16 biti, dimensiune virtuala 240x640 (nu rezolutie fizica 240x640).
- Tastatura matrix_keypad pe event1 la momentul colectarii; numerotarea event poate varia.
- Marimi reale: boot/recovery 33554432, system 838860800, cache 209715200,
  userdata 2566897152 octeti. XML-ul dump-ului indica 0 pentru userdata deoarece este variabila;
  aceasta valoare nu este o marime utilizabila pentru build.

Nu se foloseste configuratia GC9305 din portul Linux pentru acest aparat fara adaptare.
Prima ipoteza Android pastreaza kernelul stock cu DTB-urile sale atasate.

## Continut

- device/nokia/leo/BoardConfig.mk: schita experimentala cu geometria boot si partitiile din dump.
- device/nokia/leo/prebuilt/kernel: payload stock extras din boot.bin, fara modificare.
- device/nokia/leo/reference-stock/: init, fstab, taste si configuratie kernel pentru analiza.
  Aceste fisiere de Android M/KaiOS nu sunt copiate automat ca init Android 8.1.
- port-inputs.json: masuratori si hash-uri ale intrarilor publice.
- live-device.json: inventar ADB local; poate include identificatori in cmdline, nu se publica automat.
- backup-live/: copii locale ale boot/recovery citite din aparatul real, daca verificarea s-a incheiat.

## Etapele urmatoare

1. Verificarea copiilor boot/recovery si a caii de restaurare pentru aparatul real. Aceste doua
   partitii nu reprezinta un backup complet al datelor sau calibrarilor.
2. Mediu Linux pe filesystem-ul WSL, cu Java 8 si repo. Versiunile uneltelor pentru Oreo
   trebuie fixate; Ubuntu 22.04 poate necesita un mediu de compilare mai vechi izolat.
3. Alegerea ramurii LineageOS 15.1/AOSP 8.1 si fixarea manifestului la revizii exacte.
4. Un produs de diagnostic: init/ADB pentru dispozitiv, cu USB configfs, fstab, ueventd si SELinux
   adaptate Android 8.1. Nu se transplanteaza init.rc KaiOS integral.
5. Validarea ABI Binder 32-bit fata de userspace si inventarul dependintelor ELF pentru
   gralloc/hwcomposer/GLES. De-abia apoi activarea SurfaceFlinger/framework.
6. Interfata la 240x320 cu D-pad, softkeys si introducere text; ulterior Wi-Fi, sunet si telefonie.

Blocante curente: sincronizarea integrala a surselor si prima compilare; apoi validarea
init/USB si integrarea HAL/SELinux/VINTF. Produsul si init-ul initial exista, dar sunt netestate pe telefon.
BoardConfig foloseste date verificate fata de dump, nu constituie dovada unei compilari reusite.
Nu exista comenzi de flash in aceasta pregatire.

Reproducerea pregatirii: `python analysis/prepare_android81.py` din radacina proiectului.
Inventarul live foloseste `--adb CALE_ADB --serial SERIAL_EXPLICIT` si comenzi de citire.
Optiunea `--backup-boot` citeste boot/recovery prin adb pull, verifica marimea si MD5 fata de
dispozitiv si calculeaza SHA-256 local; refuza suprascrierea copiilor existente.
