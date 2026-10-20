# Căști cu fir: detectare prin ALSA input

## Cauză identificată

Kernelul înregistrează `msm8909-snd-card Headset Jack` ca dispozitiv input cu evenimente switch. În proba cu mufa conectată, `getevent -S` a raportat `0094`: inserție căști, microfon și jack fizic. InputReader vede dispozitivul și aceleași stări.

AOSP avea `config_useDevInputEventForAudioJack=false` și aștepta interfața `h2w`, absentă pe acest port. Dispozitivul extcon existent descrie alimentarea USB; nu este mufa audio. Rezultatul: Android nu selecta căștile, deși driverul le vedea.

## Corecție

Overlay-ul produsului setează `config_useDevInputEventForAudioJack=true`. WiredAccessoryManager citește stările input și transmite conectarea/deconectarea către AudioService. Politica audio existentă include deja WIRED_HEADSET, WIRED_HEADPHONE și microfonul căștilor; nu am schimbat HAL-ul, driverele sau nivelurile mixerului pentru această corecție.

```sh
bash android81/build-connectivity-resources.sh
```

```text
python android81/install-connectivity-resources.py --profile headset
```

Instalatorul compară toate ID-urile resurselor cu APK-ul de pe telefon și permite numai schimbarea `resources.arsc`; păstrează copia originală cu hash în `output/headset-repair/`. Este necesară repornirea după înlocuirea framework-res.apk. Nu se scriu boot sau recovery.

## Probe

Înaintea modificării permanente, `HeadsetTests` a conectat temporar dispozitivul în AudioService și a redat PCM la nivel redus. AudioTrack a selectat efectiv ruta WIRED_HEADSET și a avansat cadrele audio. Utilizatorul a confirmat bipurile în ambele căști. Testul a restaurat volumul și conexiunea temporară.

După repornirea completă, AudioService a raportat automat conectarea ieșirii `type:4` și a intrării `type:80000010`, fără comanda temporară. După scoaterea și reintroducerea fizică a mufei, jurnalul a confirmat automat DEVICE_STATE_UNAVAILABLE, apoi DEVICE_STATE_AVAILABLE pentru ambele dispozitive. Microfonul căștilor, butoanele telecomenzii și convorbirile prin căști nu sunt încă validate. Proba de reportofon anterioară a folosit selecția Android implicită, când căștile nu erau recunoscute.

`HeadsetTests` este o probă de depanare pentru starea inițială fără rută; nu se rulează în utilizarea curentă sau în timpul apelului. Cere confirmarea utilizatorului înaintea bipurilor.
