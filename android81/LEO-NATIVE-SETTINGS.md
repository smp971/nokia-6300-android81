# Pagini native pentru Setări Leo

Extinderea din 19 octombrie 2026 păstrează cele patru taburi și înlocuiește
redirecționările de bază cu pagini Android native, controlabile din D-pad.

## SIM-uri și rețea mobilă

Preferințele pentru apeluri, SMS și date sunt citite din Telecom și Subscription
Manager. Se pot alege contul de apeluri (inclusiv „Întreabă mereu”), SIM-ul pentru
SMS și SIM-ul pentru date. Schimbarea SIM-ului de date și comutarea datelor mobile
cer confirmare; schimbările sunt refuzate în timpul unui apel. Vizualizarea unui
meniu nu inițiază apeluri, SMS-uri sau probe de modem.

Detaliile fiecărui SIM arată operatorul, tehnologia de voce/date și roamingul,
fără număr de telefon, IMSI sau ICCID. APN și alegerea avansată a operatorului
folosesc încă paginile Android. Butoanele nu activează IMS și nu validează VoLTE
sau VoWiFi. La începutul pornirii se așteaptă înregistrarea SIM-urilor.

## Dată și oră

Ora și fusul automat se pot comuta separat. Data/ora manuală se editează în cinci
câmpuri numerice; valorile calendaristice invalide sunt respinse și aplicarea
cere confirmare. Intervalul 2000–2037 evită limita de timp a kernelului pe 32 de
biți. Editorul manual cere dezactivarea orei automate; alegerea fusului cere
dezactivarea fusului automat. Lista include București, Chișinău, câteva fusuri
europene și UTC. Formatul 12/24 ore folosește setarea Android.

Pagina urmărește schimbările de timp/fus și actualizarea minutului, păstrând
selecția. Ora automată depinde de serviciile de timp ale portului/operatorului;
la începutul boot-ului poate apărea data inițială a kernelului până la sincronizare.

## Localizare

Comutatorul folosește setarea Android, cu confirmare. Activarea din această pagină
alege modul cu furnizor de rețea pentru a permite scanarea Wi-Fi/Bluetooth pe Oreo.
Sunt afișați furnizorii accesibili aplicației. Acest control nu adaugă un driver
GPS și nu dovedește obținerea unei poziții; GPS rămâne neintegrat în port.

## Aplicații și permisiuni

Lista uzuală include aplicațiile utilizatorului și aplicațiile de sistem cu
lansator. Soft stânga comută lista completă. Detaliile native oferă lansare,
starea permisiunilor la execuție și informațiile pachetului. Dimensiunea afișată
este numai APK-ul, nu datele/cache-ul aplicației. Modificarea permisiunilor și
administrarea completă sunt delegate interfeței Android.

## Blocare automată

Setări → Siguranță → Blocarea tastelor permite blocarea imediată și activarea
blocării la stingerea ecranului. Opțiunea automată este inițial dezactivată.
Serviciul existent de accesibilitate observă ecranul, dezactivează cursorul din
taste la stingere și respectă ecranele de apel/alarmă. Deblocare: soft stânga,
apoi `*` în trei secunde. Este protecție contra apăsărilor accidentale, fără PIN.

Ecranul de blocare este desenat nativ, pe tot ecranul: ceas, dată în română,
baterie/încărcare și starea ambelor SIM-uri, pe tema sau imaginea HOME. Informațiile
se actualizează la evenimente și la schimbarea minutului; nu există animație
permanentă. La soft stânga apare indiciul „Apasă acum *”, care expiră după trei
secunde. Apăsările și clicurile accidentale nu deblochează ecranul.

## Probe pe Nokia 6300

| Probă | Rezultat |
| --- | --- |
| Cele cinci pagini native | Deschise după reboot, fără crash Java în jurnal |
| Drepturi pentru SIM, timp și setări protejate | Acordate aplicației de sistem după reboot |
| Preferințe apeluri/SMS pe SIM 1 | Salvare a valorii deja selectate; nu s-au inițiat apeluri sau SMS-uri |
| Schimbare efectivă pe SIM 2 / oprirea datelor | Neexecutate; conexiunea existentă a fost păstrată |
| Oră automată | Oprită și repornită din taste; valoarea inițială restaurată |
| Format 12/24 ore | Ambele salvate și citite din Android; formatul inițial restaurat |
| Ziua 32 în editorul manual | Respinsă cu mesaj, fără aplicarea datei |
| Aplicarea unei date valide / alt fus | Neexecutate pentru a păstra ceasul și alarmele utilizatorului |
| Localizare | Oprire și activare furnizor de rețea; configurația inițială GPS restaurată; fără probă GPS |
| Aplicații și permisiuni | Listă, detalii și starea permisiunilor deschise; administrarea Android deschide Detalii aplicație când selectorul direct nu este disponibil |
| Stingere + reaprindere cu blocare automată | Ecranul vizual apare și preferința de blocare este activă |
| Combinație expirată | `*` după peste trei secunde păstrează blocarea |
| Combinație validă | Soft stânga → `*` deblochează |
| Finalul probelor | Setările originale restaurate, blocare automată dezactivată; Wi-Fi conectat, 2/2 ping; Bluetooth ON; sistem read-only |

## Integrare

APK-ul păstrează identitatea și datele Leo Visual. Allowlist-ul aplicației de
sistem adaugă `MODIFY_PHONE_STATE`, `WRITE_SECURE_SETTINGS`, `SET_TIME` și
`SET_TIME_ZONE` alături de dreptul existent pentru hotspot. Funcțiile folosesc
serviciile Android; nu execută shell-uri root din aplicație. Instalarea bazei de
sistem se face prin `install-visual-system-settings.py`, apoi repornire pentru
drepturile noi. Nu se rescrie recovery-ul și nu se schimbă driverele.
