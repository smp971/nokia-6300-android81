package ro.leo.visual;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.SparseArray;
import ro.leo.i18n.L;

/** Reads connection labels while Settings is open. Never scans or changes connections. */
final class SettingsNetworkState {
    private static final int HID_HOST = 4;
    private static final int[] PROFILES = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET, HID_HOST};
    private final Context context;
    private final Runnable changed;
    private final BluetoothAdapter bluetooth;
    private final SparseArray<BluetoothProfile> proxies = new SparseArray<>();
    private BluetoothProfile.ServiceListener listener;
    private boolean watching;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { changed.run(); }
    };

    SettingsNetworkState(Context context, Runnable changed) {
        this.context = context;
        this.changed = changed;
        bluetooth = BluetoothAdapter.getDefaultAdapter();
    }

    void start() {
        if (watching) return;
        IntentFilter filter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
        filter.addAction("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED");
        context.registerReceiver(updates, filter);
        watching = true;
        if (bluetooth == null) return;
        listener = new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int id, BluetoothProfile profile) {
                if (listener != this) {
                    bluetooth.closeProfileProxy(id, profile);
                    return;
                }
                proxies.put(id, profile);
                changed.run();
            }

            @Override public void onServiceDisconnected(int id) {
                if (listener != this) return;
                // The same proxy may bind again when Bluetooth turns back on.
                changed.run();
            }
        };
        for (int id : PROFILES) {
            try { bluetooth.getProfileProxy(context, listener, id); }
            catch (RuntimeException ignored) {}
        }
    }

    void stop() {
        if (!watching) return;
        context.unregisterReceiver(updates);
        watching = false;
        listener = null;
        for (int i = 0; i < proxies.size(); i++) {
            bluetooth.closeProfileProxy(proxies.keyAt(i), proxies.valueAt(i));
        }
        proxies.clear();
    }

    String wifi() {
        try {
            WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (manager == null) return L.t("Indisponibil");
            int state = manager.getWifiState();
            if (state == WifiManager.WIFI_STATE_DISABLED) return L.t("Oprit");
            if (state == WifiManager.WIFI_STATE_ENABLING) return L.t("Se pornește…");
            if (state == WifiManager.WIFI_STATE_DISABLING) return L.t("Se oprește…");
            if (state != WifiManager.WIFI_STATE_ENABLED) return L.t("Indisponibil");
            WifiInfo info = manager.getConnectionInfo();
            ConnectivityManager connections = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo network = connections == null ? null : connections.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            boolean connected = network != null ? network.isConnected()
                : info != null && info.getSupplicantState() == SupplicantState.COMPLETED;
            if (!connected) return L.t("Neconectat");
            String name = cleanName(info == null ? null : info.getSSID());
            if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
                name = name.substring(1, name.length() - 1).trim();
            }
            return name.isEmpty() || "<unknown ssid>".equalsIgnoreCase(name) ? L.t("Conectat") : compact(name, 22);
        } catch (RuntimeException unavailable) {
            return L.t("Indisponibil");
        }
    }

    String bluetooth() {
        if (bluetooth == null) return L.t("Indisponibil");
        try {
            int state = bluetooth.getState();
            if (state == BluetoothAdapter.STATE_OFF) return L.t("Oprit");
            if (state == BluetoothAdapter.STATE_TURNING_ON) return L.t("Se pornește…");
            if (state == BluetoothAdapter.STATE_TURNING_OFF) return L.t("Se oprește…");
            if (state != BluetoothAdapter.STATE_ON) return L.t("Indisponibil");
        } catch (RuntimeException unavailable) {
            return L.t("Indisponibil");
        }
        boolean connected = false;
        for (int id : PROFILES) {
            try {
                BluetoothProfile proxy = proxies.get(id);
                if (proxy != null) {
                    for (BluetoothDevice device : proxy.getConnectedDevices()) {
                        connected = true;
                        String name = cleanName(device.getName());
                        if (!name.isEmpty()) return compact(name, 17);
                    }
                }
                connected |= bluetooth.getProfileConnectionState(id) == BluetoothProfile.STATE_CONNECTED;
            } catch (RuntimeException ignored) {}
        }
        return connected ? L.t("Conectat") : L.t("Neconectat");
    }

    String dataSim() {
        try {
            int id = SubscriptionManager.getDefaultDataSubscriptionId();
            if (id < 0) return L.t("Neales");
            SubscriptionInfo info = SubscriptionManager.from(context).getActiveSubscriptionInfo(id);
            if (info == null || info.getSimSlotIndex() < 0) return L.t("Se inițializează");
            return "SIM " + (info.getSimSlotIndex() + 1);
        } catch (RuntimeException unavailable) {
            return L.t("Indisponibil");
        }
    }

    private static String cleanName(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
    }

    private static String compact(String value, int length) {
        if (value.codePointCount(0, value.length()) <= length) return value;
        return value.substring(0, value.offsetByCodePoints(0, length - 1)) + "…";
    }
}
