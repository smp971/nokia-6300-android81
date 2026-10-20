package ro.leo.visual;

import android.bluetooth.*;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.wifi.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Oreo connectivity controls with explicit D-pad actions and real service state. */
public final class ConnectivityActivity extends LeoActivity {
    // Oreo's hidden WifiConfiguration.KeyMgmt.WPA2_PSK (SoftApManager mapping).
    private static final int AP_WPA2_PSK=4;
    private WifiManager wifi;
    private BluetoothAdapter bluetooth;
    private BluetoothProfile input,a2dp;
    private String kind;
    private String hotspotResult="";
    private boolean registered, discoveryOwned;
    private final List<BluetoothDevice> devices=new ArrayList<>();
    private final List<ScanResult> networks=new ArrayList<>();
    private final BroadcastReceiver updates=new BroadcastReceiver() {
        public void onReceive(Context context,Intent intent) {
            if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(device!=null && !devices.contains(device)) devices.add(device);
            }
            if(!form) show();
        }
    };
    private final BluetoothProfile.ServiceListener profile=new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int id,BluetoothProfile value) { if(id==BluetoothProfile.A2DP)a2dp=value;else input=value; if(!form) show(); }
        public void onServiceDisconnected(int id) { if(id==BluetoothProfile.A2DP)a2dp=null;else input=null; if(!form) show(); }
    };
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        kind=getIntent().getStringExtra("kind"); if(kind==null) kind="wifi";
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        bluetooth=BluetoothAdapter.getDefaultAdapter();
        if("bluetooth".equals(kind) && bluetooth!=null){bluetooth.getProfileProxy(this,profile,4);bluetooth.getProfileProxy(this,profile,BluetoothProfile.A2DP);}
    }
    protected void onResume() {
        super.onResume();
        IntentFilter filter=new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED");filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);filter.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        registerReceiver(updates,filter); registered=true; show();
    }
    protected void onPause() { if(registered) unregisterReceiver(updates); registered=false; super.onPause(); }
    protected void onDestroy() {
        if(bluetooth!=null) {
            if(discoveryOwned) bluetooth.cancelDiscovery();
            if(input!=null) bluetooth.closeProfileProxy(4,input);if(a2dp!=null)bluetooth.closeProfileProxy(BluetoothProfile.A2DP,a2dp);
        }
        super.onDestroy();
    }
    private int selection() {
        if(root!=null) for(int i=0;i<root.getChildCount();i++)
            if(root.getChildAt(i) instanceof ListView) return Math.max(0,((ListView)root.getChildAt(i)).getSelectedItemPosition());
        return 0;
    }
    private void show() {
        if(isFinishing() || isDestroyed()) return;
        try {
            if("bluetooth".equals(kind)) showBluetooth();
            else if("hotspot".equals(kind)) showHotspot();
            else showWifi();
        } catch(RuntimeException unavailable) { notice("Serviciul nu este disponibil"); }
    }
    private void androidPage(String action) {
        try { startActivity(new Intent(action)); } catch(RuntimeException failure) { notice("Pagina Android este indisponibilă"); }
    }
    private boolean locationReady() {
        if(checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},70); return false;
        }
        if(Settings.Secure.getInt(getContentResolver(),Settings.Secure.LOCATION_MODE,0)==0) {
            notice("Android 8 cere Localizare activă pentru scanare. Poți adăuga Wi-Fi manual.");
            startActivity(new Intent(this,PrivacySettingsActivity.class)); return false;
        }
        return true;
    }
    private void showWifi() {
        if(wifi==null) { notice("Wi-Fi indisponibil"); finish(); return; }
        int selected=selection();
        int state=wifi.getWifiState();
        String status=state==WifiManager.WIFI_STATE_ENABLED?"Pornit":state==WifiManager.WIFI_STATE_DISABLED?"Oprit":state==WifiManager.WIFI_STATE_ENABLING?"Se pornește…":state==WifiManager.WIFI_STATE_DISABLING?"Se oprește…":"Eroare";
        WifiInfo info=wifi.getConnectionInfo();
        String current=info!=null && info.getNetworkId()!=-1?info.getSSID().replace("\"",""):"Neconectat";
        final ArrayList<String> rows=new ArrayList<>(Arrays.asList("Wi-Fi · "+status,"Conexiune · "+current,"Caută rețele","Adaugă rețea","Setări Android"));
        networks.clear();
        if(wifi.isWifiEnabled()) {
            List<ScanResult> results=new ArrayList<>(wifi.getScanResults());
            Collections.sort(results,new Comparator<ScanResult>() { public int compare(ScanResult a,ScanResult b) { return Integer.compare(b.level,a.level); }});
            Set<String> seen=new HashSet<>();
            for(ScanResult scan:results) if(!scan.SSID.isEmpty() && seen.add(scan.SSID)) {
                networks.add(scan); rows.add(scan.SSID+" · "+(scan.capabilities.contains("PSK")?"WPA/WPA2":scan.capabilities.contains("WEP")||scan.capabilities.contains("EAP")?"Alte setări":"Deschisă"));
            }
        }
        list("Wi-Fi Leo",rows,selected,new Choice() { public void pick(int i) {
            if(i==0) { if(!wifi.isWifiEnabled() && apState()!=11) { notice("Oprește întâi hotspotul"); return; } if(!wifi.setWifiEnabled(!wifi.isWifiEnabled())) notice("Cererea Wi-Fi a fost refuzată"); }
            else if(i==1) notice("Starea conexiunii se actualizează automat");
            else if(i==2) { if(!wifi.isWifiEnabled()) notice("Pornește întâi Wi-Fi"); else if(locationReady() && !wifi.startScan()) notice("Scanarea nu a pornit"); }
            else if(i==3) networkForm("",false);
            else if(i==4) androidPage(Settings.ACTION_WIFI_SETTINGS);
            else if(i-5<networks.size()) {
                ScanResult scan=networks.get(i-5);
                if(scan.capabilities.contains("EAP")||scan.capabilities.contains("WEP")) { notice("Această securitate se configurează în Android"); androidPage(Settings.ACTION_WIFI_SETTINGS); }
                else networkForm(scan.SSID,scan.capabilities.contains("PSK"));
            }
        }},"Actualiz.",new Runnable(){ public void run(){show();}});
    }
    private static String quoted(String value) { return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\""; }
    private void networkForm(String name,boolean secured) {
        LinearLayout panel=fields("Conectare Wi-Fi");
        final EditText ssid=field(panel,"Nume rețea (SSID)",name,InputType.TYPE_CLASS_TEXT);
        final EditText password=field(panel,"Parolă · gol pentru deschisă","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        back=new Runnable(){public void run(){show();}};
        left=new Runnable(){public void run(){
            final String network=ssid.getText().toString(), key=password.getText().toString();
            if(network.isEmpty() || network.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32) { notice("SSID: 1–32 octeți"); return; }
            if(secured && key.isEmpty()) { notice("Rețeaua cere parolă"); return; }
            if(!key.isEmpty() && !(key.length()>=8 && key.length()<=63) && !key.matches("[0-9a-fA-F]{64}")) { notice("Parolă WPA: 8–63 caractere sau 64 hex"); return; }
            if(key.isEmpty()) { options("Rețea fără parolă?",new String[]{"Conectează","Anulează"},new Choice(){public void pick(int i){if(i==0) connectWifi(network,key);}}); }
            else connectWifi(network,key);
        }};
        footer("Conectare","","Înapoi"); ssid.requestFocus();
    }
    private void connectWifi(String name,String password) {
        if(!wifi.isWifiEnabled()) { notice("Pornește întâi Wi-Fi"); return; }
        WifiConfiguration config=new WifiConfiguration(); config.SSID=quoted(name);
        if(password.isEmpty()) config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        else { config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK); config.preSharedKey=password.matches("[0-9a-fA-F]{64}")?password:quoted(password); }
        List<WifiConfiguration> saved=wifi.getConfiguredNetworks();
        if(saved!=null) for(WifiConfiguration old:saved) if(config.SSID.equals(old.SSID) && config.allowedKeyManagement.equals(old.allowedKeyManagement)) { config.networkId=old.networkId; break; }
        int id=config.networkId>=0?wifi.updateNetwork(config):wifi.addNetwork(config);
        if(id<0 || !wifi.enableNetwork(id,true)) { notice("Conectarea nu a fost acceptată"); return; }
        wifi.reconnect(); notice("Conectare solicitată"); show();
    }
    private void showBluetooth() {
        if(bluetooth==null) { notice("Bluetooth indisponibil"); finish(); return; }
        int selected=selection(),state=bluetooth.getState();
        String status=state==BluetoothAdapter.STATE_ON?"Pornit":state==BluetoothAdapter.STATE_OFF?"Oprit":"În schimbare…";
        if(bluetooth.isEnabled()) for(BluetoothDevice device:bluetooth.getBondedDevices()) if(!devices.contains(device)) devices.add(device);
        final List<BluetoothDevice> visible=new ArrayList<>(devices);
        ArrayList<String> rows=new ArrayList<>(Arrays.asList("Bluetooth · "+status,bluetooth.isDiscovering()?"Se caută… · oprește":"Caută dispozitive","Setări Android"));
        for(BluetoothDevice device:visible) {
            String name=device.getName(); if(name==null || name.isEmpty()) name="Dispozitiv fără nume";
            boolean connected=input!=null && input.getConnectionState(device)==BluetoothProfile.STATE_CONNECTED;
            boolean audioConnected=a2dp!=null&&a2dp.getConnectionState(device)==BluetoothProfile.STATE_CONNECTED;rows.add(name+" · "+(audioConnected?"Audio A2DP conectat":connected?"HID conectat":device.getBondState()==BluetoothDevice.BOND_BONDED?"Asociat":"Nou"));
        }
        list("Bluetooth Leo",rows,selected,new Choice(){public void pick(int i){
            if(i==0) { boolean accepted=bluetooth.isEnabled()?bluetooth.disable():bluetooth.enable(); if(!accepted) notice("Cererea Bluetooth a fost refuzată"); }
            else if(i==1) {
                if(bluetooth.isDiscovering()) bluetooth.cancelDiscovery();
                else if(!bluetooth.isEnabled()) notice("Pornește întâi Bluetooth");
                else if(locationReady()) { discoveryOwned=bluetooth.startDiscovery(); if(!discoveryOwned) notice("Căutarea nu a pornit"); }
            } else if(i==2) androidPage(Settings.ACTION_BLUETOOTH_SETTINGS);
            else if(i-3<visible.size()) deviceOptions(visible.get(i-3));
        }},"Actualiz.",new Runnable(){public void run(){show();}});
    }
    private void deviceOptions(final BluetoothDevice device) {
        options("Dispozitiv Bluetooth",new String[]{device.getBondState()==BluetoothDevice.BOND_BONDED?"Conectează mouse/taste":"Asociază","Deconectează mouse/taste","Setări Android","Conectează audio stereo","Deconectează audio stereo"},new Choice(){public void pick(final int i){
            if(i==2) { androidPage(Settings.ACTION_BLUETOOTH_SETTINGS); return; }
            bluetooth.cancelDiscovery();
            if(i==0 && device.getBondState()!=BluetoothDevice.BOND_BONDED) { if(!device.createBond()) notice("Asocierea nu a pornit"); return; }
            if((i>=3?a2dp:input)==null) { notice("Profilul selectat nu este pregătit"); return; }
            try {
                BluetoothProfile target=i>=3?a2dp:input;if(i==3&&device.getBondState()!=BluetoothDevice.BOND_BONDED){if(!device.createBond())notice("Asocierea nu a pornit");return;}Object accepted=target.getClass().getMethod(i==0||i==3?"connect":"disconnect",BluetoothDevice.class).invoke(target,device);
                notice(Boolean.TRUE.equals(accepted)?"Cerere trimisă; așteaptă starea":"Cererea nu a fost acceptată");
            } catch(ReflectiveOperationException failure) { notice("Profilul selectat nu este disponibil"); }
        }});
    }
    private int apState() {
        try { return ((Integer)wifi.getClass().getMethod("getWifiApState").invoke(wifi)).intValue(); }
        catch(Exception unavailable) { return -1; }
    }
    private WifiConfiguration apConfig() throws ReflectiveOperationException {
        return (WifiConfiguration)wifi.getClass().getMethod("getWifiApConfiguration").invoke(wifi);
    }
    private void showHotspot() {
        int selected=selection(), state=apState();
        String status=state==13?"Pornit":state==11?"Oprit":state==12?"Se pornește…":state==10?"Se oprește…":state==14?"Eroare":"Indisponibil";
        String name="Neconfigurat";
        try { WifiConfiguration c=apConfig(); if(c!=null) name=c.SSID; } catch(Exception ignored) { }
        ArrayList<String> rows=new ArrayList<>(Arrays.asList("Hotspot · "+status,"Nume · "+name,"Configurează WPA2","Setări Android"));
        if(!hotspotResult.isEmpty()) rows.add(hotspotResult);
        list("Hotspot Leo",rows,selected,new Choice(){public void pick(int i){
            if(i==0) toggleHotspot(); else if(i==1||i==2) hotspotForm(); else if(i==3) androidPage("android.settings.TETHER_SETTINGS"); else notice(hotspotResult);
        }},"Actualiz.",new Runnable(){public void run(){show();}});
    }
    private void hotspotForm() {
        if(apState()!=11) { notice("Oprește întâi hotspotul"); return; }
        WifiConfiguration old=null; try { old=apConfig(); } catch(Exception ignored) { }
        LinearLayout panel=fields("Configurare hotspot");
        final EditText ssid=field(panel,"Nume rețea",old!=null?old.SSID:"Leo 6300",InputType.TYPE_CLASS_TEXT);
        final EditText password=field(panel,"Parolă WPA2 · 8–63 caractere",old!=null && old.preSharedKey!=null?old.preSharedKey:"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        left=new Runnable(){public void run(){
            String name=ssid.getText().toString(),key=password.getText().toString();
            if(name.isEmpty() || name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32 || key.length()<8 || key.length()>63) { notice("Verifică numele și parola"); return; }
            if(!Settings.System.canWrite(ConnectivityActivity.this)) { notice("Leo necesită acces la modificarea setărilor"); return; }
            WifiConfiguration c=new WifiConfiguration(); c.SSID=name; c.preSharedKey=key; c.allowedKeyManagement.set(AP_WPA2_PSK);
            try { if(Boolean.TRUE.equals(wifi.getClass().getMethod("setWifiApConfiguration",WifiConfiguration.class).invoke(wifi,c))) { notice("Configurație salvată"); show(); } else notice("Configurația a fost refuzată"); }
            catch(Exception failure) { notice("Hotspotul nu poate fi configurat"); }
        }};
        back=new Runnable(){public void run(){show();}};
        footer("Salvează","","Înapoi"); ssid.requestFocus();
    }
    private void toggleHotspot() {
        final ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        try {
            int state=apState();
            if(state==12 || state==10) { notice("Așteaptă schimbarea stării"); return; }
            if(state==13) { manager.getClass().getMethod("stopTethering",int.class).invoke(manager,0); return; }
            WifiConfiguration config=apConfig();
            if(config==null || config.preSharedKey==null || config.preSharedKey.length()<8 || !config.allowedKeyManagement.get(AP_WPA2_PSK)) { notice("Configurează mai întâi WPA2 și parola"); return; }
            if(wifi.isWifiEnabled()) {
                options("Hotspot oprește Wi-Fi",new String[]{"Pornește hotspot","Anulează"},new Choice(){public void pick(int i){if(i==0) startHotspot(manager);}});
            } else startHotspot(manager);
        } catch(Exception unavailable) { notice("Serviciul hotspot nu este disponibil"); }
    }
    private void startHotspot(ConnectivityManager manager) {
        try {
            hotspotResult="Pornire solicitată";
            Field field=ConnectivityManager.class.getDeclaredField("mService"); field.setAccessible(true); Object service=field.get(manager);
            Method start=service.getClass().getMethod("startTethering",int.class,ResultReceiver.class,boolean.class,String.class);
            start.setAccessible(true);
            start.invoke(service,0,new ResultReceiver(new Handler(Looper.getMainLooper())) {
                protected void onReceiveResult(int code,Bundle data) { hotspotResult=code==0?"Cerere acceptată":"Eroare hotspot · cod "+code; notice(hotspotResult); if(!form) show(); }
            },true,getPackageName());
        } catch(Exception unavailable) {
            Throwable cause=unavailable instanceof InvocationTargetException?((InvocationTargetException)unavailable).getTargetException():unavailable;
            hotspotResult="Refuzat · "+cause.getClass().getSimpleName();
            android.util.Log.w("LeoConnectivity","startTethering failed",cause);
            notice(hotspotResult); show();
        }
    }
}
