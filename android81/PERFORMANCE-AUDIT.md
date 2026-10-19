# Măsurători UI și ADB · 19 octombrie 2026

Telefon TA-1286, Android 8.1 intern, Adreno, `low_ram=true`. Jurnalele brute
rămân în `output/`, exclus din Git. Nu s-au inițiat apeluri/SMS și nu s-au
încărcat drivere audio în aceste probe.

## Rezultat instalat

`LeoActivity` reutilizează rândurile TextView din liste. La schimbarea selecției
recolorează numai rândul vechi și cel nou, în loc să invalideze întregul adapter.
Derularea listei lungi de aplicații și selecția din Configurări au fost verificate.

Comparație pe aceeași pagină Configurări, trei probe de câte 40 apăsări sus/jos,
60 ms apăsat + 60 ms eliberat, injectate în dispozitivul fizic `matrix_keypad`:

| Măsură | Înainte | După |
| --- | --- | --- |
| p95 HWUI, cele trei probe | 8 / 7 / 7 ms | 5 / 5 / 5 ms |
| Mediană framestats, toate cadrele valide | 6,20 ms | 4,06 ms |
| p95 framestats, toate cadrele valide | 7,67 ms | 5,45 ms |
| Cadre valide | 239 | 262 |

Duratele framestats sunt `FrameCompleted - IntendedVsync`, filtrate după începutul
probei și Flags=0. Numărul de cadre diferă: o apăsare și o eliberare pot produce
invalidări separate. Proba CLI finală, o repetare, a produs 92 cadre, mediană
4,11 ms și p95 5,99 ms (găleata p95 HWUI: 5 ms).

Aceste valori măsoară lucrul aplicației, **nu timpul de la tastă la pixel**.
HWC raportează aproximativ 29 Hz, iar ioctl-ul de prezentare framebuffer are
mediană aproximativ 33,05 ms. Erorile EGL de așteptare a fence-ului încă apar.
Nu avem o măsurătoare optică a latenței și nu pretindem eliminarea întârzierii.

La repaus: aproximativ 381 MiB RAM utilizabili, 164 MiB disponibili, PSS Leo
14,7 MiB, CPU 99–100% liber. Există pagini în zRAM, dar fără trafic swap activ
în eșantioanele de repaus. Governorul era deja `performance`.

## Experiment respins

`patch-leo-consumer-fence.py` oferă un experiment explicit, oprit implicit:
dezactivarea așteptării EGL server-side în `SyncFeatures::useWaitSync` când
`persist.leo.adreno.cpu_wait=true`. Erorile EGL au dispărut, dar afișarea s-a
blocat: cele trei probe au produs numai 1 / 0 / 0 cadre valide. Valoarea 4950 ms
din histograma fără cadre nu este o durată măsurată și nu intră în comparație.

Experimentul a fost retras din sursa AOSP prin `--undo`, proprietatea este 0,
iar `libgui.so` original a fost restaurat și verificat după reboot:
`0c828be3f445120a11969b8deff9b6dfa7d618aadc2e886f260803ecc4b10b4c`.
Scripturile experimentului sunt păstrate pentru reproducere, nu reprezintă
configurația recomandată. Nu instala această bibliotecă pentru uz curent.

## Reproducere

Cu telefonul deblocat, fără apel și fără apăsări manuale:

```text
wsl -d kaios -- bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/build-keypad-frame-bench.sh
python android81/measure-keypad-frames.py --label settings-check --trials 3
```

Benchmark-ul verifică identitatea dispozitivului de intrare și injectează numai
săgeți. Măsurarea salvează diagnosticele local și restabilește proprietatea de
trasare. Nu deschide ecrane de apel și nu trimite mesaje.

## ADB

`framework-command.py` are limită implicită de 25 s și acceptă un argument
inițial `--timeout SECONDS`. La depășire termină numai clientul ADB pornit de el,
cu cod 124; serverul comun rămâne activ. Proba `--timeout 1 ... sleep 3` s-a
încheiat în 1,42 s, iar următoarea comandă a răspuns normal. 30 cereri native
succesive au trecut, mediană 93 ms, maxim 110 ms.

Aceasta limitează blocarea uneltelor de pe PC. Nu repară întreruperile USB:
după unele reporniri de framework este încă necesară reconectarea cablului.
La ultima repornire completă, cu animația și configurația SD instalate, ADB a
revenit fără intervenție. Nu este suficient pentru a declara problema rezolvată.
