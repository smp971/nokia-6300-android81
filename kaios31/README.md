# KaiOS 3.1 / Nokia 2780 donor -> Nokia 6300

Audit si prima proba hardware, 15 octombrie 2026. Nu exista inca o imagine portata sau o pornire KaiOS 3 pe Nokia 6300.

## Rezultat verificat

| Caracteristica | Nokia 6300 real / dump | Donator Nokia 2780 |
|---|---|---|
| Executabile principale | ELF32 ARM | ELF32 ARM |
| Gecko | 48.0a2 | 84.0.3, build 20220923194456 |
| Baza Gonk/Android | 6.0.1 / API23 | 10 / API29 |
| Platforma declarata | msm8909 | msm8937 in vendor/build.prop |
| Binder | protocol 7, testat prin ioctl pe telefon | structuri protocol 8, confirmate prin dezasamblare |
| Ecran aparat testat | ST7789VX2 Yihua SPI | driverele donatorului nu au fost testate pe 6300 |

Aceeasi arhitectura CPU nu inseamna acelasi ABI al serviciilor, driverelor si bibliotecilor.

## Materiale descarcate si provenance

- Imagine comunitara system-patched.img.xz, release `2026100801`:
  https://github.com/gogogoghost/nokia-2780-flip-jailbreak-tutorial/releases/tag/2026100801
- SHA-256 arhiva: c3878a28fe73f88620a6d0a46395fa08392e4794b95230cd501a7c49a29aaf97,
  verificat fata de digest-ul publicat de GitHub si dimensiunea asset-ului.
- Imaginea este MODIFICATA de comunitate. Hash-ul confirma asset-ul descarcat, nu certifica
  firmware-ul ca original sau adecvat pentru alte dispozitive. Nu s-au executat scripturile donatorului.
- Arhiva publica emmc, release `emmc`, utilizata pentru extragerea vendor:
  https://github.com/gogogoghost/nokia-2780-flip-jailbreak-tutorial/releases/tag/emmc
  Asset-ul nu publica un digest; SHA-256 calculat local si tabela GPT sunt in vendor-analysis.json.
  Au fost extrase numai metadatele GPT si vendor, nu partitiile NV/identitate/userdata.

Imaginile decomprimate si fisierele extrase sunt in WSL, /root/kaios31-leo.
Arhivele, metadata de release si rapoartele sunt in acest director.

## Spatiu

- system.img descarcat si decomprimat: 834666496 bytes = 796 MiB.
- Partitia system a Nokia 6300: 838860800 bytes = 800 MiB.
- Imaginea de sistem SINGURA incape geometric, cu o marja de 4 MiB.
- Sistemul donator este system-as-root si foloseste o partitie vendor separata.
- vendor.img extras: 408944640 bytes = 390 MiB. Nokia 6300 nu are partitie vendor dedicata in tabela verificata.
- Blocuri ext4 alocate aproximativ: system 564797440 + vendor 343425024 = 908222464 bytes,
  aproximativ 866 MiB. Nu incape o combinare integrala simpla in system de 800 MiB.

Aceste cifre nu estimeaza exact o imagine reconstruita: includ overhead ext4 si nu includ
toate efectele unui repack. Vendor-ul 2780 oricum nu poate fi folosit integral pentru hardware-ul 6300.
Nu s-a modificat tabela de partitii.

## Dependinte si ABI

- libxul.so din 2780 are 44 de dependinte DT_NEEDED absente nominal din sistemul stock 6300.
- Cu system + vendor + bibliotecile APEX ale donatorului, s-a urmarit o inchidere de 188 biblioteci
  fara nume DT_NEEDED lipsa. Aceasta NU verifica toate simbolurile, versiunile lor, namespace-urile
  linkerului, comportamentul serviciilor sau dependintele incarcate prin dlopen.
