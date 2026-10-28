# KaiOS de pe SD · Leo în memoria internă

Leo Recovery 0.2 include un selector timpuriu și opțiunea **KaiOS from SD**.
Imaginile au fost copiate și verificate pe cardul telefonului. **Prima pornire
a ajuns la meniul KaiOS, confirmat de utilizator.** Integrarea rămâne experimentală:
serviciile KaiOS nu au fost reverificate complet. ADB root funcționează, dar se
deconectează după pornire și a necesitat reconectarea fizică USB.

## Cum este construit

Pornirea normală folosește în continuare partiția boot Leo, nemodificată.
Din recovery: **KaiOS from SD → Verify SD images / Boot KaiOS once**.
Pornirea cere confirmare, creează o cerere pe SD și repornește în recovery.

Înaintea init-ului recovery, selectorul rulează ca PID 1:

1. Verifică exemplarul Nokia și montează cardul extern real `179:65`.
2. Consumă cererea `LeoKaiOS/boot-once`, înaintea încercării de boot.
3. Verifică markerul, dimensiunile și semnăturile ext4; calculează din nou
   SHA-256 pentru întregul system KaiOS original.
4. Atașează system/data/cache la loop0/1/2. System are loop read-only.
5. Protejează la scriere blocurile interne boot/system/cache/userdata pentru
   durata acestei porniri. Protecția dispare la următoarea repornire a kernelului.
6. Mută montarea SD sub `/leo-sd` în rădăcina KaiOS inclusă în ramdisk și
   execută init-ul stock, păstrând PID 1. Nu lansează un al doilea init ca aplicație.

Țintele interne protejate sunt boot `179:29`, system `179:30`, cache `259:0`
și userdata `259:7`; fiecare adresă este comparată cu sysfs înainte de ioctl.

Fără cerere validă, fără card sau la eroare înaintea execuției init-ului KaiOS,
selectorul revine la recovery. Init-ul recovery original este restaurat **în RAM**
înaintea execuției; imaginea din partiție nu este modificată în acest proces.
După ce init-ul KaiOS a preluat controlul, o eroare a serviciilor/UI nu mai poate
fi tratată de acest selector ca o eroare de pregătire: poate fi necesară repornirea.

Kernelul recovery este identic cu cel KaiOS original:
`f860d012480be69e03bf57200cef9fbb5d5d2f1dd46e947e1989cd5dc44955fa`.
Nu folosim kexec, care este dezactivat în acest kernel.

## Fișierele de pe card

| Fișier în `LeoKaiOS/` | Conținut |
| --- | --- |
| `system.img` | System KaiOS original, 800 MiB, verificat și deschis read-only |
| `data.img` | Ext4 de 512 MiB, inițial gol; datele KaiOS se păstrează aici |
| `cache.img` | Ext4 de 64 MiB |
| `owner` | Markerul exact pentru exemplarul de test |
| `boot-once` | Cerere temporară, consumată înainte de pornire |
| `selector-last.log` | Confirmarea pregătirii loop-urilor și protecțiilor |

Sunt necesari **1.376 MiB** pentru imagini, plus spațiul normal de lucru al
cardului. Nu scoate cardul cât timp KaiOS rulează. System rămâne nemodificat;
data/cache sunt intenționat modificabile și nu mai au hash-ul inițial după utilizare.

`prepare-kaios-sd.py` pregătește imaginile pe PC. `deploy-kaios-sd.py --install
--confirm-target 695d` le copiază comprimat în recovery, verifică fiecare fișier
și refuză să înlocuiască un director `LeoKaiOS` existent. Nu formatează cardul
și nu scrie partiții interne. Nu creează singur cererea de boot.

## Izolarea montărilor

Rădăcina KaiOS este derivată din boot-ul stock verificat și inclusă în recovery.
Constructorul redirecționează montările system/userdata/cache, inclusiv montările
directe din init și din modul charger. Elimină formatarea automată și criptarea
footer din fstab și scoate cardul din lista volumelor gestionate de vold.
Daemonul OTA nu pornește automat. ADB este cerut în configurația inițială.
Pentru diagnosticul ADB, imaginea instalată păstrează domeniul serviciului `adbd` și
adaugă numai `adbd` și `shell` în bitmap-ul permissive al politicii stock verificate.
ID-urile au fost citite cu libsepol pentru acest dump; regulile AV și celelalte
domenii nu sunt rescrise, iar SELinux global nu este dezactivat. Această corecție
a trecut proba pe telefon: UID 0 și SELinux global enforcing. Prima eliminare
a argumentului `su` nu a fost suficientă.

Modemul, SIM-urile, firmware-ul, persist și NV sunt comune telefonului;
**aceasta nu este o izolare completă a serviciilor radio**. Stock KaiOS poate
modifica starea/configurația acestor servicii. Nu copiem datele personale din Leo.

## Verificări și limite

- Imaginile au fost copiate pe card și verificate integral prin SHA-256.
- Verificatorul nativ a acceptat imaginile pe telefon, fără atașarea loop-urilor.
- Recovery 0.2 a pornit normal și a rămas accesibil prin ADB.
- Backup-ul boot/system/recovery pe card a trecut recitirea și verificarea hash-urilor.
- Prima cerere SD nu a fost executată din cauza argumentelor suplimentare pentru
  init; selectorul a fost corectat pentru a decide după PID 1.
- Proba loop-urilor, a celor trei montări ext4, a mutării SD și a protecțiilor RO
  a trecut pe telefon, cu restaurarea protecțiilor după probă.
- Meniul KaiOS a fost confirmat vizual după boot. Imaginea curentă expune USB
  Qualcomm 9091 și ADB root. `b2g=running` și `sys.boot_completed=1`.
- Tabela montărilor a fost colectată din KaiOS: loop0/1/2 folosesc imaginile de
  pe `/leo-sd`; system este read-only, data/cache read-write. Toate cele patru
  blocuri interne protejate raportează read-only.
- Repornirea normală din KaiOS a revenit în Leo, confirmat vizual și prin ADB:
  markerul Android intern este prezent, iar protecțiile RO ale partițiilor s-au resetat.
- ADB s-a întrerupt după pornire; reconectarea fizică USB l-a restabilit fără
  reboot. Stabilitatea USB și funcțiile radio/media KaiOS rămân de verificat.
- Captura automată KaiOS nu a reușit: screencap nu găsește display-ul, iar
  citirea directă a framebuffer-ului este refuzată. Captura recovery existentă
  provine dintr-o probă anterioară; nu este prezentată ca o captură KaiOS.

Jurnalul selectorului, dacă revine în recovery, este `/leo-selector.log`.
Proba nativă `leo-boot-selector --probe-loops`, disponibilă doar în recovery root,
verifică atașarea loop-urilor, montările ext4 și mutarea SD, apoi demontează și
restaurează protecțiile. Montarea data/cache poate actualiza metadatele acestor
două imagini SD; system și filesystem-urile interne nu sunt scrise.
Pentru schema completă a recovery-ului vezi [Leo Recovery](LEO-RECOVERY.md).
