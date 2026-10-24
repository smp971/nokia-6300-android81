# Leo 0.6 · conectivitate și aplicații

24 octombrie 2026 · Nokia 6300 4G TA-1286 · Android 8.1 / API 27.

## Probe reale

| Funcție | Rezultat | Limite |
| --- | --- | --- |
| SIM 2, apel VoLTE primit | Confirmat anterior, voce în ambele sensuri | SIM selectat pentru date/IMS |
| SIM 2, apel VoLTE de ieșire | Confirmat de utilizator; monitorizarea a păstrat LTE și datele conectate | Nu înseamnă IMS simultan pe ambele SIM-uri |
| SIM 2, SMS trimis | Livrare confirmată de operator și utilizator | Numărul de test rămâne local |
| SIM 2, SMS primit | Răspuns recepționat în baza Leo, asociat abonamentului SIM 2 | Conținutul mesajelor nu se publică |
| DTMF în apel | Rutarea tastelor există în Leo InCall | Tonurile nu au fost verificate la un serviciu telefonic |
| Apel Bluetooth HFP | Alegere profil, conectare/deconectare și stare în Leo | Proba cu cască/microfon a fost amânată de utilizator |
| Email cu atașamente | Selecție din Fișiere Leo, maximum cinci fișiere de câte 20 MiB | Confirmarea și trimiterea rămân în clientul Email Android; SMTP real amânat |
| MMS | Pagină de diagnostic în SIM-uri și date | Clientul MMS Leo nu este implementat; configurația Orange examinată nu conține un APN MMS |

Starea VoLTE afișată în Leo verifică slotul selectat, înregistrarea și capacitatea
de voce raportate prin IMS, plus existența Binder-ului IMS. Prezența LTE singură
nu este prezentată drept VoLTE. [Inițializarea IMS](IMS-RUNTIME.md).

## Cameră

Meniul camerei adaugă temporizator de 3/5/10 secunde, rafală de 3/5 fotografii,
reglarea expunerii numai dacă HAL-ul o declară și o pagină pentru capabilități video.
Ieșirea din cameră anulează declanșarea întârziată și eliberează dispozitivul.
Fișierele JPEG se păstrează în `Pictures/Leo`.

Înregistrarea video nu este activată prin simpla existență a unor dimensiuni
declarate de HAL. Trebuie verificate separat fluxul senzorului, encoderul,
sunetul și salvarea fișierului. [Portarea BF20A1](CAMERA-RUNTIME.md).

## GPS

Aplicația GPS Leo afișează poziția, precizia, viteza, altitudinea, sateliții și
vechimea ultimei poziții. Solicită localizarea numai după pornirea explicită din
Opțiuni și o oprește la ieșire. Maximum 30 de locuri favorite; distribuirea este
explicită. Nu generează poziții simulate și nu încarcă automat coordonate.

Sunt construite serviciul GNSS HIDL 1.0 din AOSP și adaptorul pentru HAL-ul GPS
KaiOS. Bibliotecile originale sunt private în `/vendor/leo-gps`; nu se înlocuiesc
global `libc` sau `libutils`. Încărcarea HAL-ului și înregistrarea serviciului au
trecut proba izolată. Poziționarea prin sateliți necesită verificare pe telefon;
încărcarea bibliotecii nu confirmă o poziție GPS.

## Actualizare

Ambele aplicații Leo folosesc versiunea **0.6.0-dev / cod 6**. Sursele includ
controale instrumentate pentru contractele Email, vârsta poziției GPS, ID-urile
aplicațiilor și proba explicită a rafalei/anulării temporizatorului.

Pachetul local cu runtime-uri este descris în [RUNTIME-UPDATE.md](RUNTIME-UPDATE.md).
Datele SIM, conturile Email, mesajele și fotografiile nu intră în pachet.
