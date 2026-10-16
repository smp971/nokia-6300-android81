# Etapa initiala Android 8.1 — actualizat 16 octombrie 2026

REMEDIERE KEYSTORE PROBATA: lipsa HAL android.hardware.keymaster@3.0-impl
ducea la iesirea Keystore si NullPointerException IKeystoreService.exist in
ConnectivityService la USER_STARTED, apoi restart system_server. Compilate cu
succes implementarea si serviciul Keymaster3 (keymaster-build.log, exit0).
test-keymaster-overlay.py a adaugat biblioteca prin bind mount volatil al
/system/vendor/lib/hw, fara modificarea imaginii SD/recovery. SHA256 HAL:
73d3ad778590737bf7a10cfaefaf9bb66787bcf78c741f748eb3841945e13d99.
Dupa proba: init.svc.keystore=running, service android.security.keystore found;
jurnalul confirma folosirea SoftKeymasterDevice software. Persistenta la reboot
necesita regenerarea pachetului system cu noile PRODUCT_PACKAGES deja adaugate.
Launcher ramane nevalidat: pm enable component a reusit, start explicit inca
returneaza Error type3. Nu este declarata finalizata stabilizarea intregii interfete.

CONTROL FARA TOUCH: ADB 695d conectat, uid=0 confirmat. Prin serviciul
lock_settings: `set-disabled true` reusit si `get-disabled` returneaza true.
Setari numai in data imaginii de test: screen_off_timeout=1800000;
window_animation_scale/transition_animation_scale/animator_duration_scale=0.
Launcher3Go este instalat, resolverul vede .Launcher, dar HOME query returneaza
numai Settings FallbackHome; start explicit returneaza Error type3 (probabil
component dezactivat implicit). cmd package enable nu este suportat in aceasta
versiune; pm enable prin framework-command.py a coincis cu restart system_server,
deci NU se considera activat/verificat. System_server continua sa reporneasca.
Corectie diagnostic RAM: /proc/meminfo arata MemAvailable ~269524kB si Cached
~258876kB in momentul probei; `free` BusyBox interpreteaza incomplet cache-ul.
MemFree redus singur NU dovedeste epuizarea RAM si nu explica sigur restarturile.

PRIMA PROBA FRAMEWORK INSTALATA: recovery MD5 c9dc5f8b611368906b7b3bce5448272f
scris si verificat, reboot direct recovery reusit. SD system/data SHA256 confirmate
prin framework-stage-result.json; boot intern pastrat (framework-install.json).
Stage android-framework-boot-action, LEO_FRAMEWORK_IMAGES_MOUNTED.
Android a ajuns la sys.boot_completed=1, system_server, SystemUI si input
matrix_keypad recunoscut. NU este un boot stabil: SurfaceFlinger a avut doua
SIGSEGV initiale in SwiftShader FrameBuffer::copyLocked, apoi a ramas PID560;
system_server a repornit repetat (706 -> 1856 -> 2289). Launcher nu este inca
validat/afisat. sys.boot_completed poate ramane 1 dupa restartul system_server,
deci aceasta proprietate singura NU dovedeste stabilitatea sesiunii curente.
Capturi: bootanimation Android si dialog Process system isn't responding.
Memorie fizica 381MiB, fara swap initial; zram0 era neconfigurat (disksize0).
Activat TEMPORAR zram 256MiB, major253 minor0, /dev/leo-zram privat, mkswap/swapon
numai pe dispozitivul RAM. Nu este persistent la reboot. Swap folosit ~63MiB.
Setari numai in data de test: device_provisioned=1, user_setup_complete=1.
Input via framework-command.py foloseste BOOTCLASSPATH din init.environ.rc;
tap Wait pe dialog ANR a fost injectat. bootanim oprit temporar via ctl.stop.
Captura PNG screencap poate astepta mult la broadcast media scanner; prefera
screencap RAW (240,320,format1,dataspace0, 16-byte header), apoi conversie host.
Rapoarte output/framework-boot-*; sunt instantanee si pot preceda ultimele restarturi.
Nu a fost restaurat recovery original si nu a fost inlocuit system/userdata intern.

FRAMEWORK COMPILAT CU SUCCES: systemimage aosp_leo_go-eng, make exit 0,
mesaj `build completed successfully (09:20)`. Corectia host mmap este validata
de trecerea dexpreopt si generarea system.img. Pachetul SD este generat:
system.raw.img 838860800 bytes SHA256
3e1586dfc160a616da9bea37072c3ab216b6c62c49bf86464515a90ad0a1fd7e;
data.raw.img 536870912 bytes SHA256
7e77368606c34a74e71aa98bd750379c17882b3ea68e250b1dbf413ff8718ffc.
Recovery framework offline verificat: MD5 c9dc5f8b611368906b7b3bce5448272f,
SHA256 ddb495b5c2be3b207afb53289470d210b9a5c8f8598ddf0bd439aa4ecc153edd.
Transfer SD in curs, sesiune 78651. Nu exista inca dovada de pornire framework.
flash-init-from-ram.py foloseste helperul bootstrap specific framework pentru
a verifica hash-ul corect inainte de scriere.

