# Leo Keypad — multi-tap și T9 local

19 octombrie 2026. IME Android 8.1 pentru tastatura fizică a Nokia 6300 TA-1286.
Componenta rămâne `ro.leo.keypad/.KeypadIme`; actualizarea păstrează selecția IME.

## Comenzi

| Mod / tastă | Acțiune |
| --- | --- |
| # | abc → ABC → 123 → T9 → T9 ABC; modul este păstrat între editoare |
| abc / ABC | Apăsări repetate pe 2–9 pentru literă; confirmare după 850 ms |
| T9 / T9 ABC | O apăsare pentru fiecare literă; dicționar local românesc de bază |
| * în T9, cuvânt în curs | Sugestia următoare |
| * în abc | Simboluri prin apăsări repetate |
| 0 | Acceptă compoziția și adaugă spațiu |
| OK | Acceptă compoziția; dacă nu există, acțiunea editorului sau rând nou |
| Soft dreapta fizic / Delete | Șterge ultima cifră a predicției, litera în compoziție sau caracterul anterior |
| Săgeți / schimbare câmp | Confirmă compoziția înainte de mutarea cursorului |
| Câmp telefon / numeric | Taste numerice obișnuite, indiferent de modul text |
| Parolă | Fără T9; # selectează doar abc/ABC/123 |

Sunt incluse diacriticele românești în multi-tap. Ștergerea din afara compoziției
folosește puncte de cod Unicode, cu fallback pentru perechi surrogate.

## Dicționar și limite

`PredictiveDictionary.java` conține o listă proprie de cuvinte uzuale, cu eliminarea
duplicatelor și potrivire numerică fără diferențierea diacriticelor. Sunt afișate
maximum 12 sugestii, preferând potrivirile complete înaintea completărilor de prefix.
Compoziția este limitată la 24 de cifre. Cuvintele lipsă se scriu în modul abc;
o secvență fără potrivire rămâne numerică.

Nu sunt preluate motorul Kika/IMEConnect sau dicționarele proprietare din KaiOS.
Nu există permisiune INTERNET, învățare automată, salvarea cuvintelor introduse
sau jurnalizare a textului. Este păstrată numai preferința de mod.

## Verificări

- 424 verificări JVM pentru coduri, diacritice, potriviri, prefixe, limite și fallback.
- Zece probe pe editorul Android real: salut, ștergere în predicție, OK, majuscule,
  schimbare sugestie cu *, fallback necunoscut, mod numeric, multi-tap, parolă și câmp telefon.
- Probele vechi au verificat fizic diacritice, majuscule și ștergere multi-tap.

Editorul de probă `ro.leo.keypad/.EditorTestActivity` nu are acțiuni de trimitere
sau salvare. El compară textul cu rezultatul sintetic așteptat și jurnalizează numai
identificatorul probei și booleanul passed; nu conținutul câmpului. Nu garantează
compatibilitatea tuturor aplicațiilor sau tuturor tipurilor de parolă/PIN.

## Reproducere

În WSL kaios: `bash android81/build-keypad-ime.sh`, respectiv
`bash android81/test-app-suite.sh`. Pe Windows: `python android81/install-leo-app-suite.py`
și `python android81/verify-keypad-prediction.py` cu serialul 695d conectat.
Instalatorul păstrează APK-urile anterioare verificate local și datele aplicațiilor.

[Restul suitei și limitele hardware](LEO-APP-SUITE.md).
