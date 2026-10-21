# Leo · făcut pentru fiecare zi

21 octombrie 2026 · Nokia 6300 4G TA-1286 · Android 8.1 / API 27.

Telefonul își recunoaște contactele, mesajele cer mai puține apăsări, iar muzica
își amintește unde ai rămas. Șase completări native, fără un nou framework grafic.

| Zonă | Ce s-a schimbat | Acces din taste |
| --- | --- | --- |
| Telefon | Numerele recunoscute sunt prioritare față de potrivirile parțiale; rezultat cu nume, fotografie sau inițiale. T9 românesc păstrat. | Introdu numărul sau cifrele numelui; jos → rezultatul găsit → OK. Alegerea contactului completează numărul, fără apel automat. |
| Mesaje | Răspuns rapid, șabloane locale editabile și căutare T9 în conversație, peste redirecționarea existentă | **5** în conversație deschide editorul. Opțiuni → Răspuns din șablon sau, în editor, Opțiuni → Inserează șablon. |
| Ceas de birou | Ora mare, dată, următoarea alarmă, baterie și luminozitate redusă | Ceas → fila Ora → Opțiuni → Ceas de birou. Sus/jos: 5/10/20/40%; OK: păstrarea ecranului aprins. |
| Colecții vizuale | Leo Night · Turcoaz, Nordic · Albastru și Solar · Chihlimbar, comune pentru Home, meniuri, player și Telefon/ecran de apel | Personalizare → Colecții vizuale complete → previzualizare → Aplică → confirmare. |
| Muzică | Versuri LRC locale, oprire temporizată și reluarea ultimei sesiuni/cozi | Player → Opțiuni. Reluarea este disponibilă și în Biblioteca muzicală. |
| Ajutor Leo | Aplicație offline cu hartă a tastelor, scurtături, instrucțiuni și starea funcțiilor | Intrarea nouă **Ajutor Leo** din Aplicații, Acces rapid Home sau Setări → Telefon. |

## Telefon și mesaje

Indexul Telefonului rămâne limitat la 1000 de numere și cinci sugestii afișate.
Ordinea este: număr recunoscut, început de nume T9, început de număr, potrivire
parțială. Fotografiile sunt decodate în worker la maximum 64 px și decupate
proporțional; cache-ul păstrează cel mult 12 miniaturi. Nu se interoghează un
serviciu extern și nu se inițiază apeluri din potrivirea unui contact.

Redirecționarea și căutarea existau deja și au fost păstrate. Căutarea acceptă acum
și cifre T9/diacritice, prin normalizarea comună Leo. Răspunsul rapid folosește
același editor și aceleași confirmări de SIM/trimitere. Șabloanele nu sunt mesaje
programate: alegerea lor completează textul, iar utilizatorul confirmă trimiterea.
Există maximum 20 de șabloane, fiecare de cel mult 240 de caractere; editorul
păstrează limita de 1000 de caractere și contorul de segmente GSM/Unicode.

Șabloanele sunt incluse în Backup v2, în categoria Notițe și șabloane SMS.
Testele folosesc modul demo al Mesajelor: nu trimit și nu salvează ciorne reale.

## Ceas și teme

Ceasul de birou schimbă doar `Window.screenBrightness`. Nu scrie luminozitatea
sistemului sau timpul de repaus. Păstrarea ecranului aprins este opțională și
implicit oprită; la ieșire nu rămâne un serviciu de ceas ori un wake lock.
Ora se redesenează la minut și se deplasează discret cu câțiva pixeli. Bateria
se actualizează din broadcast-ul Android, iar alarma respectă zilele selectate.

Colecția completă setează tema globală, accentul din temă și moștenirea culorilor
în Telefon. Confirmarea elimină suprascrierile individuale cunoscute, pentru un
aspect coerent. Fundalul personal este păstrat. Stilurile pe aplicații pot fi
configurate din nou ulterior. Opțiunea „Telefon · Tema Leo / Leo Night” din Stil
Leo permite revenirea la comportamentul separat al dialerului. Pe aplicațiile
Android externe nu se aplică aceste teme. Colecțiile noi sunt opționale; tema
utilizatorului a fost restaurată după probe.