Reluare framework: prima trecere a ajuns la dexpreopt, dar dex2oatd a oprit
compilarea cu `ANDROID_DATA not set and /data does not exist` pe host.
Log pastrat: /root/android81-leo/logs/framework-build-before-android-data.log.
build-framework-diagnostic.sh exporta acum ANDROID_DATA catre directorul dedicat
/root/android81-leo/host-android-data si creeaza dalvik-cache. Reluare activa
in sesiunea 45633; ckati reciteste dependentele, cu asteptari I/O in WSL.
Corectia nu este inca validata prin succesul dexpreopt/systemimage.
Output local live: http://127.0.0.1:18763/ (live-build-output.py, PID host 9492).
Actualizare: ANDROID_DATA a rezolvat prima eroare. Dexpreopt esueaza apoi
la mmap pentru boot-framework.art: adresa ceruta 0x7039b000, adresa returnata
de kernel in zona inalta si aliniata la 2MiB. setarch -R si PR_SET_THP_DISABLE
nu au rezolvat proba. Niciun sysctl global nu a fost modificat.
patch-art-host-mmap.py aplica in mem_map.cc MAP_FIXED_NOREPLACE pentru adrese
cerute explicit, numai pe Linux host (!ANDROID_OS), fara suprascrieri de mapari.
Originalul este pastrat ca mem_map.cc.leo-host-original in arborele WSL.
Build activ sesiunea 58807 (inlocuieste 45633/59471, terminate cu eroare).
Compilarea mem_map.cc a trecut; link/dependente ART in curs, 985 pasi planificati.
Succesul dexpreopt si systemimage nu este inca confirmat.

FRAMEWORK IN PREGATIRE: build aosp_leo_go-eng systemimage pornit, log WSL
/root/android81-leo/logs/framework-build.log. Launcher3Go si stiva SwiftShader/HWC
sunt incluse in produs. Tastatura noua: OK DPAD_CENTER, soft dreapta BACK,
soft stanga MENU, rosu HOME. Nu este inca probata cu framework pe telefon.
Card verificat: mmcblk1 type SD, p1 179:65, aproximativ 26.4GiB liberi.
Plan implementat in surse: system.raw.img 800MiB RO + data.raw.img 512MiB RW,
fisiere dedicate in leo-android81 pe SD; fara format sau inlocuire system/userdata.
framework-mount.c valideaza tip SD, major/minor, marker proprietate folder si
fisiere regulate cu dimensiuni limitate; bootstrap pastreaza early ADB in namespace
separat si monteaza imaginile inainte de init. In caz de eroare tine ADB activ.
build-init-diagnostic-image.py --framework preia rootdir Go din tar, exclude
flash_recovery/update_verifier si orice fstab intern; USB ramane la early ADB.
Scripturi noi: build-framework-diagnostic.sh, package-framework-runtime.sh,
stage-framework-runtime.py. NU reprezinta inca dovada de boot framework.
Build host: Jack cu heap 6GiB prin ANDROID_JACK_VM_ARGS (variabila launcherului
singura era suprascrisa de jack_server_setup.mk). Concurenta limitata la 2 in
/root/.jack-server/config.properties, backup in /root/android81-leo/
jack-config-before-framework.properties; 4 compilari concurente saturau si 6GiB.
configure-jack-framework.py pastreaza aceasta configuratie la reluare.
Helperul leo_framework_exec a fost testat pe telefon: vede /dev/__properties__
din namespace-ul PID1. Utilitarele mount/exec/bootstrap sunt compilate; Settings.apk
este generat. Frameworkul nu a fost inca instalat sau pornit.

