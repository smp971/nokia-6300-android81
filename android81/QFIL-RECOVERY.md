# Leo Recovery prin QPST / QFIL · candidat experimental

Kit privat pentru exemplarul **Nokia 6300 4G TA-1286 / ADB 695d**, cu GPT-ul
original intact. Este o cale de reinstalare a recovery-ului când Android nu mai
pornește. Nu este un kit de reparare a GPT-ului sau a bootloaderului.

**Validat offline; flash-ul QFIL și acceptarea loaderului în EDL nu sunt probate.**
Imaginea Leo Recovery inclusă a pornit pe telefon și a instalat ROM-ul prin SD.
Acest rezultat nu validează transportul Sahara/Firehose sau aplicația QFIL.

## Conținut și țintă

| Fișier | Rol |
| --- | --- |
| `8k.mbn` | Loader ARM32 MSM8909 local, cu SHA-256 fixat; acceptare EDL neconfirmată |
| `leo-recovery.img` | Leo Recovery 0.2, 33.554.432 octeți |
| `rawprogram0.xml` | O singură scriere: recovery, p33, LBA 2488416, 65536 sectoare de 512 octeți |
| `patch0.xml` | Gol; fără modificări GPT |
| `manifest.json`, `SHA256SUMS.txt` | Proveniență, geometrie, dimensiuni și hash-uri |

Nu sunt incluse ținte pentru GPT, bootchain, modem, NV, persist, system, boot sau
userdata. Kitul nu șterge datele. QFIL nu verifică automat serialul ADB 695d:
modelul și geometria trebuie confirmate înaintea folosirii pe telefon.

Loaderul este identic SHA-256 cu cel din folderul local `nokia-6300-qfil-safe`.
Documentația acelui kit mai vechi notează citiri defectuoase și sesiuni instabile;
nu este dovadă de flash QFIL reușit. XML-ul nu cere citire automată prin acest
loader. După programare, hash-ul partiției recovery trebuie verificat separat
prin ADB root, iar orice eroare de scriere trebuie investigată, nu ignorată.

## Folosire când vom valida QFIL

1. Extrage ZIP-ul într-un folder separat și verifică SHA256SUMS.
2. Telefonul trebuie să expună **Qualcomm 9008 / EDL**. Modul 900E de crash nu
   confirmă că este pregătit pentru programare. Păstrează driverul Qualcomm
   potrivit QFIL; nu este necesară schimbarea driverului telefonului în modul Leo.
3. În QFIL, folosește Flat Build, memoria eMMC și loaderul inclus `8k.mbn`.
   Încarcă exclusiv `rawprogram0.xml` și `patch0.xml` din acest kit.
   **Erase All Before Download trebuie să fie dezactivat.**
4. Revizuiește ținta: **numai recovery**. Dacă loaderul este respins sau nu
   răspunde, păstrează jurnalul; compatibilitatea nu este demonstrată de existența
   fișierului și nu alegem un loader pentru alt chipset la întâmplare.
5. După scriere și verificare, pornește Leo Recovery cu combinația fizică
   deja folosită pe acest exemplar. Apoi poți instala pachetul Leo aprobat de pe
   SD; acea operație separată șterge datele și cere backup și confirmare.

Modemul/NV nu sunt restaurate de acest kit. O defecțiune în GPT, bootchain sau
hardware necesită un diagnostic separat. Nu se promite recuperarea oricărui brick.

## Reconstruire pe PC, fără telefon

```powershell
python android81/build-qfil-recovery.py
```

Constructorul verifică CRC-urile GPT, pozițiile celor patru partiții OS cunoscute,
hash-ul loaderului și hash-ul recovery-ului aprobat. XML-ul este generat doar
pentru recovery și patch-ul este gol. Un ZIP existent nu este suprascris.

Interfața generală Flat Build / Browse / Load XML este descrisă și în
[documentația producătorului Quectel](https://developer.quectel.com/doc/sbc/Quectel-Pi-H1/en/OS/Debian/system_flash.html).
Acea pagină privește o placă UFS diferită: configurația noastră eMMC și ținta
recovery provin din GPT-ul Nokia verificat, nu din imaginile acelei plăci.
