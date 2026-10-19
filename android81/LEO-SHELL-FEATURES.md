# Extensii Leo pentru telefon cu taste

Cele șase componente sunt implementări Android native inspirate de utilizarea
KaiOS. Nu execută Gaia/Gecko și nu includ motorul T9 proprietar KaiOS.

## Acces din taste

Pe Home: **soft stânga / săgeată jos** deschide Notificări, **săgeată sus** deschide
Configurări rapide, **OK** deschide lista/grila aplicațiilor. Meniul fizic oferă
aceleași destinații din alte aplicații prin serviciul de accesibilitate Leo.

### Notificări

Lista citește notificările Android active prin `LeoNotificationService`. OK
deschide detaliile; soft stânga oferă deschiderea acțiunii originale sau eliminarea
notificării. Notificările permanente nu se elimină. Conținutul rămâne în memorie;
Leo nu creează o arhivă a notificărilor. Acțiunile unei notificări aparțin aplicației
emitente; nu există răspuns direct sau interfață pentru toate butoanele sale.

Oreo blochează acordarea accesului pentru ascultători pe dispozitive low-RAM.
`patch-leo-notification-listener.py` permite grantul **numai** componentei exacte
`ro.leo.visual/ro.leo.visual.LeoNotificationService`. Verificarea apelantului
system/shell și profilul low-RAM rămân active. După instalarea APK-ului și a
runtime-ului `services` potrivit, grantul se acordă explicit de instalator:

```text
/sbin/leo-framework-exec /system/bin/cmd notification allow_listener ro.leo.visual/.LeoNotificationService
```

Nu înlocui doar `services.jar`: acest build folosește preopt ART. Instalatorul
verifică cele 45 de dependențe boot și instalează împreună jar/art/odex/vdex.

### Configurări rapide

Wi-Fi și Bluetooth se comută prin serviciile Android existente. În timpul
tranziției este cerută așteptarea rezultatului. Hotspotul și SIM-urile deschid
paginile native dedicate. Sunt disponibile profilul de sunet și patru trepte de
luminozitate. Opțiunea Vibrații este refuzată când Android nu detectează vibratorul.
Aceste comenzi nu încarcă module audio/kernel și nu confirmă repararea sunetului.

### Contacte și VCF

În Contacte → Opțiuni: căutare după nume/număr sau cifre T9, import/export VCF,
apelare rapidă. Caută → Cifre T9 folosește un câmp numeric, fără conversia cifrelor în multi-tap.
Căutarea T9 normalizează diacriticele românești; nu este predicție
de fraze și nu modifică dicționarul IME.

VCF folosește parserul/compozitorul AOSP `com.android.vcard`, pentru v2.1,
v3.0 și v4.0, inclusiv quoted-printable și UTF-8. Importul citește maximum 1 MiB /
500 contacte, apoi cere confirmare. Adaugă contacte noi; **nu deduplică și nu
suprascrie** agenda. Repetarea unui import creează copii. Inserările se aplică
într-un singur batch atomic; peste 384 KiB de operații fișierul trebuie împărțit.
Contactele fără număr se păstrează în provider, dar lista Leo afișează numerele.

Exportul v3 UTF-8 scrie în **Fișiere → Documente Leo**, cu nume unic și fără a
suprascrie fișiere existente. Limita este 5000 contacte / 8 MiB. Fișierul conține
date personale și nu este încărcat automat nicăieri.

În Apelare rapidă se atribuie contacte tastelor **2–9**. Menținerea tastei pe
Home deschide numărul în Telefon Leo; alegerea SIM-ului și confirmarea apelului
rămân necesare. Nu există apel automat la menținerea tastei.

### Indicatoare SMS și apeluri pierdute

Home și ecranul blocat afișează doar două numere, fără expeditor sau conținut.
SMS-urile noi din oglinda privată Leo sunt necitite până la deschiderea conversației.
Migrarea bazei v1→v2 lasă istoricul existent citit. Marcarea folosește data ultimului
mesaj afișat, pentru a nu șterge starea unui mesaj sosit între timp.

Apelurile pierdute se numără din CallLog după ultimul moment văzut în Leo.
Prima inițializare ia istoricul existent drept văzut. Deschiderea istoricului
Telefon Leo confirmă apelurile afișate; nu editează CallLog sau notificările altei
aplicații. Nu este un contor global sincronizat cu orice aplicație SMS/Telefon.

### Browser text

Reader nativ cu adresă/căutare, linkuri selectabile, derulare D-pad, favorite și
istoric local (30/50 intrări). Rulează într-un proces separat. Acceptă HTTP/HTTPS,
maximum cinci redirecționări și 1 MiB/pagină; verifică certificatele TLS și refuză
redirecționarea HTTPS→HTTP. Nu ocolește erorile de certificat.

