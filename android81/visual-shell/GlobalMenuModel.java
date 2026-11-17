package ro.leo.visual;

import java.util.Arrays;
import ro.leo.i18n.L;

/** Stable actions shared by the global menu and its tests. */
final class GlobalMenuModel {
    static final int HOME=0, APPS=1, NOTIFICATIONS=2, QUICK=3, SETTINGS=4, POINTER=5,
        MORE=6, MEDIA=7, RECENT=8, LOCK=9, POWER=10, HELP=11, WORKSPACE=12,
        TRANSFERS=13, STUDIO=14, ANDROID=15, CLOSE=16, CUSTOMIZE=17;
    static final String PREFERENCES = "home-menu";
    static final String ORDER_KEY = "global-menu-order";
    private static final int[] DEFAULT_PRIMARY = {HOME, APPS, NOTIFICATIONS, QUICK, SETTINGS, POINTER};
    private static final int[] COMMANDS = {HOME, APPS, NOTIFICATIONS, QUICK, SETTINGS, POINTER,
        MEDIA, RECENT, LOCK, POWER, HELP, TRANSFERS, WORKSPACE, STUDIO, ANDROID, CLOSE};
    static final int MAX_PRIMARY = DEFAULT_PRIMARY.length;

    static int[] primary(String saved) {
        if (saved == null) return DEFAULT_PRIMARY.clone();
        int[] pins = new int[MAX_PRIMARY];
        int length = 0;
        for (String value : saved.split(",")) {
            int id;
            try {
                id = Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (!contains(COMMANDS, COMMANDS.length, id) || contains(pins, length, id)) continue;
            pins[length++] = id;
            if (length == MAX_PRIMARY) break;
        }
        return length == 0 ? DEFAULT_PRIMARY.clone() : Arrays.copyOf(pins, length);
    }

    static String encode(int[] pins) {
        StringBuilder value = new StringBuilder();
        for (int id : pins) {
            if (value.length() > 0) value.append(',');
            value.append(id);
        }
        return value.toString();
    }

    static int[] actions(boolean tools) {
        return actions(DEFAULT_PRIMARY, tools);
    }

    static int[] actions(int[] pins, boolean tools) {
        int[] commands = tools ? available(pins) : pins;
        int[] result = Arrays.copyOf(commands, commands.length + 1);
        result[commands.length] = tools ? CUSTOMIZE : MORE;
        return result;
    }

    static int[] available(int[] pins) {
        int[] available = new int[COMMANDS.length - pins.length];
        int at = 0;
        for (int id : COMMANDS) if (!contains(pins, pins.length, id)) available[at++] = id;
        return available;
    }

    private static boolean contains(int[] values, int length, int target) {
        for (int i = 0; i < length; i++) if (values[i] == target) return true;
        return false;
    }

    static String[] labels(boolean tools, boolean pointer) {
        return labels(actions(tools), pointer);
    }

    static String[] labels(int[] actions, boolean pointer) {
        String[] rows = new String[actions.length];
        for (int i = 0; i < actions.length; i++) rows[i] = label(actions[i], pointer);
        return rows;
    }

    static String label(int id, boolean pointer) {
        switch (id) {
            case HOME: return L.t("Acasă");
            case APPS: return L.t("Aplicații");
            case NOTIFICATIONS: return L.t("Notificări");
            case QUICK: return L.t("Configurări rapide");
            case SETTINGS: return L.t("Setări");
            case POINTER: return pointer ? L.t("Oprește mouse-ul") : L.t("Mouse din taste");
            case MORE: return L.t("Mai multe");
            case MEDIA: return L.t("Acum se redă");
            case RECENT: return L.t("Aplicații recente");
            case LOCK: return L.t("Blochează tastele");
            case POWER: return L.t("Pornire și oprire");
            case HELP: return ContextHelp.label();
            case WORKSPACE: return L.t("Spațiu de lucru");
            case TRANSFERS: return L.t("Centrul de transferuri");
            case STUDIO: return "Studio";
            case ANDROID: return L.t("Android din taste");
            case CLOSE: return L.t("Închide aplicația curentă");
            case CUSTOMIZE: return L.t("Personalizează meniul");
            default: throw new IllegalArgumentException("Unknown menu action");
        }
    }
}
