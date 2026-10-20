# Leo · aspect flat

20 octombrie 2026. Actualizare vizuală instalată pe Nokia 6300 TA-1286.

- Home și meniul de aplicații au fundal uniform. Fundalul ales de utilizator rămâne disponibil pe Home.
- Pictogramele comune păstrează culorile și simbolurile; umbrele și reflexele au fost eliminate.
- Antetele, listele, câmpurile și meniurile folosesc suprafețe simple, cu colțuri discret rotunjite. Selecția deschisă și textul închis rămân vizibile din taste.
- Muzică și Calculator folosesc fundaluri uniforme, panouri fără luciu și text sans-serif. Vizualizatorul și cifrele calculatorului sunt păstrate.
- Ecranul de blocare și previzualizarea temelor folosesc aceeași direcție vizuală. Temele colorate existente, imaginile stock și fotografiile pentru fundal sunt păstrate.

Sursele comune: `Appearance.java`, `LeoActivity.java`, `IconArt.java`, `RetroStyle.java`, `KeypadDialog.java`; adaptări în `MainActivity.java`, `KeypadLockActivity.java` și `WallpaperActivity.java`.

## Verificare

Compilare APK reușită, instalare cu `pm install -r` și sincronizarea copiei privilegiate din sistem, fără reboot și cu datele păstrate. Verificate prin capturi reale: Home, grila aplicațiilor, Setări, playerul în pauză, Calculator și Opțiuni. Navigarea ADB a deschis paginile și meniurile; nu s-au inițiat apeluri, SMS sau redare audio. `dumpsys activity lastanr` nu raportează ANR de la boot. Verificarea manuală a tastelor fizice rămâne separată.

Nu este o modificare a rendererului sau framework-ului Android și nu se declară un câștig de performanță fără măsurători. Aplicațiile Android externe își păstrează propria temă.

Capturile sunt în `screenshots/leo-flat-20261020-*.png`, prezentate în README. Pentru recompilare se folosește `build-visual-shell.sh`; nu sunt necesare imagini generate sau resurse externe noi.