INTEGRARE GRAFICA INSTALATA SI TESTATA: recovery-android81-graphics.img,
MD5 a54b53da3c67326f6b7de3f55c9ef1a3, SHA256
6549ef378ca5bd01bc1231dd2436151243707b0ec3c9bb302c56bcd91e93286f.
Payload pana la 22644736 bytes intr-o partitie de 33554432 bytes; kernel/cmdline
originale pastrate. Runtime extras automat in /tmp/leo-graphics. init porneste
logd, Binder/HwBinder, composer, SurfaceFlinger si scena fara comenzi manuale.
Confirmare utilizator dupa reboot: benzile si patratul alb se vad si se misca.
Dump autostart: suprafata activa, SwiftShader, 14 cadre la momentul capturii.
Oprirea controlata a leo_scene a declansat automat oprirea leo_sf/leo_composer
si revenirea la leo_ui; procesul meniului confirmat. Testul are alarma 150s,
dar expirarea naturala nu a fost asteptata in aceasta verificare.
Readback dupa reboot confirma recovery nou si boot original MD5
7cc8b4d7ec4373530a29ba2414851e27. Originalul recovery nu a fost restaurat.
Imaginea Desktop recovery-adbshell.img nu a fost modificata.
Dovezi: output/graphics-install.json, graphics-autostart-surfaceflinger.txt,
graphics-autostart-dmesg.txt, graphics-autostart-result.json.
Framework Java/launcher/telefonie nu sunt incluse; urmatoarea etapa separata.
Kernelul a emis WARNING Unbalanced enable IRQ 227 in calea display SPI;
nu a impiedicat testul, cauza si impactul pe termen lung raman de investigat.
Sectiunea RAM de mai jos descrie proba anterioara integrarii.

STIVA GRAFICA TESTATA PE TELEFON: SurfaceFlinger Android 8.1, servicemanager,
hwservicemanager, gralloc, EGL 1.4 si GLES 2.0 Google SwiftShader functionale.
HWC propriu (hwcomposer-leo.cpp), prin adaptorul HIDL composer 2.1, converteste
RGBA8888 in RGB565 si prezinta pe framebufferul fizic 240x320, pitch512.
Utilizatorul confirma trei benzi colorate si patratul alb animat prin SurfaceFlinger.
Dump final: 151 cadre prezentate. Vsync software configurat 29Hz nu reprezinta FPS
masurat; performanta ramane de optimizat. Nu exista accelerare GPU verificata.
Proba allocator/EGL trece cu readback RGBA 64,128,191,255, fara eroare GL.
Surse: graphics-probe.cpp, graphics-exec.c, hwcomposer-leo.cpp, surface-test.cpp
in device/nokia/leo. Build: build-graphics-diagnostic.sh. Pachetul include HWC
sub numele hwcomposer.default.so pentru incarcare prin fallback.
Proba a rulat integral din /tmp/leo-graphics, fara rescriere de partitii.
Recovery instalat ramane versiunea UI e041416f9ff8bb4f7b36b692a82c53d2.
La final SurfaceFlinger si composer au fost oprite, meniul leo_ui repornit.
Managerii Binder/logd raman in RAM; reboot elimina runtime-ul experimental.
Nu exista inca framework Java, launcher Android sau telefonie Android.
Dovezi: output/graphics-probe.log, surfaceflinger-dump.txt (cu suprafata activa),
surfaceflinger-final.txt, graphics-logcat.txt, graphics-stack-result.json.
Pachet reproductibil: output/graphics-runtime.tar.gz si graphics-runtime.json.
stage-graphics-runtime.py refuza suprascrierea unui runtime cu servicii active;
pentru o noua incarcare se porneste o sesiune recovery proaspata.
Pasul urmator este integrarea stivei in imagine, apoi pornirea frameworkului.
Sectiunile de mai jos pastreaza istoricul etapelor anterioare.

PRIMA UI NATIVA TESTATA SI INTEGRATA: utilizator confirma meniul vizibil si
navigare cu taste. Test live framebuffer: 240x320, virtual 240x640, RGB565, pitch512,
smem327680; FBIOPAN_DISPLAY=0 si MSMFB_DISPLAY_COMMIT=0. Evenimente matrix_keypad
103/108/352/158 si alte taste confirmate in output/leo-ui.log. UI are pagini DISPLAY,
KEYPAD, DEVICE INFO; sus/jos selecteaza, OK deschide, soft dreapta revine, rosu 107
inchide si restaureaza bufferul anterior. Este meniu de diagnostic nativ, nu launcher
Android/framework. Sursa device/nokia/leo/diag-ui.c; build-ui-diagnostic.sh, ELF static
output/leo-diag-ui. Testul initial a rulat doar in RAM si a fost confirmat de utilizator.
Integrare instalata si verificata: recovery-android81-ui.img, MD5
e041416f9ff8bb4f7b36b692a82c53d2; SHA256
9671e0c8c1f237416682db027bd8a978a0b8ffad7c2e84ad601efb3803d553d9.
Marker /leo-autostart-android porneste automat init dupa early ADB; /sbin/recovery
pastreaza skip pentru DT vendor. prop.default adaugat din proprietatile probei.
Service init leo_ui root/graphics/input, seclabel su, disabled+oneshot, start on boot.
Dupa reboot direct recovery: android-init-boot-action si proces leo-diag-ui active,
confirmate fara gate manual. Nicio restaurare originala. Boot/system/userdata reale
nu au fost rescrise pentru UI; cache folosit pentru jurnalele de diagnostic. Copia
Desktop recovery-adbshell.img ramane neschimbata. Log framebuffer DD nu este suportat
(ENODEV), deci nu exista screenshot valid generat; confirmarea vizuala vine de la
utilizator. Urmatorul pas: SurfaceFlinger/EGL/gralloc si framework Android Go; aceste
servicii NU sunt inca pornite. Vezi output/ui-install.json, ui-autostart-processes.txt.