Convertește HTML în text. Nu execută JavaScript, formulare, CSS complex, video
sau descărcări; nu este un motor web modern complet. Căutarea folosește
DuckDuckGo Lite și necesită conexiune. Compatibilitatea TLS depinde de Android 8.1
și de certificatele disponibile. Pagina locală de start funcționează offline.
WebView-ul vechi rămâne separat, cu blocajul GPU cunoscut.

### Ghid inițial

Parcurge limba Android, dată/oră, Wi-Fi, SIM-uri, taste, notificări și finalizare.
Paginile existente fac modificările numai la cererea utilizatorului. Nu resetează
preferințe și nu inițiază apeluri/SMS. Leo are încă interfața în română; schimbarea
limbii Android nu traduce automat textele Leo. După finalizare nu se mai deschide
automat, dar rămâne în **Setări → Telefon → Configurare inițială** și în aplicații.

## Verificare

`test-contact-keys.sh` verifică nume românești, cifre T9 și normalizarea numerelor.
`build-six-feature-tests.sh` construiește un APK de instrumentare separat de
producție: vCard 2.1 quoted-printable / 3.0 Unicode / 4.0, import-export prin
provider pe un contact sintetic și marcarea SMS fără a consuma un mesaj mai nou.
Modurile `seed`/`cleanup` creează/elimină doar mesaje și notificări locale de probă,
fără transmisie prin modem. APK-ul de probe nu este parte din ROM.

Rezultatele efective pe telefon și capturile sunt consemnate în README. Paginile
native nu validează automat infrastructura hardware de care depind.

### Rezultate pe TA-1286 · 19 octombrie 2026

- Compilare SDK 27 și verificări JVM T9 trecute.
- Instrumentare pe telefon: VCF 2.1 quoted-printable / 3.0 Unicode / 4.0 și
  import-export pe un contact sintetic, prin ContactsProvider, trecute.
- Import VCF confirmat din taste, căutare `783326` → contactul sintetic Stefan,
  export din meniul Leo, copiere locală și verificare SHA-256 trecute.
- SMS sintetic: citirea până la o dată păstrează un SMS mai nou necitit.
- Notificare locală: listă, detalii, deschiderea PendingIntent și eliminare din
  taste trecute; nu au fost eliminate notificările altor aplicații.
- Wi-Fi și Bluetooth: oprire/pornire din configurările rapide trecute.
  Luminozitate 25% salvată, apoi valoarea/modul inițiale restabilite.
- Home/ecran blocat: câte un SMS și apel pierdut sintetice afișate. Deschiderea
  conversației, apoi a istoricului, a redus separat contoarele la zero.
- Menținerea tastei 9 a deschis PhoneActivity cu numărul sintetic; niciun apel
  nu a fost plasat, iar atribuirea de probă a fost eliminată.
- Browser text: încărcare HTTPS example.org, adăugare/afișare favorit și istoric
  trecute. Nu validează site-uri complexe, autentificare sau formulare web.
- Ghid: toate cele șapte pagini și finalizarea parcurse fără schimbarea
  preferințelor de rețea, timp sau SIM ale utilizatorului.

Contactele, VCF-urile și înregistrările SMS/apel locale de probă sunt eliminate
la final. Instrumentarea oprește pachetul și poate deconecta serviciile Leo;
înainte de a reveni la utilizarea normală se restabilește serviciul de
accesibilitate Leo și se reconectează ascultătorul autorizat de notificări.
Fișierele brute și exporturile de contacte rămân în directoare locale ignorate.

Proba finală a meniului global a injectat scan-code-ul fizic 212 pe dispozitivul
`matrix_keypad`: meniul s-a deschis peste browser, iar intrarea Configurări rapide
a lansat pagina corectă. După repornire au fost verificate din nou ambele servicii
Leo conectate, configurarea finalizată, profilul low-RAM și `/system` read-only.
Audio a rămas dezactivat; această extindere nu validează și nu schimbă driverele.

APK-ul verificat și instalat atât ca actualizare de date, cât și ca bază în
`/system/priv-app/LeoVisual`: SHA-256
`55c9e9742e9933ad196715a690667ae3df7bc5c7a67fa544957e98ece86640f4`.
Reconstruirea finală a surselor a produs același hash. La încheiere, după probele
post-boot reușite, transportul ADB s-a blocat din nou; Windows continua să vadă
telefonul ca Leo init diagnostic. Reconectarea cablului rămâne necesară când
apare acest blocaj cunoscut. Nu a fost observat Qualcomm 900E în această sesiune.
