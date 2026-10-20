# Investigație FM pe Nokia 6300

20 octombrie 2026. Rezultat: driver prezent, recepție încă neintegrată.

## Probe pasive

Un utilitar ARM propriu, cu timeout de trei secunde, deschide `/dev/radio0` și citește numai capabilități, tuner și frecvență. Nu execută setări V4L2, inițializare de firmware sau comenzi audio.

```text
driver=radio-iris card=QTI FM Radio Transceiver version=264688 capabilities=0x80250000
G_TUNER: Invalid argument
frequency_raw=0
```

`version` este câmpul raportat de driver, nu o versiune de firmware validată. Rezultatul nu demonstrează recepție. Modulele `radio_iris` și `radio_iris_transport` sunt prezente. `G_TUNER` rămâne EINVAL și după specificarea tipului RADIO; nu avem încă o inițializare activă validată.

## Inițializarea originală

Dump-ul KaiOS conține `init.qcom.fm.sh` și `fm_qsoc_patches`. Calea normală originală scrie `1` în parametrul `radio_iris_transport/fmsmd_set`, apoi rulează `fm_qsoc_patches 199217 0`, actualizând `hw.fm.init` numai la succes.

Nu am executat această secvență. Utilitarul original depinde de `libqmi.so`, `libqcci_legacy.so`, `libqmiservices.so`, pe lângă libcutils/libc++/libdl/libc/libm. Cele trei biblioteci QMI nu sunt disponibile în directoarele de biblioteci Android verificate. Nu este suficient să copiem aplicația web KaiOS: ea cere API-ul Gecko `mozFMRadio`, absent aici.

## Pași necesari

1. Analiza dependențelor QMI și inițializare izolată, fără înlocuirea bibliotecilor de sistem Android.
2. Validarea alimentării, acordării și opririi, cu timeout și mecanism de revenire independent de ADB.
3. Ruta FM prin placa Qualcomm, căști și control de volum.
4. Abia după recepție reală: interfață Leo pentru frecvențe și favorite.

Diagnosticul Leo arată separat prezența dispozitivului FM și detectarea căștilor. Nu afișează un comutator fictiv de radio.

## Recompilare utilitar

```sh
bash android81/build-fm-diagnostic.sh
```

Binarul generat este `android81/output/leo-fm-diagnostic`. Se execută în namespace-ul Android prin `framework-command.py`, nu direct în namespace-ul adbd de recovery. Sursele sunt în `fm-runtime/`; utilitarele proprietare din dump nu sunt incluse în Git.
