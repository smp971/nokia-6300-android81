# Radio FM pe Nokia 6300 / Leo

20 octombrie 2026. **Recepție reală confirmată de utilizator în căști, pe 101,3 MHz.**
Interfața Leo și backend-ul sunt experimentale; verificările și limitele sunt separate mai jos.

## Ce funcționează

- Calibrare cu utilitarul original, activare RX, acordare și oprire prin `/dev/radio0` (`radio-iris`, QTI FM Radio Transceiver).
- Scanare 87,5–108 MHz, pas 100 kHz; semnalul maxim al probei inițiale a fost pe 101,3 MHz. Valorile RSSI sunt date brute semnate ale driverului, fără calibrare separată în laborator.
- Redare FM reală prin HAL-ul Qualcomm: `handle_fm` și `fm_volume`, usecase `play-fm`, PCM18 playback/capture active.
- Pagină nativă Radio FM Leo, intrarea stabilă 25 din meniul de 26 aplicații. D-pad acordaj/volum, favorite locale, frecvență directă.
- Conexiune locală autentificată între Leo și procesul root; niciun serviciu de rețea. Aplicația se verifică prin UID, iar clientul verifică serverul root.

Proba instrumentată a trecut: receptor real din UID-ul Leo, acordare din D-pad, favorite, rezultate de scanare persistente, volum, comandă difuzor/căști, oprire la pierderea focusului, repornire și oprire cu OK. Scanarea a lăsat două rezultate de semnal în proba curentă. Confirmarea auditivă a difuzorului și oprirea la scoaterea fizică a căștilor sunt consemnate separat. Rezultatele scanării sunt **vârfuri de semnal**, nu identificări RDS garantate de posturi; unele pot fi zgomotoase. Scanarea amuțește receptorul temporar și revine la frecvența anterioară.

## De ce inițializarea originală eșua

`fm_qsoc_patches 199217 0` ieșea cu 255 înainte de a deschide radioul. Urma locală a arătat că `ro.qualcomm.bt.hci_transport` lipsea. Un strat de compatibilitate furnizează valoarea `smd` **numai procesului utilitarului FM**, în acord cu transportul Iris/SMD folosit de kernel. Proprietățile globale, bibliotecile sistemului și serviciile Bluetooth/modem nu se modifică.

După corecție: calibrare cu succes, STATE=RX acceptat, acordare pe 100 MHz și bandă raportată 87,5–108 MHz; închiderea STATE=OFF și a transportului a trecut. Scanarea ulterioară și ascultarea au confirmat recepția la 101,3 MHz.

`G_TUNER` întorcea EINVAL în proba pasivă fiindcă radioul era oprit. Driverul de referință acceptă această citire numai în mod RX/TX. Portul utilizează numai RX; nu expune mod de emisie.

## Backend și limite

Fișierele runtime proprii și utilitarul original sunt păstrate root-only în `/data/leo-fm`. Dependențele QMI se reutilizează din runtime-ul modemului existent; `libqcci_legacy.so` este extras local din dump. Calibrarea originală este în `/data/misc/fm/Riva_fm_cal`; nu se publică în Git.

Serviciul Android rămâne foreground în timpul redării și are acțiune Oprește în notificare. Monitorizează pierderea focusului audio, deconectarea căștilor și starea apelurilor pentru SIM-urile active. Nu inițiază apeluri și nu pornește automat la boot.

Sesiunea root primește heartbeat la trei secunde și se închide la deconectarea clientului sau lipsa comenzilor timp de 18 secunde. Există o limită de o oră pe sesiune și cleanup supravegheat separat. Aceste mecanisme de userspace nu pot recupera un blocaj în kernel. Probele scurte au watchdog de 25/30/45 secunde. Scanarea verifică deconectarea clientului între frecvențe.

La oprire, aplicația face shutdown pe ambele sensuri ale socketului. Simplul close lăsa citirea blocată și întârzia repornirea până la expirarea heartbeat-ului; problema a fost corectată și repornirea rapidă a trecut proba.

Comutarea căști/difuzor folosește API-ul AudioSystem al platformei pentru ieșirea primară, apoi selectarea rutei FM. La oprire se restaurează ruta indicată de politica Android. Nu schimbăm preferințele globale de forțare a difuzorului. Căștile trebuie să rămână conectate ca antenă, inclusiv la redarea pe difuzor.

