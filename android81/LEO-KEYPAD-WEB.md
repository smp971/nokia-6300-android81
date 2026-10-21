# Leo · cuvinte și pagini din taste

**0.3.0-dev / cod 3 · 21 octombrie 2026**

Include și pachetul 0.2.0-dev. Sursele sunt pregătite pentru Nokia 6300;
**instalarea și probele pe aparat așteaptă conexiunea ADB**.

## T9: mai multă română, mai puține schimbări de mod

Dicționarul inclus este o listă originală, completată manual cu termeni pentru
conversații, călătorii, vreme și folosirea telefonului. Nu importă vocabularul
proprietar KaiOS și nu folosește un serviciu online. Nu este un dicționar complet
al limbii române.

Predicția examinează toate potrivirile incluse înainte să aleagă cele 12 sugestii:

1. Cuvintele care corespund întregii secvențe au prioritate față de completări.
2. Urmează frecvența alegerilor personale, când învățarea este activată explicit.
3. La egalitate sunt preferate completările mai scurte, apoi ordinea listei originale.

Codurile sunt calculate înaintea sortării, nu în fiecare comparație.
Cuvintele personale nu mai sunt ascunse de plafonul intermediar al listei incluse.
Se păstrează limita de 256 cuvinte personale și formatul existent de backup.

`*` trece la sugestia următoare. O apăsare ținută pe `*` trece o singură dată la
cea precedentă; repetările ulterioare nu continuă ciclul. `0` confirmă și pune spațiu,
OK confirmă fără spațiu, iar `#` schimbă modul, ca înainte.

În modul T9 cu litere mici, editorul poate cere majusculă la începutul propoziției
sau al cuvântului. IME folosește `getCursorCapsMode`, fără să citească sau să
stocheze textul din jurul cursorului. Modul T9 ROMÂNĂ păstrează majusculele explicite.
Mesajele și șabloanele Leo cer acum capitalizarea propozițiilor; Notițe o cerea deja.

Învățarea rămâne **oprită implicit**. Nu se activează în parole, adrese email,
URL-uri, câmpuri numerice sau editori care refuză învățarea personalizată.
Se memorează doar sugestia compusă de IME și confirmată în câmpurile eligibile.

### Dicționar personal

Setări → Tastatură și text → Dicționar personal permite:

- căutare după text sau începutul secvenței T9;
- ordonare alfabetică ori după numărul alegerilor;
- redenumirea unui cuvânt păstrând frecvența;
- ștergere cu confirmare;
- resetarea frecvențelor păstrând cuvintele.

Redenumirea refuză un nume deja existent. Operațiile rămân în pachetul privat
Leo Keypad; dicționarul continuă să poată fi inclus explicit în Backup Leo.

## Browser: cursor pe linkuri, text mai lizibil

Browserul Leo rămâne un **cititor HTML în mod text**, fără WebView, JavaScript,
formulare web, redare video sau descărcări. Nu am înlocuit motorul web al Androidului.
Pentru pagini complexe este în continuare necesar un browser complet compatibil.

| Tastă | Acțiune în pagina afișată |
| --- | --- |
| D-pad sus/jos | Derulare |
| D-pad stânga/dreapta sau 4/6 | Link precedent/următor, cu evidențiere și revenire circulară |
| OK sau 5 | Deschide linkul selectat; dacă nu este ales unul, îl selectează pe primul |
| 0 | Adresă / căutare web |
| 2 | Caută text în pagina deja încărcată |
| 8 | Următorul rezultat local, cu revenire circulară |
| * / # | Micșorează / mărește textul, între 14 și 26 |
| 1 / 3 | Începutul / sfârșitul paginii |
| Înapoi | Pagina păstrată anterior în memorie; apoi Home |

Cursorul este o selecție de link nativă; nu simulează un mouse peste o pagină grafică.
Adresele și căutările sunt introduse cu tastatura existentă. Căutarea web este trimisă
către DuckDuckGo numai la deschidere explicită; căutarea din pagină nu face cereri.

Mărimea textului se păstrează local. Istoricul de revenire păstrează maximum trei
pagini și maximum 256 Ki caractere în total. Revenirea nu reîncarcă pagina de pe
Internet. Pagina curentă are separat limita de 256 Ki caractere; răspunsul de rețea
rămâne limitat la 1 MiB, cu maximum cinci redirecționări și timeout-uri existente.

Linkurile selectabile sunt HTTP/HTTPS, maximum 150. Adresele cu credențiale,
caractere de control sau schemă nesuportată sunt refuzate. Redirecționarea automată
de la HTTPS la HTTP rămâne refuzată. Verificarea TLS nu a fost dezactivată.
Favoritele și istoricul persistent existente se păstrează în spațiul privat Leo;
nu sunt încă incluse în Backup Leo.

## Validare și limite

- Verificările host pentru T9 au trecut: diacritice, ordonare, coliziuni, majuscule,
  editare, formatul backup și limitele dicționarului. Cele 275 de aserțiuni includ
  256 inserări de fixture pentru verificarea capacității, nu 275 de scenarii separate.
- 17 verificări de URL-uri și căutare locală au trecut, fără cereri de rețea.
- Compilarea ambelor aplicații și a APK-ului de probe Android a trecut.
- `BrowserNavigationTests` este pregătit cu HTML local: evidențiere, zoom, căutare,
  revenire și formular de adresă. Nu deschide linkuri și nu face cereri externe.
- Probe pe telefon pentru `*` ținut, majuscule în editor, pagina web reală,
  accesibilitate și instalare: **în așteptare**. Nu sunt publicate capturi noi.

După build:

```bash
bash android81/run-host-keypad-web-checks.sh
```

[Construirea și instalarea pachetului comun](RELEASE-APPS.md).
