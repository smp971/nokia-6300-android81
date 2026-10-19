# Leo — interfață Android pentru telefon cu taste

Stare: 19 octombrie 2026. Platformă experimentală: Nokia 6300 4G TA-1286,
Android 8.1. Scop: utilizare cotidiană din taste, cu o interfață coerentă,
inspirată de comportamentul KaiOS și construită peste serviciile Android.

## Componente și verificare

| Componentă | Stare demonstrată | Limită / următoarea verificare |
| --- | --- | --- |
| Leo Visual HOME | Selectat implicit; grilă și listă native | Nu toate aplicațiile externe acceptă navigare completă din taste |
| Aplicații Android | Listă alfabetică cu iconuri; 12 intrări detectate pe telefon; lansare Calculator și revenire verificate | Lista include activități lansabile, nu servicii; fiecare aplicație necesită probe proprii |
| Aspect | Listă/grilă și culoarea fundalului se salvează | Persistența listei verificată după restartul procesului, fără reboot nou |
| Notificări și meniu global | Acces din Home și tasta Meniu dedicată | Adaptarea conținutului notificărilor rămâne separată |
| Mouse din taste | Deplasare și clic verificate | Compatibilitatea cu toate aplicațiile / derularea nu este complet validată |
| Leo Multi-tap | Litere, diacritice, moduri și ștergere; confirmare fizică de la utilizator | Nu include predicție T9; trebuie extinse testele în aplicații reale |
| IME implicit | Selectat pe telefon, configurat la pornire și inclus în împachetarea internă | Hook-ul nou nu a fost verificat printr-un reboot suplimentar |
| Apel primit | Ecran complet, răspuns/închidere din taste, audio în ambele sensuri | Nu echivalează cu validarea tuturor serviciilor de telefonie |
| Audio media | AudioTrack și OGG au fost auzite, inclusiv după reboot | Acoperirea tuturor rutelor audio nu este completă |
| Dual SIM / LTE | Starea ambelor SIM-uri în Home; LTE verificat anterior | VoLTE/VoWiFi nu sunt validate prin apel real |

Pentru hardware și istoricul problemelor: [README](../README.md) și [STATUS](STATUS.md).
Acest tabel nu înlocuiește testele hardware curente.

## Ce mai putem adapta din KaiOS

Inspecția locală a arhivelor a identificat următoarele repere. Sunt surse de
analiză a comportamentului, nu componente deja portate:

| Reper din dump | Utilitate pentru Leo | Abordare |
| --- | --- | --- |
| Launcher: `_appList--grid.scss`, `_appList--list.scss`, `_appList--single.scss` | Organizare aplicații | Grila și lista sunt implementate nativ; modul cu o singură aplicație rămâne opțional |
| System: `shared/js/softkey_panel.js`, `softkey_panel_helper.js` | Etichete contextuale pentru taste | Convenții comune pentru Home, dialoguri și aplicațiile proprii |
| `custom_dialog.js`, `system_dialog.js`, `option_menu.scss` | Confirmări și meniuri accesibile | Dialoguri Android controlabile integral din D-pad și softkeys |
| `notices_dialog.scss`, `notification_helper.js` | Prezentare notificări | Păstrăm serviciul Android; adaptăm doar prezentarea și comenzile |
| `simcard.scss`, `sound_manager.js` | Stare SIM și profiluri audio | UI peste API-urile Android și rutele hardware deja disponibile |
| `gaia-theme.css` | Culori semantice pentru fundal, text, selecție și borduri | Temă proprie cu contrast bun; fără redistribuirea resurselor vendor |
| `keypad_t9.js`, `imeconnect_sdk.js`, dicționare Kika | Comportamentul predicției | Investigație separată a motorului și licențelor; multi-tap rămâne baza funcțională |

Aplicațiile KaiOS folosesc API-uri Gecko privilegiate. Copierea arhivelor în Android
nu le face funcționale. Nu avem încă o aplicație KaiOS originală demonstrată pe port.
Inventarul reproductibil este în [KAIOS-UI-INVENTORY](KAIOS-UI-INVENTORY.md).

## Etape și criterii de acceptare

1. **Bază coerentă:** HOME și IME implicite, listă/grilă, preferințe salvate.
   Implementat; urmează confirmarea hook-ului IME la un reboot planificat.
2. **Componente UI comune:** antet, focus vizibil, listă, dialog și bară softkey.
   Prima componentă este implementată: meniul Opțiuni folosește `KeypadDialog`,
   cu selecție și bară de acțiuni explicite. Meniul global de accesibilitate
   folosește aceeași componentă. Dialogurile informative urmează să fie
   uniformizate; etapa nu este încă încheiată.
   Acceptare: deschidere, selecție, confirmare, anulare și revenire exclusiv din taste,
   fără focus ascuns sau pierderea selecției la întoarcere.
3. **Aplicații esențiale:** Contacte și Mesaje adaptate pentru tastatură, apoi Ceas,
   Fișiere și profiluri audio. Acceptare: scenarii reale documentate individual;
   trimiterea mesajelor/apelurile de probă se fac doar cu instrucțiunea utilizatorului.
   Copia locală a aplicațiilor KaiOS este verificată; primul ecran propriu este Ceas
   (oră și cronometru). Matricea completă și limitele sunt în
   [KAIOS-APP-PORTS.md](KAIOS-APP-PORTS.md).
   Calculator și Notițe Leo sunt compilate; motorul aritmetic are 29 de cazuri
   validate pe JVM. Instalarea și probele fizice așteaptă reconectarea telefonului.
4. **Predicție T9:** alegerea unui motor redistribuibil, integrare în IME și teste
   românești. Acceptare: candidați, selecție, corectare și revenire sigură la multi-tap.
5. **Stabilitate și performanță:** măsurători pe aceleași ecrane, pornire repetabilă,
   verificarea consumului și a erorilor. Nu declarăm fluiditate doar după aspect.

## Reguli vizuale și de proiect

- Ecran 240×320: antet scurt, conținut lizibil, bară inferioară permanentă cu
  acțiunile soft stânga / OK / soft dreapta, unde aplicația proprie permite.
- Focus unic, contrast puternic și selecție vizibilă; nicio comandă esențială doar tactilă.
- Soft dreapta revine; în editor poate șterge, cu etichetă explicită. Roșu păstrează
  politica de apel Android; tasta Meniu dedicată deschide meniul global.
- Shell nativ, fără motor web obligatoriu pentru navigarea de bază.
- Surse, scripturi, documentație și capturi curate în Git; dump-uri, date personale,
  dicționare și binare proprietare rămân în afara repository-ului.
- Fiecare funcție are o stare: implementată, verificată automat, confirmată pe telefon
  sau nevalidată. Un demo nu este echivalent cu o funcție hardware completă.

![Listă de aplicații Leo](screenshots/visual-shell-list.png)
