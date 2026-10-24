# Rețea mobilă · blocaj de pornire și recuperare

24 octombrie 2026 · Nokia 6300 4G TA-1286, Leo 0.5.

## Ce s-a observat

După o nouă pornire, ambele SIM-uri erau `READY`, modemul Qualcomm era `ONLINE`,
iar procesele RIL rulau. Totuși, registrul Android indica `POWER_OFF` pe ambele
sloturi. Modul avion era 0, `cell_on` era 1, iar framework-ul dorea alimentarea
radioului pe ambele sloturi. Cererile inițiale off/on din RIL au răspuns cu
întârzieri mari și telefonul nu s-a înregistrat.

Comutarea modului avion nu a recuperat înregistrarea. Repornirea doar a serviciilor
`leo_rild1` și `leo_rild2` a recuperat rețeaua, fără reboot și fără modificarea
firmware-ului modemului, NV/persist, APN-urilor sau SIM-ului de date.
Aceasta localizează blocajul în inițializarea/legătura radio; cauza internă exactă
din driver nu este demonstrată și nu atribuim problema schimbărilor UI.

## Starea confirmată după recuperare

| Verificare | Rezultat |
| --- | --- |
| SIM 1 | Vodafone RO, voce `IN_SERVICE`, date `IN_SERVICE`, LTE |
| SIM 2 | Orange, voce `IN_SERVICE`; datele nu sunt active pe acest slot |
| Conexiune de date SIM 1 | Android raportează `mDataConnectionState=2` |
| Servicii | Ambele RIL, rmt_storage și per_mgr rulează |
| Verificator | `verify-radio-runtime.py` trece pe ambele sloturi |

Nu s-au inițiat apeluri/SMS și nu s-a repetat o probă HTTP pentru această reparație.
Conexiunea ADB s-a întrerupt după repornirea RIL și a revenit prin reconectarea USB.

## Protecția instalată

`radio-runtime/recover.sh` și regula init separată pornesc un observator limitat
după `sys.boot_completed`. El poate reporni RIL **o singură dată pe boot** dacă:

- ambele SIM-uri sunt `READY`;
- niciun slot nu este deja înregistrat;
- ambele sloturi au rămas `POWER_OFF`, cu ambele apeluri idle;
- modul avion este dezactivat și `cell_on=1`;
- framework-ul raportează dorința de pornire pe ambele sloturi;
- starea este observată în trei verificări consecutive, după minimum 150 s de uptime.

Starea este recitită înaintea restartului. Înregistrarea pe cel puțin un slot,
modul avion sau radioul oprit intenționat anulează recuperarea. Lipsa semnalului
cu `OUT_OF_SERVICE`, un SIM absent, un apel sau rezultate incomplete nu determină
restart. Observatorul are maximum 30 de iterații, cu pauze de 10 s și timeout-uri
pentru interogări; nu este un serviciu de monitorizare permanentă.

Instalarea schimbă numai două fișiere text din system, cu backup local și verificare
SHA-256. System a fost readus read-only. Biblioteca RIL, boot și recovery sunt păstrate.

## Ce este verificat și ce rămâne

- 12 fixture-uri sanitizate, rulate pe telefon prin `--classify`, au trecut fără
  a emite comenzi radio. Acoperă recuperarea eligibilă și cazurile de excludere.
- `--check` pe telefonul recuperat a raportat `HEALTHY`.
- Rularea normală a scriptului pe rețeaua sănătoasă a ieșit cu `HEALTHY`, fără restart.
- **Regula init a pornit la reboot și a raportat `HEALTHY`, fără restart RIL.**
  Recuperarea automată a unui nou blocaj nu este încă probată; nu am întrerupt
  rețeaua pentru a provoca artificial eșecul. Pornirea radio mai devreme și
  timpii măsurați sunt documentați în [LEO-STARTUP-CHROME.md](LEO-STARTUP-CHROME.md).

```powershell
# Citește numai clasificarea stării curente:
python -X utf8 android81/framework-command.py /system/bin/sh /system/vendor/leo-radio/recover.sh --check

# Instalarea separată, explicită, a protecției:
python -X utf8 android81/install-radio-recovery.py --install
```

Instalatorul complet `install-radio-runtime.py` include acum și aceste două
fișiere pentru instalările viitoare. Nu se rerulează peste runtime-ul existent;
pentru acesta se folosește instalatorul separat de mai sus.