SUCCES INIT ANDROID 8.1 (PROBA MINIMALA, FARA FRAMEWORK/GUI): V5 MD5
7e51a75b9976d4e2d7be3c74da8efcf0. Bootstrap + ADB AOSP 8.1 root functionale;
gate /tmp/leo-run-android trimis. Log confirma First stage mount skipped (recovery mode),
Loaded policy from /sepolicy, init second stage started!; marker /tmp/leo-stage este
android-init-boot-action. PID1 init.android, ueventd activ. Verificat dupa ~50s uptime.
Boot hash neschimbat 7cc8b4d7ec4373530a29ba2414851e27; recovery hash confirma V5.
Telefon lasat in proba Android nativa cu ADB root; originalul NU a fost restaurat.
Cauze identificate: V3 adbd static vechi din backup moare SIGSEGV (wait status 11),
nu creeaza ep2, UDC bind esueaza ENODEV. ADB AOSP 8.1 cu LEO_EARLY_ADB a rezolvat
canalul de diagnostic. V4 init AOSP cere vendor din fstab DT (status ok), asteapta
10011ms si cere panic, fiindca telefonul nu are partitie vendor separata. V5 adauga
/sbin/recovery ca marker F_OK recunoscut de IsRecoveryMode, potrivit probei bootate
din recovery si integral din ramdisk; astfel se omite montarea DT in prima etapa.
Politica AOSP O a fost acceptata de kernel; SELinux global permissive doar pentru
aceasta proba de diagnostic. Inca lipsesc prop.default si mai multe servicii; init
boot action nu inseamna Android Go complet. Nicio interfata grafica sau telefonie
Android nu a pornit. Gate-ul este manual prin ADB la fiecare boot; fara el proba
revine in KaiOS dupa timeout. Sistemul si userdata reale nu au fost montate de proba.
Cache a fost montat pentru loguri; flash numai recovery. Copia Desktop recovery-adbshell
este versiunea KaiOS recovery cu root, pastrata neschimbata.
Dovezi: output/leo-init-v5-after-gate-dmesg.txt, leo-init-v5-live-info.txt,
leo-init-v5-partition-hashes.txt, init-diagnostic-v5-install.json. Imaginea curenta:
output/recovery-init-diagnostic.img, SHA256 e1ea8ee96de3b9754b2ca6ce832cc9301609c05718c947d844ac9b80bbb1ade6.
ADB timpuriu foloseste mount namespace privat (nu vede proprietatile/init sockets
din noul /dev al PID1); pentru reboot direct helperul bootstrap --reboot-recovery
foloseste syscall restart2, nu serviciul proprietati. V5 scris verificat direct din
RAM cu flash-init-from-ram.py, validari partitie/sysfs/hash inainte de scriere.

INIT DIAGNOSTIC V3 CU LOG CACHE INSTALAT: MD5 6a4070d8747ce8e196cd263dc343ca8d
verificat; adb reboot recovery executat. Cache verificat in KaiOS: ext4, mmcblk0p32,
259:0, 409600 sectoare (200 MiB), ~158.6 MiB liberi. Bootstrap valideaza PARTNAME,
numar sectoare si major/minor citite din sysfs inainte de mount. DOAR cache este
montat persistent pentru logurile leo-init-v3-{bootstrap,adbd,kernel}.log; nu se
formateaza. stdout/stderr adbd redirectionate in cache, trace activat, errno si starea
procesului copil inregistrate, kernel ring buffer copiat. Daca gate-ul Android lipseste,
opreste copilul ADB, sync/demonteaza cache si incearca reboot normal dupa 25s asteptare
(+ pana la 20s setup USB). Asteapta revenirea KaiOS/ADB si colectarea logurilor. Nu
exista inca dovada init Android lansat. Boot/system/userdata neatinse de aceasta proba.
Originalul nu a fost restaurat. Recovery bun pe Desktop ramane pastrat.

V2 BOOTSTRAP EXECUTIE CONFIRMATA: utilizatorul a observat exact doua clipiri ale
iluminarii tastelor. Confirma acceptarea imaginii si executarea bootstrap PID1 cel
putin pana dupa mount /dev, /proc, /sys, /tmp; NU confirma executia init Android.
ADB timpuriu lipseste; problema acestei probe este in pornirea adbd/configurarea USB.
Gate-ul /tmp/leo-run-android nu a fost trimis, deci init Android nu a fost lansat.
Urmatorul pas: inspectie cache din KaiOS si pregatirea unui jurnal local al bootstrap,
recuperabil dupa reboot. Utilizator rugat sa revina normal si sa activeze ADB.
Aceasta dovada nu explica retrospectiv esecul vechii imagini Android fara bootstrap.

