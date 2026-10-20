# Home cu carduri, telefonie și vibrații audio

Etapă 20–21 octombrie 2026, pe Nokia 6300 TA-1286 de dezvoltare.

## Telefonie

Utilizatorul a confirmat că popupul Android nu mai apare la apelul primit.
Notificarea proprie rămâne pe canalul discret `leo-call-quiet`, iar ecranul Leo
este deschis direct de InCallService. La proba de răspuns, microfon, difuzor și
închidere, utilizatorul a confirmat funcționarea și a cerut reducerea probelor
repetate. Nu s-au inițiat alte apeluri automate.

DTMF există pe taste, dar recunoașterea tonurilor de către un serviciu din rețea
nu a fost verificată în această etapă. Notificările reale de apel pierdut și
SIM 2 rămân scenarii distincte, fără a pretinde că au trecut acum.
ADB s-a deconectat în timpul apelului, ca în probele anterioare; investigația
transportului USB nu a fost reluată.

## Carduri Home

Un card la un moment dat, pentru a păstra lizibilitatea pe ecranul 240×320:

- **Meteo:** orașul ales, temperatură și stare din cache, cu data/ora salvării.
  Home nu pornește cereri de rețea; deschide Meteo pentru actualizare.
- **Următoarea alarmă:** prima alarmă Leo activă, calculată în fusul local,
  sau mesaj explicit dacă nu există. Deschide direct fila Alarme din Ceas.
- **Acum se redă:** titlul piesei și starea ei sau postul FM activ; deschide
  pagina comună Muzică/FM. Nu pornește redarea automat.

`*` schimbă cardul; `#` îl deschide. Se poate selecta și din ciclul stânga/dreapta,
după cele patru scurtături, apoi OK. OK fără selecție păstrează accesul la aplicații.
Personalizare → Home · ceas și informații permite dezactivarea fiecărui card.
Cu carduri active, ceasul este compact pentru a evita suprapunerile.

Datele sunt recitite la revenire, schimbarea preferințelor, evenimente media și
actualizarea ceasului la minut. Nu există un nou serviciu de polling în fundal.
`HomeCardsTests` a trecut șase verificări offline: tipuri stabile, lipsa unei
prognoze fără temperatură inventată, lipsa alarmelor, cache etichetat, ascunderea
unui card și dezactivarea tuturor. Preferințele reale sunt restaurate după test;
testul nu programează alarme și nu pornește media sau rețeaua.

| Meteo din cache | Următoarea alarmă existentă | Muzică / FM |
| :---: | :---: | :---: |
| ![Meteo Home](screenshots/leo-home-cards-weather.png) | ![Alarmă Home](screenshots/leo-home-cards-alarm.png) | ![Media Home](screenshots/leo-home-cards-music.png) |

## Vibrații: cauza și corecția

Activarea directă a driverului PMIC și primele comenzi audio nu produceau vibrație.
Jurnalul HAL a explicat de ce a doua cale nu funcționa:
`Haptic feature not initialized, cannot start`.

În build.prop KaiOS existau trei proprietăți absente din port:

```
vendor.audio.haptic_audio=true
vendor.audio.haptic_freq=157
vendor.audio.haptic_ampl=100
```

După aplicare și repornirea audioserver, HAL a confirmat inițializarea,
generarea semnalului la 157 Hz și utilizarea PCM 41 cu ruta
`audio-haptic-playback`. **Utilizatorul a simțit cele trei impulsuri de 650 ms.**
Aceasta confirmă calea haptică audio; vechiul control LED/PMIC nu este folosit
ca dovadă pentru funcționarea fizică.

Adaptorul HIDL Vibrator::on/off trimite acum parametrii AudioSystem când
proprietatea haptică este activată, păstrând fallback-ul upstream pentru alte
configurații. Durata este limitată la 15 secunde pe comandă; amplitudinea variabilă
și efectele predefinite rămân declarate nesuportate. Nu se scriu registre PMIC brute.

`VibrationApiTest` a trecut prin API-ul Android: un impuls de 300 ms, urmat de cancel.
Jurnalul confirmă primirea parametrului de către HAL și scrierea a **14.400 de
mostre** în PCM-ul haptic. Această probă de integrare are dovadă software; confirmarea
fizică explicită a utilizatorului este pentru cele trei impulsuri precedente.

## Instalare și recompilare

- `init.leo.haptic.rc` stabilește proprietățile înainte de inițializarea audio și
  este inclus în device-common.mk.
- `prepare-audio-vibrator.py` aplică adaptorul în arborele AOSP local;
  `build-vibrator-hal.sh` construiește biblioteca. Patch-ul hardware/interfaces
  este exportat cu SHA-256 și revizie fixată în manifestul de reconstrucție.
- `install-audio-vibrator.py --activate` păstrează copii locale de rollback,
  instalează atomic HAL/configurația, repornește audio și framework-ul, apoi
  remontează /system read-only. `--undo --activate` restaurează copiile salvate.
- Instalatorul refuză înlocuirea unei biblioteci mapate fără `--activate`.
  Prima instalare a întâlnit un remount read-only ocupat de vechea bibliotecă
  mapată; repornirea framework-ului a eliberat-o, iar /system a revenit read-only.
- Repornirea parțială a produs dialogul platformei „SIM card added”. A fost cerută
  apoi o repornire completă, pentru inițializarea normală a SIM-urilor și verificarea
  configurației persistente. Nu s-au rescris boot/recovery.

După pornirea completă, uptime-ul a revenit la zero, `sys.boot_completed=1`,
Home Leo a devenit fereastra activă, fără dialogul SIM. Proprietățile haptice
au fost `true / 157 / 100`, audioserver a pornit, iar noua bibliotecă era mapată
în system_server. /system a fost verificat read-only, iar bufferul crash al
noului boot nu conținea excepții fatale în acel moment.

Cardurile Meteo/Alarmă/Muzică au fost inspectate pe ecran. `*` le schimbă;
`#` pe Alarmă a deschis fila Alarme din Ceas. Piesa curentă/următoarea alarmă
sunt preluate din starea existentă, fără pornire automată de redare.

Un apel cu vibrație simultană, consumul haptic de durată și toate profilurile sonore
nu sunt validate prin această singură probă.
