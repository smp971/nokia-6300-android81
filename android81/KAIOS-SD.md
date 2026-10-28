# KaiOS pe card, Android intern

Ținta este o alegere între **Leo Android intern** și **KaiOS cu system/data/cache
pe SD**. Pregătirea de stocare există pe PC; **KaiOS SD nu este încă bootabil**.
Nu a fost schimbat boot, recovery sau cardul fizic pentru această pregătire.

## Ce am verificat

| Element | Rezultat |
| --- | --- |
| Kernelul recovery-ului root/Leo Recovery | Identic byte cu byte cu cel din boot KaiOS original |
| SHA-256 kernel stock și recovery | `f860d012480be69e03bf57200cef9fbb5d5d2f1dd46e947e1989cd5dc44955fa` |
| Kernelul Leo Android actual | Diferă de cel stock; nu presupunem compatibilitatea KaiOS cu el |
| Loop devices, ext4 și VFAT | Activate în configurația kernelului stock |
| kexec | Dezactivat; aplicația recovery nu poate înlocui direct kernelul/init-ul activ |
| Montările KaiOS originale | Indică direct system/userdata/cache interne; trebuie redirecționate |
| Volumul SD gestionat de vold | Trebuie adaptat: cardul care ține OS-ul nu trebuie oferit spre formatare |

## Arhitectura propusă

Pornirea obișnuită păstrează Android din memoria internă. O opțiune viitoare din
Leo Recovery ar cere „KaiOS SD o singură dată”, ar salva cererea pe SD și ar
reporni în recovery. Un **selector PID 1, înainte de init-ul recovery**, ar consuma
cererea și ar porni ramdiskul/init-ul KaiOS cu kernelul stock al recovery-ului.
Fără cerere sau fără card ar reveni în meniul Leo Recovery.

Acest selector **nu este încă implementat** și în meniul 0.1 nu există un buton
care să pretindă că pornește KaiOS SD. Pornirea unui al doilea `init` din aplicația
recovery nu este suficientă: KaiOS are nevoie de init ca PID 1 și de mediul său
de boot, proprietăți și SELinux.

## Stocarea deja pregătită

`output/kaios-sd-preparation/LeoKaiOS/` conține:

| Fișier | Conținut |
| --- | --- |
| `system.img` | System KaiOS original, 800 MiB, nemodificat și verificat SHA-256 |
| `data.img` | Ext4 nou, gol, 512 MiB; fără date personale copiate |
| `cache.img` | Ext4 nou, gol, 64 MiB |
| `owner` | Marker de identificare pentru acest exemplar |

Dimensiunea totală a imaginilor este **1.376 MiB**. Sunt fișiere pe PC;
constructorul nu partiționează și nu formatează un card. Fiecare imagine nouă
a trecut `e2fsck -f -n`. Manifestul are explicit `bootable=false` și
`selector_implemented=false`.

`ramdisk-plan/` păstrează ramdiskul original comprimat și propunerile pentru:

- system → loop0, read-only și fără replay de jurnal;
- data → loop1, fără opțiuni de formatare sau criptare automată;
- cache → loop2, inclusiv montările directe din init;
- eliminarea volumului SD din configurația automată de formatare vold.

Aceste fișiere sunt un **plan**, nu o imagine boot/recovery instalabilă. Nu le
scrie peste partițiile Android. Comanda de pregătire offline este:

```powershell
python android81/prepare-kaios-sd.py --output android81/output/ALTA_PREGATIRE_KAIOS_SD
```

## Ce rămâne înaintea primei porniri

1. Selectorul timpuriu și cererea de boot consumată o singură dată.
2. Atașarea loop-urilor și păstrarea montării cardului când init-ul stock recreează `/dev`.
3. Auditarea tuturor montărilor/scripturilor KaiOS pentru a elimina revenirea la
   system/data/cache interne, inclusiv la eroare sau filesystem invalid.
4. Tratarea modului de boot recovery, a proprietăților și a volumelor vold.
5. Probă pe telefon, cu revenire în Leo Recovery dacă mediul SD nu este pregătit.

System și datele aplicațiilor pot fi separate pe SD, dar modemul, SIM-urile,
calibrarea, persist și NV aparțin aceluiași telefon; nu sunt două dispozitive
radio izolate. La prima probă trebuie urmărit și ce schimbări persistente face
KaiOS în aceste servicii comune. Performanța depinde de card și poate fi sub
cea a KaiOS instalat în memoria internă.