INIT DIAGNOSTIC V2 INSTALAT: MD5 59f191422241e2107307ee88bf94ceed verificat;
adb reboot recovery executat. Pastreaza cmdline si OS-version stock byte-for-byte.
Init diagnostic seteaza permissive doar cand exista env LEO_DIAG_HOLD_ON_FAILURE.
Bootstrap adauga doua clipiri button-backlight la brightness 40 si configurare USB
os_desc ca stock. Nu a aparut inca ADB; asteapta observatia utilizatorului despre
clipiri. Gate-ul Android NU a fost trimis. Test separat in KaiOS: adbd din backup
ramane activ 4 secunde in chroot fara proprietati/USB, cu mount+network namespace
separate; procesul de test oprit controlat. Asta nu valideaza configurarea USB.
V1 a ramas la Enabled by KaiOS, apoi utilizatorul a repornit normal si ADB root a
revenit. Boot hash verificat identic inainte de V2; system/userdata neatinse de flash.
Fisierele de sandbox sunt doar in /data/local/tmp/leo-diag. Copia root recovery buna
ramane pastrata pe Desktop si in output/recovery-debug.img, fara restaurare automata.

PROBA INIT CU EARLY ADB INSTALATA: recovery MD5 7734ef6897bcc39fe64f3e6e355ef13f
verificat dupa scriere, apoi adb reboot recovery. Nu a aparut ADB in intervalul de
observatie. Utilizator intrebat ce afiseaza telefonul; inca nu avem raspuns. NU s-a
trimis gate-ul /tmp/leo-run-android, deci init Android nu a fost lansat intentionat.
Aceasta lipsa ADB nu localizeaza singura problema: header/kernel acceptance versus
bootstrap/USB. Nu pretindem cauza identificata. Originalul nu a fost restaurat;
imaginea root recovery functionala ramane salvata in output/recovery-debug.img si
pe Desktop/good 6300 files/recovery-adbshell.img. Boot/system/userdata neatinse.
Compilare init+bootstrap reusita 1m39s. Init are hold la panic activat exclusiv prin
env LEO_DIAG_HOLD_ON_FAILURE. Kernel si campul OS version sunt stock in noua imagine;
cmdline adauga androidboot.selinux=permissive. Build: build-init-diagnostic.sh si
build-init-diagnostic-image.py; sursa bootstrap: device/nokia/leo/diag-bootstrap.c.

TEST USERSPACE ANDROID 8.1 REUSIT IN RECOVERY: shell-ul mksh Android 8.1 si
comenzile toybox id/uname/ls ruleaza in chroot /tmp/leo-android81-runtime, cu linkerul
si bibliotecile AOSP 8.1 din proba existenta. Markerii ANDROID81_NATIVE_SHELL_OK si
ANDROID81_NATIVE_TEST_DONE plus uid=0 confirmati. Prima proba fara /proc a abortat
la /proc/self/exe; dupa montarea proc read-only doar in directorul de test a reusit.
Montarea proc de test a fost demontata dupa rulare. Nu s-a executat init Android,
nu s-a schimbat recovery, nu s-au montat/scris partitii persistente. Kernelul poate
executa acest runtime nativ Android 8.1; NU demonstreaza boot complet/framework/GUI.
Runtime-ul ramane doar in /tmp (RAM) pana la reboot. Log: output/android81-userspace-test.log,
raport: output/android81-userspace-result.json. Scripturi: prepare-userspace-test.py,
test-userspace.py. Urmatorul diagnostic trebuie sa urmareasca init timpuriu/politica/USB,
fara lansarea init Android ca proces obisnuit: acesta monteaza FS si reincarca SELinux.

RECOVERY DEBUG ROOT REUSIT: imagine MD5 9f8bcb0e626f06c4bb604043329aa117 instalata
si verificata, pornita prin adb reboot recovery. Shell raspunde uid=0 gid=0; markerul
/tmp/leo-debug-init contine root-adb-v1. ADB legacy raporteaza device, dar mount-urile
si markerul confirma recovery. Kernel/init executabil nemodificate. ADB static ARM
provine din backup-ul boot functional (SHA256 a44268d77f41d332e1e4fd69bbf345761210f701936c891eee589f849c12addd).
ro.secure=0, ro.debuggable=1 in recovery. Politica vendor versiunea 30: doar bitmap-ul
permissive pentru adbd (174), shell (862) modificat, toti octetii regulilor pastrati.
SELinux global enforce=1. Citirea logului recovery si dmesg REUSITA. Boot MD5 ramane
7cc8b4d7ec4373530a29ba2414851e27; recovery confirma MD5-ul imaginii noi. Telefon lasat
in recovery modificat, fara restaurare, conform preferintei utilizatorului. Android
8.1 inca nu a pornit; acesta este un recovery KaiOS cu diagnostic root functional.
Dovezi: output/debug-install.json, recovery-debug-live.log, recovery-debug-dmesg.txt,
recovery-debug-live-info.txt, recovery-debug-partition-hashes.txt. Build reproductibil:
build-recovery-debug-policy-reader.sh (WSL), apoi build-recovery-debug.py (Windows).