Rămân de validat: RDS/nume post, sesiuni îndelungate, consumul real pe baterie, toate scenariile de apel și reluarea manuală după reboot. Nu înregistrăm programe radio în aceste probe.

## Utilizare

Meniu Leo → Radio FM → OK pentru pornire/oprire. Stânga/dreapta: pas 0,1 MHz. Sus/jos: volum FM. Opțiuni oferă canale scanate, scanare, favorite, salvarea frecvenței și comutarea difuzorului. Favoritele sunt limitate la opt; rezultatele scanării se păstrează local.

## Recompilare

Necesită arborele AOSP și runtime-ul modemului din proiect, plus dump-ul original local.

```sh
bash android81/build-fm-diagnostic.sh
bash android81/build-fm-route.sh
bash android81/build-visual-shell.sh
```

Pe PC, cu radioul oprit:

```text
python android81/stage-fm-trial.py
```

Scriptul verifică hash-urile, încarcă fișierele atomic în directorul privat și testează linker-ul prin afișarea utilizării utilitarului, fără inițializare FM. APK-ul Leo se instalează separat. Nu se scriu boot/recovery și nu se înlocuiesc biblioteci Android.

Probe native prin `framework-command.py`:

```text
/data/leo-fm/trial --watchdog-test
/data/leo-fm/trial --receive
/data/leo-fm/trial --scan
/data/leo-fm/trial --route-check
/data/leo-fm/trial --listen 101300
```

Ultima comandă redă 15 secunde la volum redus și se rulează numai după anunțarea utilizatorului. Logul este root-only, `/data/leo-fm/trial.log`.

`FmTests` din APK-ul separat de instrumentare verifică receptorul real cu volum zero, acordarea din taste, favoritele, scanarea, rutele audio, pierderea focusului și repornirea. Nu inițiază apeluri/SMS. Opțiunea `-e keep-scan true` păstrează în mod explicit rezultatele scanării pentru utilizare; celelalte preferințe sunt restaurate.

## Surse de referință

