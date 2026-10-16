# Nokia 6300 4G TA-1286 — experimental Android 8.1 bring-up

Repository local pentru sursele, configurarile si uneltele dezvoltate in acest
proiect. Dump-urile, datele private, cheile ADB, kernelul precompilat, imaginile
generate si rapoartele brute raman local, in afara Git.

- `android81/device/nokia/leo`: produs AOSP, initializare, tastatura si componente native.
- `android81`: scripturi de compilare, patch-uri AOSP reproductibile, impachetare si diagnostic.
- `analysis`: unelte de analiza a dump-ului.
- `kaios31`: sursele investigatiei donatorului KaiOS.

Starea experimentelor si rezultatele verificarilor sunt in `android81/STATUS.md`.
Instructiunile de lucru sunt in `android81/README.md`. Arborele AOSP 8.1 este
separat, in WSL: `/root/android81-leo/src`, android-8.1.0_r81. Modificarile sale
locale se aplica prin scripturile din acest repository; nu este copiat aici.

Istoricul incepe cu starea existenta la initializarea Git. Experimentele vechi
nu au commit-uri retroactive; documentatia pastreaza rezultatele lor.
Modificarile viitoare se salveaza in commit-uri distincte, cu verificarea efectuata
si limitarile cunoscute. Nu este configurat niciun remote si nimic nu este publicat.

Acesta este un prototip de diagnostic, nu un ROM pregatit pentru utilizare zilnica.
Scripturile de flash valideaza tinta; consultati sursele inainte de executare.