## Muzică: local și la cerere

### Versuri

Un fișier UTF-8 `piesa.lrc` lângă `piesa.mp3` se încarcă automat când deschizi
Versuri Leo. Sunt acceptate marcaje `[mm:ss]`, zecimi/sutimi/milisecunde, marcaje
multiple pe același rând și `[offset:+/-milisecunde]`. Fișierele fără timpi sunt
citibile în mod manual. Limite: 256 KiB, 2000 de intrări, 4000 de rânduri sursă.

Poți alege și un LRC local din Opțiuni: dosarul piesei, Documente Leo / Lyrics și
Documents/LeoLyrics de pe telefon/card. Căutarea este limitată; nu scanează tot
volumul și nu descarcă versuri. Pentru o sursă care nu oferă o cale locală,
alegerea manuală rămâne disponibilă. Alegerea manuală nu este o asociere
persistentă pentru fiecare piesă. LRC-urile UTF-16 și toate convențiile extinse
ale programelor karaoke nu sunt suportate.

OK schimbă urmărirea automată/manuală, iar sus/jos derulează. Urmărirea folosește
poziția serviciului muzical existent; nu deschide un al doilea player. Actualizarea
este la 250 ms numai pe pagina vizibilă, în timpul redării, altfel la o secundă.

### Oprire și reluare

Temporizatorul folosește un AlarmManager monotonic și oprește numai PlaybackService
Leo la 5/15/30/60 de minute. Poate fi anulat; oprirea serviciului anulează și
termenul. Nu oprește FM, apeluri, alt player ori telefonul. Pauza păstrează termenul;
începerea unei alte piese în același serviciu îl păstrează de asemenea.
Termenul nu se restaurează după reboot.

Bookmark-ul local păstrează cel mult 500 de surse și poziția, în maximum 1 MiB,
cu scriere atomică în worker. Coadă de scriere limitată: o operație activă și una
în așteptare, cu prioritate pentru starea cea mai nouă. Nu sunt scrieri periodice
pe firul interfeței. Actualizarea este la aproximativ 30 de secunde, la pauză,
seek finalizat, schimbări de coadă și oprire. O terminare forțată poate pierde
ultima porțiune de progres nesalvată. Shuffle/repeat folosesc preferințele existente.

„Reia ultima sesiune” este o acțiune explicită. Muzica nu pornește după boot.
Sursele sunt URI-uri locale, nu copii ale pieselor; un card absent, fișier șters,
reindexare sau grant expirat poate împiedica redarea. Bookmark-ul nu intră în Backup.

## Verificare și capturi

- **48 probe noi trecute pe aparat**, inclusiv contact real fictiv cu fotografie,
  T9, răspuns rapid și șablon din taste, redirecționare fără destinatar prestabilit,
  dimming local fără schimbări de sistem, cele trei colecții, moștenirea temei în
  demo InCall, LRC și validarea noilor date în backup.
- Probele media au folosit un WAV original cu **PCM zero**, fără sunet: player
  pregătit, seek/pauză, bookmark și reluarea poziției reale, final natural cu poziție resetată, LRC automat și oprirea
  efectivă a serviciului prin termen expirat. Broadcast-ul prea devreme este ignorat.
- Au trecut și **10 verificări Ceas/DST/catalog** și **9 verificări media/UI** existente.
- Datele de probă și preferințele sunt restaurate la final. Niciun apel, SMS,
  email sau transfer extern nu a fost inițiat.

Capturile `leo-comfort-*.png` au date fictive. Fotografia este desenată local;
versurile demonstrative sunt originale. Captura temei arată o previzualizare,
nu o schimbare permanentă a temei curente.

Nu s-a așteptat un termen real de 60 de minute și nu s-a repetat verificarea
pe fiecare rută audio/codecuri. Reboot-ul și repausul prelungit rămân scenarii
separate; aceste rezultate nu reprezintă o retestare completă a ROM-ului.

Recompilare: `build-visual-shell.sh`, apoi `build-six-feature-tests.sh`.
Runner: `ro.leo.visual.tests/ro.leo.visual.ComfortTests`.
