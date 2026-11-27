package ro.leo.visual;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.ref.WeakReference;
import ro.leo.i18n.L;

final class ContextHelp {
    static WeakReference<LeoActivity> current = new WeakReference<>(null);

    static String label() {
        return L.t("Info / Ajutor");
    }

    static void open(Context context, String page) {
        LeoActivity activity = context instanceof LeoActivity ? (LeoActivity) context : current.get();
        String controls = "";
        if (activity != null && (context == activity || activity.hasWindowFocus())) {
            controls = activity.helpControls();
            if (!(context instanceof Activity)) page = activity.getClass().getSimpleName();
        }
        Intent intent = new Intent(context, ContextHelpActivity.class)
            .putExtra("page", page).putExtra("controls", controls);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    static String text(String page) {
        if (page == null) page = "";
        if (page.contains("HomeEditor")) {
            return L.t("Săgețile mută elementul. * / # schimbă elementul selectat. OK deschide opțiunile de aspect. Soft stânga salvează.");
        }
        if (page.contains("QuickSettings")) return L.t("Săgețile aleg plăcuțele. OK schimbă Wi-Fi, Bluetooth sau localizarea și deschide alegerea profilului. Pe slidere, stânga/dreapta schimbă luminozitatea în pași de 5% și volumul media cu un nivel; OK alege nivelul exact. Opțiuni păstrează detaliile de rețea, SIM-urile și volumele separate. Hotspotul cere confirmare dacă trebuie oprit Wi-Fi-ul. Localizare activă nu înseamnă că o aplicație a obținut deja o poziție GPS.");
        if (page.contains("Companion")) return L.t("WebUI: activare permanentă, pornire automată, tema panoului web, parole, utilizatori și funcționalități. Adresa HTTPS apare în prima intrare când Wi-Fi este disponibil. Administratorii pot modifica setările; utilizatorii cu acces doar pentru citire pot consulta datele și schimba parola proprie. Parolele au minimum 8 caractere. Satelitul de pe Home indică un server activ.");
        if (page.contains("MainActivity")) {
            return L.t("Acasă: 0 deschide media curentă, 1 deschide căutarea, 2–9 lung deschid scurtăturile. Sus deschide configurările rapide, jos notificările. * schimbă cardul și # îl deschide. Pe cardul Favorite, 4 / 6 schimbă persoana și OK deschide contactul.")
                + "\n\n" + L.t("Aplicații: săgețile mută selecția, * / # schimbă pagina, 0 trece la pagina următoare. Tastele 1–9 aleg poziția de pe pagină. OK deschide aplicația.")
                + "\n\n" + L.t("Foldere: OK deschide folderul, Înapoi revine la meniul principal. Din Opțiuni poți muta aplicația în alt folder sau în meniul principal și poți alege meniul fără foldere.");
        }
        if (page.contains("Wallpaper")) {
            return L.t("Din Fundaluri incluse, stânga și dreapta schimbă imaginea. OK sau Aplică salvează fundalul pentru Acasă și ecranul blocat.");
        }
        if (page.contains("Calculator")) {
            return L.t("Cifrele introduc numere, săgețile aleg operația, * introduce punctul zecimal, # șterge expresia, iar OK calculează.");
        }
        if (page.contains("Notifications")) {
            return L.t("* afișează detaliile, 5 cere confirmarea eliminării, # deschide opțiunile listei, iar OK deschide notificarea.");
        }
        if (page.contains("Notes")) {
            return L.t("Soft stânga creează o notiță. OK deschide notița selectată, iar # deschide opțiunile sau căutarea când lista este goală.");
        }
        if (page.contains("Browser")) {
            return L.t("0: adresă. 2: caută în pagină. 8: următorul rezultat. 4 / 6: alege linkul. 5: deschide linkul. * / #: mărimea textului. 1 / 3: începutul sau sfârșitul paginii. Browserul afișează text HTML.");
        }
        if (page.contains("Clock") || page.contains("Alarm")) return HelpActivity.TEXT[2];
        if (page.contains("Recorder") || page.contains("Recording")) {
            return L.t("OK pornește sau pune în pauză. Opțiuni salvează, deschide înregistrările și alege calitatea pentru următoarea înregistrare. Închiderea paginii nu oprește o înregistrare activă; folosește Oprește și salvează.");
        }
        if (page.contains("Messages")) {
            return L.t("În conversație, 5 pregătește răspunsul. Opțiuni oferă căutare, selecție text, fixare, arhivare și filtrare. Trimiterea cere alegerea SIM-ului și confirmare. În editor, soft dreapta șterge.");
        }
        if (page.contains("Phone") || page.contains("InCall")) {
            return L.t("Sus din număr ajunge la file; stânga/dreapta schimbă fila, Jos revine în listă. Verde alege SIM-ul pentru apel. În apel: Opțiuni pentru microfon, ieșire audio, așteptare și conferință. Serviciile depind de Telecom și operator.");
        }
        if (page.contains("Bluetooth")) {
            return L.t("Selectează transferul cu OK. În așteptare poți accepta sau refuza primirea. Progresul și starea provin din stiva Bluetooth Android. Trimiterea începe numai după alegerea explicită a dispozitivului.");
        }
        if (page.contains("GlobalSearch")) {
            return L.t("Scrie termenul din taste; Jos intră în rezultate și OK deschide selecția. Căutarea este locală și limitată: SMS, calendar, fișiere, contacte, notițe, muzică și aplicații. Expeditorii filtrați nu apar în rezultate.");
        }
        if (page.contains("Calendar")) {
            return L.t("D-pad mută ziua, OK deschide ziua, iar * / # schimbă luna. În Opțiuni găsești agenda și poți crea un eveniment.");
        }
        if (page.contains("Files")) {
            return L.t("OK deschide folderul sau acțiunile fișierului. 5 marchează fișierele. Opțiuni oferă căutare, copiere și mutare. Partajarea prin Bluetooth cere alegerea dispozitivului și confirmare.");
        }
        if (page.contains("StreamRadio")) {
            return L.t("În player, OK sau 5 pornește ori oprește radioul. Stânga / dreapta sau 4 / 6 schimbă postul, iar sus / jos reglează volumul. # deschide lista de posturi. În listă, OK alege postul și Înapoi revine la player.");
        }
        if (page.contains("NowPlaying")) {
            return L.t("* schimbă sursa între Muzică și FM. # deschide playerul sursei. Pentru comenzile sursei curente, deschide Opțiuni → Info / Ajutor.");
        }
        if (page.contains("Music") || page.contains("Media") || page.contains("Lyrics")) {
            return HelpActivity.TEXT[4] + "\n\n" + L.t("În player, # deschide biblioteca.");
        }
        return L.t("Sus/jos mută selecția; listele se derulează circular. OK deschide elementul. Acțiunile softkey sunt scrise în bara de jos. În câmpurile de text, tastele numerice folosesc tastatura, iar soft dreapta șterge. Pentru o aplicație Android, meniul global oferă mouse din taste.");
    }
}
