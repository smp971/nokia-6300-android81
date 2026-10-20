# Stabilitate și telefonie · 20 octombrie 2026

## Rezultate confirmate

- Două apeluri reale inițiate de pe SIM 1 către numărul de test autorizat, păstrat numai local. Utilizatorul a confirmat sunet în ambele sensuri în ambele probe și închiderea din soft dreapta. Vechea probă de apel inițiat fără audio este depășită de aceste rezultate; SIM 2, DTMF și toate rutele audio rămân separate.
- La început: boot complet, uptime aproximativ 7012 s, audioserver `running`, runtime audio `active`, ambele stări de apel idle. După prima probă: uptime 7218 s și ambele SIM-uri idle, fără reboot.
- `dumpsys activity lastanr` a raportat `<no ANR has occurred since boot>` înainte și după probe. Bufferul crash inițial conținea o eroare DeskClock de la pornirea acestui boot, nu un crash nou al Leo. Proba primei corecții DeskClock a adăugat o a doua eroare, descrisă mai jos.
- Eșantionul scurt CPU/RAM/USB: două citiri, trei secunde între citiri, zero eșecuri ADB și aproximativ 96,5% CPU idle. Nu este un benchmark al interfeței sau o măsurare de autonomie; USB era conectat.

## USB: observații și limite

ADB s-a întrerupt la ambele apeluri, deși telefonul a continuat conversația și a răspuns la taste. Windows a continuat să enumere `Leo init diagnostic`, cu stare OK și ProblemCode 0. Repornirea serverului ADB a expus temporar transportul ca offline, fără recuperarea shell-ului. Reconectarea fizică a cablului a restabilit ADB.

Pentru a doua probă, `usb-call-observe.sh` a rulat într-o sesiune independentă, cu un timeout extern. A păstrat configurația inițială `power/control=auto`, a selectat temporar `on` și a înregistrat 60 de eșantioane locale:

- runtime USB `active` pe toate eșantioanele;
- `runtime_suspended_time=1200` ms neschimbat;
- procesul diagnostic adbd a păstrat același PID;
- UDC `configured`, apoi `not attached` și din nou `configured` la reconectarea fizică;
- restaurarea la `auto` a fost consemnată în jurnal și recitită ulterior.

Setarea `on` nu a prevenit întreruperea ADB. **Nu este păstrată ca optimizare și nu se declară USB reparat.** Cauza exactă în transportul ADB/FunctionFS, driverul gadget sau partea host rămâne nedeterminată. Nu au fost schimbate boot, recovery, descriptorii USB, stiva audio sau driverul DSP.

Monitorul are doar două moduri: `observe` și `hold-awake`; durata acceptată este 4–120 s, cu pauze de două secunde. Comenzile și citirile adaugă timp; proba completă a durat aproximativ 133 s. Lansarea folosită impune și `timeout -s TERM 135`, cu restaurare la ieșirea normală/TERM. Un blocaj de kernel nu poate fi reparat sau garantat de un script userspace.

Jurnalele locale sunt în `/data/leo-stability/usb-call-trial.log`. Conțin uptime, politică de alimentare, runtime, contor de suspendare, stare UDC și PID adbd; fără numere de telefon sau conținut de conversație. Jurnalele brute ale apelurilor și numărul de probă rămân în `output/`, ignorat de Git.

## DeskClock stock: eroarea de fundal

Crash-ul de la boot era `IllegalStateException: Not allowed to start service`, în `AlarmService.stopAlarm`, apelat de `AlarmInitReceiver` la reconcilierea alarmelor. Cererea STOP folosea `startService`, supus restricțiilor de fundal din Oreo.

Prima variantă cu `startForegroundService` a fost respinsă în probă: Oreo a declanșat `RemoteServiceException`, deoarece serviciul STOP nu afișa notificarea foreground. Acea variantă a fost înlocuită.

Corecția finală trimite un broadcast explicit către un receiver intern, neexportat, în același proces. Dacă serviciul alarmei rulează, receiverul verifică ID-ul și oprește numai instanța potrivită; dacă nu rulează, nu pornește un serviciu. Nu creează notificări și nu schimbă pornirea sau redarea alarmelor.

Compilarea DeskClock a trecut. APK-ul a fost actualizat cu `pm install -r`, cu hash verificat și originalul păstrat în `output/deskclock-background-before`. Datele aplicației și alarmele Leo nu au fost șterse. Instalatorul refuză actualizarea în timpul unui apel și acceptă `--restore`.

Verificări după instalare:

1. Broadcast explicit `TIME_SET` numai către `com.android.deskclock/.AlarmInitReceiver`, fără schimbarea orei sistemului.
2. Broadcast explicit `STOP_ALARM` către receiverul intern, cu ID inexistent, pentru ramura fără alarmă activă; fără pornirea unei alarme sonore.
3. După încă 16 secunde: bufferul crash a rămas la cele două erori anterioare, fără eroare nouă; niciun `AlarmService` activ și niciun ANR de la boot. Uptime-ul final era 19391 s, deci nu a existat reboot între aceste probe.

Aceste verificări acoperă reconcilierea și cererea STOP fără serviciu activ. Oprirea unei alarme sonore active și ignorarea unei alte instanțe sunt păstrate în cod, dar nu au fost probate sonor în această etapă.

O repornire completă cu această versiune nu a fost efectuată în această etapă. Corecția nu validează singură recuperarea alarmelor Leo după reboot.

## Recompilare și revenire

```text
# în WSL kaios pregătit pentru AOSP
bash android81/build-deskclock-background-stop.sh

# pe PC, după închiderea apelurilor
python -X utf8 android81/install-deskclock-background-stop.py
# revenire la APK-ul salvat, cu datele păstrate
python -X utf8 android81/install-deskclock-background-stop.py --restore
```

Patch-ul AOSP este exportat în `repro/patches/packages__apps__DeskClock.patch`, iar `repro/sources.json` păstrează revizia și SHA-256. Nu sunt publicate APK-uri sau copii ale datelor private.

## Următoarea probă utilă

Capturarea stării thread-urilor adbd și a endpoint-urilor FunctionFS chiar în timpul pierderii transportului, înainte de reconectare, prin monitorizare independentă. Separat: retestare după reboot, telefonie SIM 2 și DTMF. Setarea permanentă a alimentării USB sau resetarea automată a gadgetului la fiecare apel nu sunt justificate de probele curente.