- [Driver Iris și controalele RX](https://android.googlesource.com/kernel/msm/+/b3c1477026089ba53fe791515dbe125b2b147779/drivers/media/radio/radio-iris.c)
- [Transportul Iris SMD](https://android.googlesource.com/kernel/msm/+/b3c1477026089ba53fe791515dbe125b2b147779/drivers/media/radio/radio-iris-transport.c)
- [Extensia FM a HAL-ului msm8909](https://android.googlesource.com/platform/hardware/qcom/audio/+/582e0a5/msm8909/hal/audio_extn/fm.c)

Acestea sunt repere de implementare; comenzile private și comportamentul au fost verificate pe telefon. Sursele proprii sunt în `fm-runtime/`, `FmActivity.java` și `FmService.java`. Componentele originale extrase și datele de calibrare rămân locale.

## Capturi de pe telefon

| Acordaj și semnal măsurat | Meniu din taste |
| --- | --- |
| ![Radio FM Leo](screenshots/leo-fm-tuner.png) | ![Meniu Radio FM](screenshots/leo-fm-menu.png) |

## Verificarea opririi după comutarea pe difuzor

Utilizatorul a confirmat lista scanată și selectarea frecvențelor, dar a raportat oprirea sunetului după aproximativ o secundă pe difuzor. Jurnalul HAL confirmă activarea rutei speaker, urmată de închiderea sesiunii de aplicație; motivul exact nu era înregistrat în versiunea inițială. Serviciul înregistrează acum motivul opririi în tagul `LeoFM`, fără date personale.

Testul mut a fost extins: selectează activarea difuzorului din meniul real, apoi verifică dacă sesiunea rămâne activă încă opt secunde. A trecut pe telefon, la fel și revenirea la căști. Această probă inițială nu reproducea evenimentul fals al mufei; corecția și confirmarea manuală sunt consemnate mai jos.

### Evenimente false ale butoanelor mufei

La proba manuală ulterioară, `LeoFM` a raportat `Stop: Oprit` imediat după `routing=2`: era comanda de oprire a aplicației, fără o pierdere de focus sau o eroare Iris. InputReader identifică `msm8909-snd-card Button Jack` cu `Generic.kl`; scan 260 devine `BUTTON_5`, iar `Generic.kcm` din AOSP îl transformă prin fallback în `DPAD_CENTER`. Jurnalele anterioare conțin astfel de evenimente cu scan 260, device 5 și flag 0x400 (fallback). `BUTTON_4` are același fallback.

Leo ignoră acum scanurile 259/260 numai de la dispozitivul cu numele exact `msm8909-snd-card Button Jack`, atât în activități și meniurile contextuale, cât și în serviciul global al tastelor. Tastatura matrix, OK fizic și evenimentele obișnuite ale telecomenzii de căști rămân pe traseul existent. Nu se rescrie keylayout-ul sistemului.

Proba FM injectează explicit evenimentul de fallback OK cu dispozitivul real al mufei după selectarea difuzorului, apoi așteaptă opt secunde înainte să verifice redarea. După instalare, utilizatorul a confirmat explicit că redarea continuă pe difuzor cel puțin zece secunde, cu căștile conectate. Tastele fizice au fost de asemenea reconfirmate ca funcționale. După instrumentare, serviciul de accesibilitate este reconectat și pagina FM este redeschisă; testul nu trebuie să lase utilizatorul cu tastele indisponibile.

## Căutare automată și RDS

Meniul FM include „Postul următor” și „Postul anterior”. Backend-ul verifică frecvențele în pași de 100 kHz în direcția aleasă, cu revenire la capătul benzii și maximum o tură (206 poziții). Selectează primul maxim local de semnal de cel puțin -85; exclude vecinătatea imediată a frecvenței inițiale. Este o căutare software după RSSI, nu identificare garantată de post și nu ioctl-ul hardware SEEK. La lipsa unui candidat revine la frecvența inițială. Sunetul este temporar oprit în timpul căutării; deconectarea sau comanda de oprire întrerupe operația.

Controalele QUERYCTRL de pe telefon confirmă RDSON (+0x0f) și RDS processing (+0x10). Sesiunea activează RDS și procesarea PS/RT cu masca 7, în acord cu API-ul Qualcomm. La fiecare heartbeat de trei secunde citește numai FIFO-urile private RT (2) și PS (3), cu limite stricte de buffer. Nu citește FIFO-ul de evenimente (1), care poate bloca. Erorile activării RDS nu împiedică recepția FM.

Numele PS și textul RT sunt trimise în hex prin socketul deja autentificat, împreună cu frecvența. Leo verifică lungimea, înlătură caracterele de control și afișează numai mesajele pentru frecvența curentă. La acordare, scanare sau seek se șterge metadata veche. PS este limitat la primul nume de opt caractere; RT la 64. În această versiune caracterele RDS extinse au substituent; tabela completă de caractere EBU rămâne de implementat.

În proba pe telefon din 20 octombrie, controalele au răspuns cu succes, iar jurnalul a raportat `RDS_DATA kind=RT frequency=101300 bytes=26` și `RDS_DATA kind=PS frequency=101300 bytes=8`. Testul a trecut de verificarea schimbării frecvenței prin căutare și a ajuns la recepția RDS și scanare, dar a fost oprit la cererea utilizatorului după ce interfața nu mai răspundea în timpul automatizării. **Suita extinsă nu este declarată trecută.** Leo a fost repornit, nivelul volumului și lista scanată anterioare au fost restaurate și serviciul tastelor reconectat. Verificarea finală a funcțiilor noi se face manual.

Repere suplimentare: [API Qualcomm RDS](https://github.com/LineageOS/android_hardware_qcom_fm/blob/lineage-15.1/qcom/fmradio/FmRxRdsData.java), [semantica PS/RT](https://github.com/LineageOS/android_hardware_qcom_fm/blob/lineage-15.1/qcom/fmradio/FmReceiver.java). Sursele au fost consultate ca referință, fără a copia biblioteca Java în Leo.

Confirmare manuală după recuperarea interfeței: utilizatorul a verificat că tastele răspund, numele postului apare pe ecran după așteptare și căutarea automată schimbă frecvența. Confirmarea explicită a afișării întregului text RT și recepția în condiții diferite de semnal rămân separate.
