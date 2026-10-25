# GNSS · integrare experimentală

Stare la 24 octombrie 2026: HAL-ul KaiOS se încarcă și serviciul AOSP GNSS HIDL
1.0 s-a înregistrat în proba temporară. Nu este încă o confirmare de poziționare.

## Proba izolată

`build-gps-linker-probe.sh` construiește `gps-linker-probe.c`. Procesul de probă
are namespace de montare/rețea separat, `/dev` ascuns, UID 9999, limită de memorie
și durată. Încarcă numai biblioteca; nu apelează metodele GPS și nu inițializează
modemul. Rezultat: `DLOPEN_OK`, modul `gps`, `loc_api GPS Module`, API 1.0.

## Serviciul Android

`build-gps-runtime.sh` construiește din AOSP `android.hardware.gnss@1.0-service`
și `android.hardware.gnss@1.0-impl`, apoi wrapper-ul `gps-runtime-exec.c`.
`prepare-gps-runtime.py` pregătește proba limitată în `/tmp/leo-gnss-runtime`.
În acea probă, `lshal` a enumerat `IGnss/default` în procesul nou.

Runtime-ul permanent are bibliotecile private `libloc_eng`, `libloc_core`,
`libloc_api_v02`, `libloc_ds_api`, `libgps.utils` și HAL-ul `gps.default.so` din
dump. Refolosește dependențele QMI și adaptorul IPC deja instalate pentru radio.
Wrapper-ul montează directorul HAL privat doar în procesul GNSS, apoi rulează
serviciul cu UID GPS 1021. Nu modifică bibliotecile HAL globale.

`install-gps-runtime.py --install` verifică telefonul și absența apelurilor,
salvează originalele existente local, instalează fișierele și verifică SHA-256.
Include `gps.conf` original și declarația Android a funcției GPS. Activează
`persist.leo.gps.enabled=1`; fișierul init pornește serviciul devreme la următoarea
pornire, înainte de încărcarea furnizorilor de localizare în framework.

Oprirea explicită se face prin setarea `persist.leo.gps.enabled=0`. Comanda trebuie
executată în namespace-ul Android prin `framework-command.py`, ca celelalte
comenzi ale portului. Nu sunt necesare modificări boot/recovery/modem/NV.

## Verificări din 25 octombrie

Serviciul `leo_gnss` pornește automat și Android enumeră furnizorul `gps`.
Proba `GpsRuntimeTrial` a deschis aplicația, a făcut o cerere reală timp de
30 de secunde și a confirmat oprirea ei la ieșire. Nu s-au observat sateliți
și nu s-a obținut o poziție în această probă; amplasarea telefonului nu a fost
confirmată ca având vizibilitate spre cer. Rezultatul confirmă ciclul cererii,
nu funcționarea recepției GNSS.

## Ce trebuie confirmat în continuare

1. Inițializarea completă către modem după o cerere reală de localizare.
2. Sateliți și o poziție obținută într-un loc cu vizibilitate bună spre cer.
3. O sesiune mai lungă fără regresii radio.

Absența unei poziții în interior nu dovedește o defecțiune. Coordonatele, jurnalele
brute și locurile salvate rămân locale. Nu se publică în GitHub.
