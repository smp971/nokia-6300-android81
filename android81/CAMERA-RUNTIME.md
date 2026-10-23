# Cameră Leo · BF20A1 în Android 8.1

Pe 23 octombrie 2026, portarea a trecut de la enumerare privată la serviciul
Android: **o cameră HAL1, vizor în Cameră Leo și JPEG salvat în Pictures/Leo**.
Prima imagine verificată se decodează la **480 × 640**, cu orientarea aplicată
pixelilor. Aceasta este rezoluția verificată, nu o promisiune despre alte moduri.
Nu există înregistrare video și nu a fost verificat blițul. Deschiderea/închiderea
poate încă dura câteva secunde în driver, chiar dacă tastele rămân responsive.
Pornirea automată după repornire și un al doilea JPEG au fost verificate.

## Ce a lipsit

1. Inițializarea originală a senzorului, prin `mm-qcamera-daemon` și pluginul
   `libmmcamera_bf20a1.so`. Driverul înregistrează senzorul și finalizează handshake-ul.
2. Providerul AOSP HIDL 2.4 și implementările de dispozitiv, absente din imagine.
3. Adaptorul HAL1 pentru callback-ul de memorie: driverul Qualcomm trimite un
   `QCameraStreamMemory*` în argumentul user al cererii de memorie. Oreo așteaptă
   cookie-ul transmis în `set_callbacks`. Fără adaptare, providerul se închidea
   în `CameraDevice::sGetMemory`. `camera-hal-bridge.c` păstrează acel cookie și îl
   transmite allocatorului Android, fără a schimba structura memoriei.
4. Bibliotecile C2D pentru legarea fluxului de preview și pluginurile QOMX/JPEG
   pentru codificare, încărcate dinamic și absente din auditul DT_NEEDED inițial.

Adaptorul acceptă un singur dispozitiv deschis și refuză deschiderea concurentă.
Un HAL fictiv verifică de două ori deschiderea, refuzul concurenței, corectarea
cookie-ului, închiderea și redeschiderea: **13 verificări trecute pe telefon**,
fără acces la cameră sau la noduri hardware.

## Izolarea interfeței

Ieșirea din prima versiune a vizorului a provocat GL_OUT_OF_MEMORY în HWUI și a
repornit procesul Leo. Camera are acum procesul separat `:camera`, interfața ei
folosește randare software, iar SurfaceView primește în continuare fluxul camerei.
Deschiderea, configurarea, captura și închiderea HAL rulează pe HandlerThread;
tastele și revenirea nu așteaptă IPC-ul lent al driverului. Timeout-urile de
15 secunde anulează starea UI; nu pot întrerupe un ioctl blocat în kernel.
Rezultatele întârziate sunt invalidate la închiderea suprafeței sau a activității.
Eticheta centrală a barei este actualizată pe loc; nu se adaugă alte bare și nu
se recreează SurfaceView. Procesul camerei nu reconfigurează serviciul de taste
care aparține launcherului.

Proba finală a trecut: vizor, JPEG, închiderea camerei și revenire în Home,
**fără schimbarea PID-urilor launcherului, providerului sau cameraserver**.
Cele 18 verificări PocketSuite au trecut după introducerea procesului separat;
verificările camerei din acea suită inspectează manifestul, fără deschiderea senzorului.
Validarea UI finală este separată și folosește camera reală.

## Instalare și limite

Runtime-ul este separat în `/vendor/leo-camera`. Providerul și daemonul primesc
propriul mount namespace, un director privat de biblioteci hardware și socket-uri
în `/data/leo-camera/sockets`. Copiile stock rămân în afara căilor globale ale
bibliotecilor Android. Nu sunt înlocuite libbinder, libutils, driverul grafic sau
serviciile audio. Nu sunt rescrise boot, recovery ori partițiile modemului.

Serviciile init `leo-camera-daemon` și `leo-camera-provider` sunt armate prin
`persist.leo.camera.enabled=1`, după `sys.boot_completed`. Folosesc eticheta explicită
`u:r:init:s0`, ca celelalte servicii de bring-up Leo; lipsa ei a împiedicat prima
probă de pornire automată. Sunt oneshot, fără buclă automată de repornire la eroare.

**Aceasta este o integrare de dezvoltare:** procesele rulează ca root în politica
permisivă existentă. Izolarea montărilor previne amestecarea bibliotecilor, dar nu
constituie o graniță de securitate pentru cod ostil. Separarea permisiunilor,
profilul SELinux de producție, consumul în utilizare îndelungată și concurența cu
alte aplicații rămân de lucrat. Providerul este HAL1, fără funcțiile Camera2.

Instalatorul verifică ținta 695d, kernelul, lipsa unui apel, hash-urile arhivei și
ale tuturor celor 423 de fișiere, apoi readuce `/system` în mod read-only.
Verificarea implicită citește doar starea serviciilor. Proba cu `--capture`
deschide camera, salvează **o fotografie reală**, apoi revine la Home și verifică
păstrarea PID-urilor și eliberarea camerei. Nu se rulează în timpul unui apel.

```powershell
python -X utf8 android81/verify-camera-runtime.py
python -X utf8 android81/verify-camera-runtime.py --capture
```

Închide întâi Cameră Leo. Configurația poate fi dezactivată fără ștergerea fotografiilor:

```powershell
python -X utf8 android81/install-camera-runtime.py --disable
```

## Recompilare

Necesită arborele AOSP 8.1 local și dump-ul privat stock. Fișierele proprietare,
cheile, fotografiile, arhivele instalabile și jurnalele brute nu sunt în GitHub.

```bash
bash android81/build-camera-provider.sh
bash android81/build-camera-hal-bridge.sh
bash android81/build-camera-runtime-exec.sh
bash android81/build-camera-bridge-checks.sh
bash android81/build-visual-shell.sh
```

Apoi, din Windows, generează auditul dependențelor din dump-ul local și instalează:

```powershell
python -X utf8 android81/inspect-camera-port.py
python -X utf8 android81/install-camera-runtime.py --install
python -X utf8 android81/install-native-phone.py --no-open
```

`install-camera-runtime.py` fără argument pregătește doar arhiva locală;
`--install` o instalează explicit și necesită repornire pentru citirea configurației
init. Refuză suprascrierea unui runtime deja existent; actualizările ulterioare
necesită o procedură separată cu verificarea versiunii instalate.

`prepare-camera-runtime.py` este pentru probele în directorul privat din `/data/local/tmp`,
fără instalare permanentă. După montarea namespace-ului, launcherul folosește
`LEO_CAMERA_ROOT`; pentru acel director diagnosticul trebuie să îl seteze explicit.
Argumentul de timp 15–900 secunde oprește probele; 0 înseamnă serviciu continuu,
folosit de init. O comandă blocată în kernel poate depăși SIGKILL, deci nu se
suprapun probe dacă rămâne un proces anterior.

[Investigația și etapele anterioare](CAMERA-PORT-AUDIT.md).