PROBA V4 INSTALATA: MD5 7ad1315e9068b13ed7cb64af1669df10 verificat dupa scriere.
La cererea utilizatorului, adb -s 695d reboot recovery a fost executat dupa instalare.
REUSIT: dispozitivul 695d a revenit direct in starea recovery, fara taste. Shell-ul
ramane Permission denied (13). Citirea /sys/fs/selinux/enforce arata 1: actiunea init.rc
de trecere in permissive NU a avut efect; aceasta proba nu exclude SELinux drept cauza.
Nu avem log AVC; /tmp/recovery.log ramane inaccesibil. Telefon lasat in recovery V4;
originalul nu a fost restaurat. Boot/system/userdata pastrate. Vezi shell-v4-install.json.

PROBA SHELL V3: recovery porneste, dar exec /system/bin/sh ramane Permission denied
(13). enforce=1 confirmat prin ADB pull; adb root refuzat pentru production build;
/tmp/recovery.log inaccesibil. Nu avem inca AVC-ul exact. Telefon repornit in KaiOS,
ADB 695d momentan deconectat. V3 ramane instalat; originalul NU a fost restaurat.
V4 PREGATIT, NEINSTALAT: MD5 7ad1315e9068b13ed7cb64af1669df10. Adauga in actiunea
on init scrierea /sys/fs/selinux/enforce 0, numai in recovery, pentru diagnostic.
Politica sepolicy si proprietatile stock nu sunt modificate. Asteapta activarea ADB.
Nu exista o pornire Android 8.1 reusita. Vezi output/shell-v3-install.json.

PROBA SHELL V3 INSTALATA SI VERIFICATA: MD5 d399162d01c5a1cade37409aaa92221e.
V2 a pornit recovery si a permis citirea BusyBox si sh prin ADB, dar executia a ramas
Permission denied (13). SELinux este suspectat; nu avem inca un AVC care confirma.
V3 foloseste un fisier regular /system/bin/sh (BusyBox static), cu symlinkurile de
applet spre el. init.rc primeste on init: restorecon_recursive /system pentru a aplica
file_contexts stock (sh: shell_exec). Kernel, executabil init, sepolicy, default.prop
si toate celelalte intrari CPIO stock sunt identice, verificat prin parsare offline.
Scris direct peste V2 fara restaurare originala, conform preferintei utilizatorului.
Boot hash verificat; system/userdata neatinse. Asteapta Power + * manual.

PROBA SHELL V2 INSTALATA: imagine 32 MiB MD5 a8bf402d4ddc9849d0347ae6efc3a522
scrisa direct peste V1 si verificata prin citire de pe partitia recovery. BusyBox
este acum /system/bin/busybox; symlinkurile folosesc aceasta cale. /sbin stock 0750,
init, kernel, politica SELinux si proprietatile stock raman neschimbate. Boot MD5
verificat identic cu backup. Asteapta intrarea manuala Power + *; nu s-a trimis reboot.
Preferinta explicita utilizator: pastram recovery modificat intre probe, fara restaurari
automate ale originalului. Scriptul accepta hash-ul verificat al probei precedente.
Nu s-a scris system/boot si nu s-a formatat userdata. Vezi output/shell-install.json.

PROBA SHELL V1: recovery a pornit si ADB a aparut in starea recovery. Executarea
/system/bin/sh a esuat cu Permission denied (13). /proc/self/status confirma adbd
uid/gid 2000; SELinux enforce=1, context u:r:adbd:s0. Arhiva stock are /sbin root:root
0750, deci adbd nu poate traversa directorul catre BusyBox. V2 muta executabilul in
/system/bin/busybox fara modificarea permisiunilor stock sau a politicii SELinux;
imagine pregatita offline, INCA NEINSTALATA. Dupa adb reboot conexiunea 695d a disparut;
restaurarea copiei initiale NU este finalizata. Asteapta reactivarea ADB de utilizator.
Boot/system/userdata nu au fost rescrise. Dovezi: output/shell-v1-install.json si
output/recovery-adbd-status.txt. Imagine instalata V1 MD5 eaa14dec0f36f1807fa17387ef59bdc7.

