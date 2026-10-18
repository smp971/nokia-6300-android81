# Alternativă KaiOS peste Android: rezultate și plan

**19 octombrie 2026 — etapă de analiză, fără modificări pe telefon.**

## Ce avem efectiv în dump

- KaiOS 2.5.4 și Gecko 48.0a2, declarate în `b2g/application.ini`.
- 64 de intrări de aplicații, 63 de arhive locale, aproximativ 73,4 MiB comprimat.
- System: 816 fișiere, 68 de permisiuni declarate și 20 de canale inter-aplicații.
- Launcher: 19 permisiuni și referințe către 17 denumiri de API specifice detectate static.
- Tema implicită conține două fișiere CSS, un HTML și manifestul. Nu este singură întregul pachet vizual: iconuri, fonturi, imagini de fundal și sunete sunt distribuite în celelalte aplicații.
- Launcherul conține comenzile `SoftLeft`, `SoftRight`, săgeți, `Enter`, `Call` și `EndCall`.
- 182 de declarații de servicii în toate fișierele RC din ramdisk; includ variante condiționale, nu 182 de servicii dovedite active.
- 1.861 de intrări în indexul optimizat `omni.ja`, inclusiv componente pentru setări și contacte. Motorul include și binare native `b2g` / `libxul.so`.

Inventar complet: [KAIOS-UI-INVENTORY.md](KAIOS-UI-INVENTORY.md).

## Decizia propusă pentru arhitectură

Construim o interfață alternativă peste **serviciile Android deja integrate**.
Folosim dump-ul ca referință pentru aspect, navigare și comportament și testăm
separat reutilizarea unor aplicații web. Nu avem încă o aplicație KaiOS originală
demonstrată funcțional pe Android în acest proiect.

System/Launcher originale nu pot fi tratate ca pagini web autonome: ele folosesc
API-uri privilegiate și coordonează ferestre, notificări, aplicații și servicii.
Un test HTML reușit nu validează acest ansamblu. WebView-ul actual are un crash GPU
documentat; nu alegem încă un shell complet web drept interfață principală.

Portarea întregului motor B2G rămâne o investigație separată: avem binarele,
dar nu o probă de compatibilitate pe Android 8.1. Ar necesita verificarea ABI,
dependențelor native, afișării, IPC și consumului de memorie. Nu avem dovezi că
ar fi mai rapid decât soluția Android actuală.

## Componente și adaptare

| Componentă KaiOS | Dependență observată / rol | Alternativa propusă |
| --- | --- | --- |
| System | Ferestre, notificări, softkeys, permisiuni, servicii | UI proprie + integrare Android; nu înlocuim init sau system_server cu JS. |
| Launcher | mozApps, setări, telefonie, contacte, mesaje | Extindem Leo cu aspect și navigare similare; registru Android și eventual registru propriu pentru webapps. |
| Tema, iconuri, fonturi, fundaluri, sunete | Resurse distribuite în mai multe pachete | Inventariere și prototip vizual separat; păstrăm resursele originale local, verificăm proveniența înainte de distribuire. |
| Tastatură | mozInputMethod, mozSettings | IME Android pentru taste, multi-tap/T9 și dicționare evaluate separat; copierea tastaturii JS nu oferă automat integrarea IME. |
| Apeluri și jurnal | mozTelephony, contacte, canale către System | Telecom/InCallService și jurnalul Android; păstrăm calea de apel deja verificată. |
| Contacte | mozContacts și stocare | ContactsProvider și permisiuni Android; test separat de citire/scriere. |
| SMS/MMS | mozMobileMessage, SIM și evenimente de fundal | Aplicație SMS Android cu rolul și permisiunile necesare; transportul real rămâne de verificat. |
| Setări | mozSettings și numeroase API-uri hardware | Ecran adaptat la taste, Settings Android și preferințe proprii; afișăm starea reală a componentelor. |
| Ceas, alarme, calendar, notițe | Alarme, baze de date, activități web | Implementări adaptate sau punți restrânse către serviciile Android, cu probe de persistență și fundal. |
| Calculator / convertor | Mai puține dependențe statice, dar nu zero | Primele candidate pentru un test web local; încă netestate în runtime-ul Android. |
| Galerie, muzică, video, recorder, fișiere | DeviceStorage, canale audio, activități | MediaStore, codec-uri, player/recorder și acces la fișiere Android; verificare per format și operație. |
| Cameră, FM, Wi-Fi, Bluetooth, GPS | API-uri specifice și suport hardware | Mai întâi integrarea driverelor/HAL/serviciilor; o interfață nouă nu repară aceste blocaje. |
| Store, conturi, plăți, aplicații online | Servicii externe și permisiuni speciale | Nu avem o probă de interoperabilitate; prezența pachetului nu confirmă serviciul online. |
| FOTA, anti-theft, provisioning, engmode | Daemoni și acces privilegiat la platformă | Nu se activează prin copierea aplicației; necesită proiectare și validare separată. |
| Servicii Qualcomm | RIL, audio, modem și IPC nativ | Păstrăm integrarea Android existentă; nu pornim un al doilea set de daemoni din KaiOS. |

## Ordinea de lucru

1. **Inventar — realizat:** aplicații, permisiuni, API-uri detectate, resurse și declarații init.
2. **Prototip vizual izolat:** home, grilă de aplicații și etichete softkeys, comparate cu originalul. Lansare ca aplicație de test, cu revenire la Leo.
3. **Probă web minimă:** calculator sau convertor local. Verificăm întâi lipsa API-urilor, tastele, randarea și erorile; nu declarăm compatibil toate pachetele după o singură probă.
4. **Contract de integrare:** comenzi explicite și evenimente pentru fiecare funcție Android. Nu expunem execuție arbitrară sau acces general la sistem printr-o punte JavaScript.
5. **Aplicații esențiale:** contacte, jurnal, apeluri și SMS, cu date de test și confirmări pe telefon.
6. **Servicii de fundal și restul aplicațiilor:** notificări, alarme, persistență după reboot, media; apoi componentele hardware și serviciile externe.

Pentru fiecare etapă comparăm memoria proceselor și începutul desenării după
apăsarea unei taste cu Leo actual. Verificăm și consumul în repaus, revenirea
după oprirea procesului și comportamentul când intră un apel. Nu estimăm acum
un câștig de viteză: acesta trebuie măsurat pe telefon.

## Livrabilele acestei etape

- Script reproductibil: `analysis/inventory_kaios_ui.py`.
- Inventarul metadatelor tuturor aplicațiilor și declarațiilor de servicii.
- Acest plan de mapare către Android.
- Rezultate brute locale în `android81/output/kaios-ui-inventory.json`.

Nu s-au instalat aplicații, nu s-au înlocuit servicii și nu s-au rescris partiții
în această etapă. Următoarea etapă este prototipul vizual izolat, nu migrarea
imediată a tuturor aplicațiilor și serviciilor.
