# Leo · aplicațiile zilnice în stil Material

21 octombrie 2026 · Nokia 6300 4G TA-1286 / Android 8.1.

| Zonă | Funcții adăugate |
| --- | --- |
| Muzică | Coperți reprezentative în listele de albume/artiști și piese, favorite persistente, căutare text/T9 după titlu, artist și album. Opțiunile pieselor gestionează favoritele și listele. |
| Galerie | Albume pe folderele MediaStore, filtrare fără mutarea fișierelor, șase miniaturi mai mari per pagină cu decupare proporțională, numele imaginilor, dimensiuni originale, mărime și dată când sunt disponibile. |
| Contacte și Mesaje | Avataruri colorate și fotografii în lista contactelor; bule mai rotunjite și aerisite, avatar în metadatele mesajului și culori diferite pentru SIM 1 și SIM 2, păstrând etichetele text. |
| Blocare | Carduri pentru vremea din cache și următoarea alarmă; copertă și comenzi 4/5/6 pentru muzică, comenzi similare pentru FM activ. Activarea cardurilor se controlează în Personalizare → Aspect Home. |
| Calendar | Marcaje pentru zilele cu evenimente, agenda următoarelor 90 de zile, repetare zilnică/săptămânală/lunară și notificare la început sau cu 5/10/30/60 minute înainte. |
| Aplicații Android | Favorite afișate primele, ascundere reversibilă a intrărilor, filtru doar favorite, informațiile aplicației și acces la configurarea implicitelor Android unde există. |

## Încărcare și stocare

Miniaturile rândurilor sunt cerute în fundal numai pentru rândurile afișate.
Cache-ul păstrează cel mult 24 de imagini decodate la maximum 64 px, iar o eroare
nu produce reîncercări continue. Actualizarea rândurilor nu reconstruiește antetul.
Lipsa unei coperți produce un simbol/monogramă; nu toate fișierele au imagini de album.
Biblioteca și Galeria păstrează limita de 500 de intrări a indexului local.

Favoritele muzicale sunt referințe la URI-uri MediaStore: ștergerea ori reindexarea
fișierelor poate invalida referințele. Aplicațiile ascunse rămân instalate și pot fi
afișate din nou din Opțiuni. Nu se schimbă automat aplicația implicită și nu se
șterg ori dezactivează pachete. Paginile implicite sunt pagini Android externe și
compatibilitatea lor cu tastele rămâne cea a sistemului.

## Calendar și notificări

Evenimentele rămân în calendarul local Leo din CalendarProvider. Pentru o serie,
editarea și ștergerea afectează întreaga serie; excepțiile pentru o singură apariție,
sincronizarea cu un cont și o dată de sfârșit a repetării nu au interfață proprie.
Repetarea este stocată ca RRULE, cu DURATION pentru evenimentele recurente.
Afișarea folosește Instances, astfel încât aparițiile repetate intră în agenda zilei.

Evenimentul și notificarea lui sunt salvate într-un lot al furnizorului. Leo
programează un singur AlarmManager pentru cea mai apropiată notificare viitoare,
fără un serviciu care verifică periodic ora. Căutarea este limitată la 366 de zile,
2000 de apariții și 10000 de reguli de notificare. Reprogramarea se face după
salvare/ștergere, la boot, actualizarea aplicației și schimbarea orei/fusului.
Notificările al căror moment a trecut nu se declanșează retroactiv la pornire. Titlul notificării
este privat pe ecranul Android blocat. Ecranul Leo rămâne un blocaj de taste,
fără a deveni o protecție PIN a datelor.

Ora și autonomia notificării trebuie evaluate și după repornire/repaus prelungit;
probele acestei etape verifică stocarea, extinderea recurenței și programarea
următoarei notificări, fără a declanșa deliberat un sunet ori a cere probe de apel.

## Verificări și recompilare

`build-visual-shell.sh` compilează sursele; `build-six-feature-tests.sh` construiește
APK-ul de probe. Runner nou: `ro.leo.visual.tests/ro.leo.visual.DailyPolishTests`.
Probele folosesc fișiere, mesaje și evenimente fictive. Se elimină fixture-urile
și se restaurează preferințele modificate. Capturile publicate folosesc date demo.

`install-native-phone.py --no-open` actualizează aplicația și verifică hash-ul,
fără a forța deschiderea Telefonului la final. Baza privilegiată se sincronizează
prin instalatorul existent; nu se scriu imagini boot/recovery sau drivere în această etapă.

Backup-ul existent al preferințelor include noile opțiuni de carduri la blocare.
Favoritele muzicale, lista aplicațiilor ascunse/favorite și Calendar Leo au fost
adăugate ulterior în [Backup v2, cu restaurare pe categorii](LEO-DAILY-EXTRAS.md);
arhiva separată de contacte/SMS rămâne disponibilă.

Rezultat pe aparat: **25 de verificări noi trecute**, plus **8 probe UI/din taste**
și **9 probe media/player** din etapele anterioare: 42 verificări locale în această
etapă. Sunt confirmate recurența săptămânală extinsă de CalendarProvider, salvarea
notificării și programarea următorului AlarmManager, favorite/T9, coperți fără
întinderea imaginii, albume pe foldere, filtrele aplicațiilor și deblocarea din taste.
Coperta de la blocare din captură este un model de serviciu local, fără sesiune audio.
Ecranul este menținut aprins doar în ferestrele probelor; setarea de repaus nu este
modificată. Verificarea sonoră a notificării Calendar și funcționarea după reboot
ori repaus lung nu sunt confirmate de această suită.