PROBA SHELL PREGATITA: recovery-shell.img scris si verificat in recovery; asteapta
intrarea manuala Power + *. Kernelul si toate intrarile CPIO existente (inclusiv init,
sepolicy si default.prop) sunt pastrate; se adauga BusyBox static ARM si symlinkuri
system/bin pentru shell/comenzi. BusyBox a fost extras din imaginea 8110 deja prezenta
pe calculator, verificat fara PT_INTERP/PT_DYNAMIC si executat cu succes in KaiOS.
Datele de dupa payload-ul recovery vechi au fost identificate byte-for-byte ca resturi
ale aceleiasi imagini 8110; noul ramdisk ocupa o parte din acea zona, iar restul tail-ului
ramane la aceleasi pozitii. Raport: output/recovery-shell.json; scriere: output/shell-install.json.
Nu a fost trimisa comanda de reboot. Boot/system/userdata nu au fost rescrise de aceasta proba.

Restaurare finalizata dupa reconectarea ADB: recovery si boot corespund hash-urilor
copiilor salvate, b2g running in KaiOS. Fisierele temporare de restaurare/control
au fost eliminate din /data/local/tmp. Nu mai exista o restaurare in asteptare.

PROBA DE CONTROL REUSITA: recovery-control-repacked.img a pornit la Power + *.
Utilizatorul a vazut KaiOS recovery; ADB a raportat explicit starea recovery si a
permis citirea /proc/mounts. Kernelul si octetii CPIO sunt identici cu copia salvata;
gzip a fost recomprimat, ramdisk_size si ID SHA1 au fost actualizate, iar datele
suplimentare de dupa ramdisk au fost pastrate la aceleasi pozitii. Metoda aceasta
de reimpachetare este acceptata. Nu demonstreaza acceptarea ramdiskului Android 8.1.
S-a revenit in KaiOS (b2g running). ADB a disparut inainte de comanda de restaurare:
recovery-ul de control functional ramane instalat, restaurarea exacta a copiei
initiale asteapta reconectarea ADB. Vezi output/control-install.json.

PROBA MANUALA, 16 octombrie: utilizatorul a pornit recovery prin Power + * dupa
rescrierea si verificarea imaginii de test. Observatie utilizator: telefonul se
restarteaza si revine in KaiOS. Confirmat prin ADB: KaiOS2.5.4, b2g running,
boot nemodificat. Recovery salvat a fost restaurat din nou si verificat MD5
ca50095d7af89c53846b9e87bc830a6b. Aceeasi imagine esueaza la ambele metode de intrare.
Comparatia offline a antetelor confirma acelasi kernel si aceleasi adrese/pagini;
ramdiskul probei si campul versiunii OS difera. Aceste diferente NU identifica
singure cauza. Urmatoarea etapa necesita diagnosticarea init/boot timpuriu.

TEST HARDWARE, 16 octombrie: pregatit boot-ramdisk-probe.img (14831616 bytes),
cu init/ADB/shell si bibliotecile minime incluse in initramfs, fara mount-uri persistente.
A fost scrisa temporar numai partitia recovery (imagine completata la 32 MiB),
verificata prin MD5 si pornita prin adb reboot recovery. Nu a fost observat ADB Android 8.1;
telefonul a revenit la KaiOS 2.5.4. Cauza exacta nu este stabilita. Kernelul stock are
CONFIG_PSTORE dezactivat si nu expune /proc/last_kmsg, deci nu am recuperat logul tentativei.
Recovery salvat a fost restaurat si verificat. Proba de control cu recovery salvat a
expus ADB si /proc/mounts, apoi s-a revenit in KaiOS prin adb reboot. Boot si recovery
au la final MD5 identice cu backup-urile, b2g este running. Nu s-a scris system si nu
s-a formatat userdata. Raport: output/hardware-test.json. Nu exista inca o pornire
Android reusita; urmatorul pas este instrumentarea pornirii timpurii inainte de alta proba.

REZULTAT ACTUAL, 16 octombrie: prima compilare NATIVA DE DIAGNOSTIC a reusit.
Imaginile sunt in android81/output: boot.img 12009472 bytes, system.img sparse
95019308 bytes. System expandat = 838860800 bytes, exact dimensiunea partitiei system.
verify-diag-images.py a trecut: kernel identic cu payload-ul stock, adrese/pagini boot
corecte, init ELF32 si fisierele init/fstab/USB/adb_keys prezente in ramdisk.
Raport: output/verification.json. Nu s-a efectuat flash sau test de pornire pe telefon.
Acesta NU este inca produsul Android Go cu framework/interfata/apeluri.
Informatiile urmatoare descriu etapele si erorile precedente, deja depasite.