- libc noua ofera simboluri cerute de Gecko si absente in libc stock, de exemplu __fread_chk,
  __fwrite_chk si __write_chk. Exista diferente si in libgui, libui, libbinder si libutils.
- Gecko nou cere interfete HIDL pentru composer, radio, GNSS, Wi-Fi etc., plus serviciile gonkservices.
  Copierea bibliotecilor de interfata nu furnizeaza implementarile hardware compatibile.
- Bibliotecile vendor sunt pentru platforma declarata msm8937, inclusiv gralloc/hwcomposer/audio/camera;
  Nokia 6300 are implementarile vechi msm8909. Acestea cer integrare/adaptoare, nu redenumire.
- Android 10 foloseste libc/libm/libdl prin APEX; legaturile absolute au fost rezolvate offline
  catre payload-urile APEX extrase, nu catre fisierele hostului.

Detaliile masurate sunt in donor-analysis.json si abi-comparison.json.

## Proba pe telefon: Binder

A fost compilat un ELF ARM static de 1616 bytes din binder_probe.S folosind binutils ARM.
Utilitarul a fost copiat temporar in /data/local/tmp/leo-binder-probe si executat pe serialul
explicit al Nokia 6300, nu pe Nokia 8110 conectat simultan.

Operatii: open(/dev/binder), ioctl(BINDER_VERSION), close si afisare. Nu trimite tranzactii
Binder si nu inregistreaza servicii. Rezultat: `Binder protocol 7 (32-bit IPC)`.
Utilitarul temporar a fost eliminat dupa test. Rezultatul este in binder-probe-result.txt.

In donor/binder-talkWithDriver.txt, libbinder construieste ioctl 0xc0306201 la
0x3b020/0x3b02c si il transmite apelului ioctl prin PLT 0x5c060 (relocare ioctl@LIBC).
Acesta este BINDER_WRITE_READ cu structura de 48 bytes, protocolul cu campuri pe 64 biti.
Configuratia kernelului stock contine CONFIG_ANDROID_BINDER_IPC_32BIT=y, concordant cu testul.

kernel-android10.fragment documenteaza schimbarea candidata pentru un kernel experimental
separat. NU a fost aplicata, compilata sau flash-uita. Schimbarea rupe compatibilitatea
cu clientii stock Binder protocol 7; nu se poate aplica pur si simplu peste KaiOS 2.5.

## Ce urmeaza tehnic

1. Kernel experimental Nokia, pastrand suportul ST7789VX2 si keypad, adaptat ABI-ului nou Binder.
2. Adaptarea pornirii Android 10/system-as-root/APEX si a asezarii vendor pentru tabela 6300.
3. Alegerea unei cai grafice: HAL compatibil cu noul userspace sau adaptor pentru hardware-ul vechi.
4. Prima pornire pana la ADB si loguri; apoi Gecko cu o pagina de proba; apoi interfata KaiOS.
5. Audio si modem separat, inclusiv apeluri/SMS/VoLTE si suspendare/autonomie.

Concluzie: donator util si compatibil la nivel CPU, dar nu un upgrade direct. Sunt confirmate
blocante concrete la Binder, ABI/biblioteci, HAL-uri si asezarea partitiilor. Nu avem inca
dovada ca portarea completa este realizabila cu sursele si componentele disponibile.

## Reproducere

In WSL kaios, prin python3:
- inspect_donor.py: verifica arhiva system, decomprima, extrage cu debugfs si inventariaza ELF.
- extract_vendor.py: verifica dimensiunea arhivei emmc, citeste GPT si extrage numai vendor.
- compare_abi.py: comparatie statica de biblioteci/simboluri; nu executa binarele donatorului.
- build-probe.sh: construieste utilitarul Binder din sursa proprie. Nu il instaleaza sau executa automat.

Sincronizarea Android 8.1 anterioara a fost oprita intentionat pentru aceasta evaluare.
Fisierele descarcate au fost pastrate; nu ruleaza o compilare Android in fundal.
