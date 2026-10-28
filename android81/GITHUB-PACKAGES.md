# Pachete private · 28 octombrie 2026

[Descarcă din GitHub Releases](https://github.com/smp971/nokia-6300-android81/releases/tag/leo-0.14.0-dev-20261028).
Este un **prerelease experimental pentru Nokia 6300 4G TA-1286 / exemplarul 695d**.
Repository-ul și pachetele rămân private. Nu este un ROM universal pentru seria Nokia.

| Asset | Utilizare și validare |
| --- | --- |
| `leo-fresh-0.14.0-dev-20261027-232615.zip` | Instalare curată din Leo Recovery; system/userdata/boot scrise și verificate, prima pornire confirmată. Șterge datele interne. Necesită corecția de mai jos. |
| `leo-recovery-0.2-private.zip` | Imagine Leo Recovery, instalator ADB și documentație. Boot, taste și instalarea nativă de pe SD validate. |
| `leo-root-recovery-695d.zip` | Intrare prin ADB root din KaiOS cunoscut; recovery-adbshell și instalator. Nu este un ZIP OTA semnat. |
| `kaios-original-restore-695d.zip` | Originalele boot/system pentru revenire. Hash-uri verificate; restaurarea completă prin kit rămâne netestată. |
| `leo-qfil-recovery-695d-20261028.zip` | Candidat QPST/QFIL: loader local și numai partiția recovery. Validat offline, fără probă de flash EDL. |
| `leo-first-setup.sh` | Corecția bootstrap post-instalare pentru accesibilitate, meniu global/mouse, WRITE_SETTINGS și launcherele vechi. |
| `SHA256SUMS.txt` | Hash-urile tuturor asset-urilor de mai sus. |

## Instalarea Leo și corecția necesară

ROM-ul din release este **exact ZIP-ul testat**, cu SHA-256:

```text
5f65e7e7b0c8ce0ce8b24cf5ff9617e3d078a2a76172c4f4df7628063bdd9478
```

Proba a identificat permisiuni speciale lipsă și revenirea launcherelor factory
după wipe. Pe telefon au fost corectate. După instalarea acestui ZIP se aplică
`leo-first-setup.sh` conform [procedurii post-instalare](FLASH-FROM-STOCK.md#corecție-post-instalare-identificată-în-probă).
Scriptul selectează Leo Visual și dezactivează Launcher3/Leo Shell vechi.
APK-ul shell-ului vechi există încă în acest ZIP; eliminarea lui efectivă pe
telefon și în constructorul următor este documentată separat. Nu a fost înlocuită
arhiva testată cu un build nou neprobat.

La prima configurare trebuie ales SIM-ul pentru date și refăcute preferințele
rețelei/Wi-Fi. LTE/IMS după wipe nu au fost reverificate. USB/ADB poate necesita
deconectarea cablului timp de 10 secunde și reconectare.

[Instalare din recovery, backup și limite](LEO-RECOVERY.md) ·
[Procedură din KaiOS și revenire](FLASH-FROM-STOCK.md).

## QPST / QFIL

Kitul separat scrie numai p33 recovery, cu GPT intact și memoria eMMC cunoscută.
Include loaderul identic celui din folderul local `nokia-6300-qfil-safe`, pentru
care există note despre sesiuni instabile și probleme la citire. Nu a fost
executată o probă QFIL în această etapă. Nu repară GPT/bootchain și nu conține
modem/NV/persist. Vezi [procedura și limitele QFIL](QFIL-RECOVERY.md).

## Verificarea descărcărilor

Calculează SHA-256 și compară cu `SHA256SUMS.txt`, înainte de extragere:

```powershell
Get-FileHash -Algorithm SHA256 .\leo-recovery-0.2-private.zip
```

Imaginile brute și arhivele sunt atașamente Release, separate de istoricul Git.
Pentru recompilare sunt disponibile sursele, constructorii și testele; bloburile
private și mediul WSL/AOSP rămân necesare.