Ultima verificare 16 octombrie: build-ul nativ a ajuns la 28417/28554 actiuni (99%).
Eroare la validarea dimensiunii boot.img: BOARD_FLASH_BLOCK_SIZE era nedefinit,
provocand impartire la zero in verificarea de packaging AOSP. A fost configurata
alinierea de packaging 131072 (64 pagini de 2048), fara a o prezenta drept masuratoare
a blocului fizic eMMC. Build relansat, sesiunea 63727, regenereaza configuratia.
verify-diag-images.py este pregatit pentru verificarea kernelului, ramdiskului si
dimensiunii expandate a imaginii system dupa succesul compilarii.

Actualizare ulterioara 16 octombrie: compilarea a ajuns la 4207/32760 actiuni si s-a
oprit la conexiunea TLS a Jack. Remediere limitata la mediul de build: overlay Java
security local si optiuni curl pentru TLS legacy pe loopback, cu verificarea certificatelor
pastrata. Fisierul java.security global nu a fost modificat. jack-admin server-stat a
reusit dupa restartul serverului Jack. Build-ul a fost relansat in sesiunea 54856;
verificati first-build.state si logul WSL pentru starea curenta.
Anterior s-a creat /usr/bin/python -> /usr/bin/python2 deoarece scripturile Clang
Oreo au un shebang absolut si nu foloseau shim-ul din PATH.

16 octombrie 2026: sincronizarea integrala a surselor a reusit, manifestul fixat
este in /root/android81-leo/logs/manifest-pinned.xml. Prima incercare de build s-a
oprit la lipsa regulii pentru out/target/product/leo/kernel. A fost adaugata regula
PRODUCT_COPY_FILES pentru kernelul precompilat si build-ul nativ a fost relansat
(sesiunea 93235). Nu exista inca o imagine verificata. Informatiile de mai jos
despre sincronizarea in curs descriu etapele anterioare.

Ultima actualizare: WSL a incetat sa raspunda in timpul sincronizarii; prima compilare
NU a inceput. A fost necesar `wsl --shutdown` dupa ce repornirea distributiei singure
nu a restabilit accesul. Dupa restart: aproximativ 392 MiB memorie utilizata, 13 GiB libera.
Cauza exacta a blocarii nu este demonstrata; vmmemWSL ajunsese la circa 14.9 GiB working set.
Procesul a fost relansat cu run-first-build.sh (sesiunea 11999), repo -j1, Git pack.threads=1,
pack.windowMemory=128m si make -j2. first-build.state indica faza curenta. Procesele vechi
mentionate mai jos au fost intrerupte de restart si nu trebuie tratate ca active.

Finalizat:
- Instalarea uneltelor in WSL kaios: OpenJDK 8, Python 2.7, dependinte native,
  utilitarul repo (launcher 2.69, Python 3.10). Verificarea mediului a trecut.
- Initializarea AOSP android-8.1.0_r81 in /root/android81-leo/src.
- Sincronizarea initiala reusita pentru build/make, build/soong, build/blueprint,
  system/core, system/sepolicy.
- Produs aosp_leo_diag-eng si configuratiile initiale init, fstab, USB FunctionFS,
  permisiuni pentru input/framebuffer, kernel stock, autentificare cu cheia publica ADB locala.
- Analiza statica a 62 biblioteci grafice/dependinte din dump: fara DT_NEEDED nerezolvate.

Actualizare: dupa evaluarea donatorului KaiOS 3.1, utilizatorul a ales Android 8.1
cu setari Go. Sincronizarea AOSP a fost reluata (sesiunea 88846).
Procesul finish-first-build.sh (sesiunea 35629) asteapta succesul sincronizarii,
apoi incearca automat compilarea bootimage/systemimage pentru aosp_leo_diag-eng.
Starea actualizata a procesului este in first-build.state; logurile sunt in WSL.

Configuratia aosp_leo_go-eng a fost adaugata separat folosind go_defaults_512.mk
din AOSP 8.1. Aceasta este tinta cu framework, pentru etapa de dupa diagnosticul nativ;
nu este inca validata prin compilare sau pornire. Nu include servicii Google.

Starea anterioara:
- Sincronizarea completa AOSP a rulat in sesiunea 95513, script sync-sources.sh.
  Log in WSL: /root/android81-leo/logs/repo-sync.log.
  Aceasta sesiune veche a fost oprita intentionat la schimbarea directiei spre KaiOS.

Neefectuat:
- Prima compilare bootimage/systemimage si verificarile imaginilor.
- Testarea init/USB, integrarea graficii, framework Java, interfata si telefonie.
- Orice operatie de scriere sau repornire pe telefon in aceasta etapa.

Dupa incheierea cu succes a sincronizarii, sync-sources.sh salveaza manifest-pinned.xml.
Procesul de continuare executa stage-device.sh si build-diag.sh. Nu se porneste un
al doilea repo sync sau build concomitent cu procesele existente. Compilarea se opreste
la prima eroare; nu efectueaza reparatii sau flash automat.
