# Instalare Android pe memoria interna

Tinta fixa: Nokia 6300 4G TA-1286, serial ADB `695d`. Utilizatorul a autorizat
inlocuirea boot/system si formatarea datelor. Aceasta NU mai este proba reversibila
prin simpla repornire in KaiOS: restaurarea necesita imaginile de backup.

## Copii verificate pe PC

`backup-live/kaios-before-internal/` contine copiile citite din telefon si manifestul
`checksums.json`, excluse din Git. Backup boot SHA256:
`a23fcbcc993a0f5f1f03736eb59d7e431604d9c2d8900f100b927c06a1c81338`.
Backup system SHA256:
`a16d027dba2702fc9114ef3269770c30595a8e9b595329e5b7527588345fbdd6`.
Imaginile din directorul initial `partitions` au alte hash-uri: pentru aceasta
restaurare se folosesc copiile live, nu se presupune ca dump-ul public este identic.
Copiile cu sufix `.invalid` sunt capturi binare corupte de transportul shell si
NU sunt backup-uri; imaginile `.img` finale sunt verificate prin hash.

`userdata` nu este salvat de backup-kaios-internal.py. Utilizatorul a declarat ca
are backup si a cerut explicit formatarea. Aplicatiile/datele KaiOS se vor sterge.

## Construire

- Compilati leo_framework_mount pentru produsul aosp_leo_go-eng.
- Compilati shellul cu build-shell.sh.
- Rulati package-framework-runtime.sh cu `LEO_INTERNAL_PACKAGE=1`.
- Rulati `python android81/build-init-diagnostic-image.py --internal` pe Windows.

Boot pastreaza kernelul, campurile de versiune si cmdline ale boot-ului stock.
Ramdiskul pastreaza early root ADB, valideaza partitiile si monteaza system RO,
userdata RW direct. Fara loop images pe SD. Keymaster si Leo Shell sunt incluse
in system; setarile de test se aplica dupa prima pornire. zRAM este in RAM.
Recovery ramane imaginea de diagnostic SD testata anterior.

## Instalare controlata

`install-android-internal.py` fara argumente verifica backup-urile, identitatea
dispozitivului, dimensiunile si hash-urile si pregateste fisierele pe SD.
`--install --format-userdata` autorizeaza explicit operatiile scriptului:
scrie system, verifica SHA256, formateaza numai p39 userdata, scrie boot ultimul,
verifica SHA256 boot si confirma recovery nemodificat. Nu reporneste automat.
Starea fiecarei faze este salvata local in output/internal-install.json.

Nu formateaza si nu scrie recovery, modem, modemst, fsg, EFS sau alte partitii.
Un esec opreste scriptul fara repornire; investigati prin ADB inainte de reluare.

## Limite

Mutarea pe eMMC elimina accesul la imaginile SD pentru sistem/date; nu adauga
automat drivere radio, Wi-Fi sau GPU. SwiftShader ramane compozitorul software.
Shellul are bara soft-key proprie si lasa vizibila bara de stare SystemUI Android.
Ora/data reflecta ceasul telefonului, care poate necesita sincronizare.

Instalarea curenta a primit ulterior corectia gralloc.default din
patch-leo-gralloc.py, prin install-gralloc-fix.py. Biblioteca originala a fost
salvata local; copia noua evita alocarile HW_FB fizice pentru puntea software.
Boot a fost actualizat separat cu update-internal-boot.py pentru eliminarea
animatiei de pornire. Aceste operatii nu formateaza din nou datele si nu ating
recovery. Hash-ul imaginii system initiale nu mai este hash-ul partitiei dupa
inlocuirea atomica a bibliotecii; consultati gralloc-fix-install.json local.
