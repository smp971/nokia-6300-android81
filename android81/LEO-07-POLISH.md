# Leo 0.7 · telefon, mesaje, navigare și backup

26 octombrie 2026 · versiune compilată `0.7.0-dev / cod 7`.

**Instalarea finală și probele pe Nokia sunt în așteptarea reconectării USB.**
O versiune intermediară a aplicației a trecut 10 probe de file și 22 de contracte.
Nu prezenta aceste rezultate drept o validare completă a ultimului APK.

## Cele șase direcții

| Direcție | Implementare | Stare și limite |
| --- | --- | --- |
| Telefon | Căutare după nume/număr, SIM 1/SIM 2, doar pierdute, resetarea filtrelor și acces din meniul Telefon la pierdute | Filtrele se combină; rezultatele au indicator. Citirea se anulează la schimbarea paginii și folosește cache pentru contacte. Filtrarea nu șterge apeluri din jurnal. |
| VoLTE | Recuperarea lipsei persistente de voce IMS, cu LTE prezent | Controlerul nou este instalat. Regulile au trecut 11 verificări; serviciul a fost verificat cu voce IMS și date LTE. Declanșarea automată pe un blocaj real rămâne de urmărit. |
| Mesaje | Selecție de text din taste și marcaje de stare în bule | Căutarea în conversație și copierea integrală existente se păstrează. Selecția numără caractere Unicode și păstrează emoji întregi. `✓✓` = livrat, `✓` = trimis, `!` = eșec, `…` = așteptare. Textul explicativ și SIM-ul rămân vizibile. |
| Interfață | Componentă comună de file pentru Telefon/Setări; reguli de focalizare și revenire în conținut | Filele primesc focus și în modul tactil al mouse-ului. Stânga/dreapta păstrează cursorul în editor; Jos intră în conținut. Ceasul și Galeria păstrează accesul din taste după mouse. Proba clicului unic este adăugată pentru versiunea finală. |
| Cameră | Zoom din opțiuni, confirmarea expunerii acceptate și previzualizare opțională după o fotografie | HAL-ul raportează 78 de trepte de zoom și a acceptat modificarea/restaurarea parametrilor de zoom și expunere. Deschiderea previzualizării a trecut; revenirea directă cu Înapoi a necesitat o corecție, încă de retestat. Nu confirmă calitatea optică a zoom-ului sau expunerii. |
| Backup | Format 3: alarme, preferințe Cameră/SMS și limbă, alături de teme, fundal, ordine, favorite, notițe, dicționar și calendar | Compatibil cu formatele 1/2. Categorii separate pentru alarme și preferințe. Validare înainte de scriere, copie de siguranță și jurnal de revenire. Probele intermediare au folosit preferințe izolate, fără restaurarea datelor utilizatorului. |

## Folosire

- Telefon → Istoric → Opțiuni: căutare, SIM, pierdute și resetare.
- Mesaje → conversație → Opțiuni → Selectează text: pozițiile încep cu 1,
  capătul este inclusiv; previzualizează înainte de copiere.
- File: Sus din număr ajunge la file; stânga/dreapta schimbă pagina; Jos revine
  în conținut. Favoritele Telefon și editarea numărului păstrează săgețile proprii.
- Cameră → Opțiuni → Zoom / Expunere / Previzualizare după foto. Previzualizarea
  se aplică unei fotografii, fără să întrerupă rafala. Blițul și temporizatorul se păstrează.
- Backup Leo → Ce include backup-ul / Creează backup / Restaurează din fișier.

## Limitele recuperării IMS

`ImsRecoveryPolicy` așteaptă minimum trei minute de înregistrare fără voce,
cere LTE pe abonamentul selectat și refuză intervenția când Telecom raportează
un apel. Sunt permise maximum două încercări pe pornire, la cel puțin 30 de minute
distanță. Contorul se păstrează dacă procesul controlerului repornește. La
recuperare se folosește deregistrarea și repornirea runtime-ului IMS deja testată;
nu se repornește RIL/modemul și nu se modifică modul avion.

Nu este o soluție pentru toate defecțiunile operatorului sau pentru VoWiFi.
Regulile se verifică offline cu `test-ims-recovery.sh`.

## Backup și protejarea datelor

Maximum 32 de definiții de alarme; ID-uri unice și ore/zile validate. Restaurarea
categoriei replanifică alarmele active și anulează programările vechilor definiții.
O categorie de alarme nebifată sau absentă dintr-un backup vechi nu este reaplicată.
Temporizatoarele active și amânările temporare nu sunt exportate ca definiții.

Nu se includ conturi/parole Email, date modem/NV, fotografii sau muzică. Contactele
și SMS-urile au fluxul separat de backup selectiv. Fișierul de backup este personal
și nu se publică în GitHub. Hash-ul detectează deteriorarea; fișierul nu este criptat.

## Probe rămase

Instalarea APK-urilor finale, `PhoneTabsTests`, `SixPolishTests` și proba completă
`CameraControlsTests` după corecția revenirii. GPS, HFP cu microfon și SMTP real
rămân amânate la alegerea utilizatorului. Nu sunt necesare noi apeluri/SMS pentru
probele locale ale acestei versiuni.
