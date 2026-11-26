package ro.leo.visual;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.net.*;

/** Owner-enabled foreground WebUI. Event-driven Wi-Fi rebinding; no expiry, polling or wake lock. */
public final class CompanionService extends Service {
    static final String CHANGED="ro.leo.visual.WEBUI_CHANGED";
    static volatile CompanionService instance;
    static boolean active(){CompanionService live=instance;return live!=null&&live.server!=null&&live.server.running&&!live.address.isEmpty();}
    volatile CompanionServer server;
    CompanionAuth auth;
    volatile String address="",fingerprint="",status="";
    private final Handler handler=new Handler();
    private ConnectivityManager connectivity;
    private boolean destroyed,registered,preparing;
    private int generation;
    private InetAddress desired;
    private Network lost;
    static void ensureStarted(Context c){
        if(CompanionSettings.enabled(c)&&new CompanionAuth(c).configured()&&instance==null)
            c.startForegroundService(new Intent(c,CompanionService.class));
    }
    static InetAddress wifi(Context c)throws Exception{return wifi(c,null);}
    private static InetAddress wifi(Context c,Network lost)throws Exception{
        ConnectivityManager cm=(ConnectivityManager)c.getSystemService(CONNECTIVITY_SERVICE);
        for(Network network:cm.getAllNetworks()){
            if(network.equals(lost))continue;
            NetworkCapabilities cap=cm.getNetworkCapabilities(network);LinkProperties lp=cm.getLinkProperties(network);
            if(cap!=null&&lp!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress()&&!a.getAddress().isLinkLocalAddress())return a.getAddress();
        }
        throw new java.io.IOException("Wi-Fi unavailable");
    }
    private final ConnectivityManager.NetworkCallback networks=new ConnectivityManager.NetworkCallback(){
        public void onAvailable(Network network){if(network.equals(lost))lost=null;queueRefresh();}
        public void onLinkPropertiesChanged(Network network,LinkProperties properties){queueRefresh();}
        public void onLost(Network network){lost=network;queueRefresh();}
    };
    private final Runnable refresh=new Runnable(){public void run(){refreshBinding();}};
    private void queueRefresh(){handler.removeCallbacks(refresh);handler.postDelayed(refresh,250);}
    private void notifyState(String text){
        status=text;sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("leo-companion","KeyShell WebUI",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,CompanionService.class).setAction("stop"),0);
        startForeground(737,new Notification.Builder(this,"leo-companion").setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("KeyShell WebUI").setContentText(text).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,CompanionActivity.class),0))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,ro.leo.i18n.L.t("Oprește"),stop).build());
    }
    public void onCreate(){super.onCreate();auth=new CompanionAuth(this);instance=this;connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);notifyState(ro.leo.i18n.L.t("WebUI · inițializare"));}
    public IBinder onBind(Intent i){return null;}
    public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"stop".equals(intent.getAction())){CompanionSettings.enabled(this,false);stopSelf();return START_NOT_STICKY;}
        if(!CompanionSettings.enabled(this)||!auth.configured()){stopSelf();return START_NOT_STICKY;}
        if(status.isEmpty())notifyState(ro.leo.i18n.L.t("Permanent în fundal · așteaptă Wi-Fi"));
        if(!registered){
            try{connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),networks,handler);registered=true;}
            catch(RuntimeException e){notifyState(ro.leo.i18n.L.t("WebUI · rețeaua nu poate fi urmărită"));stopSelf();return START_NOT_STICKY;}
        }
        refreshBinding();return START_STICKY;
    }
    private void refreshBinding(){
        if(destroyed)return;
        try{bindTo(wifi(this,lost));}catch(Exception unavailable){bindTo(null);}
    }
    private synchronized void bindTo(final InetAddress target){
        if(destroyed)return;
        if(java.util.Objects.equals(target,desired)&&(preparing||server!=null))return;
        final int ticket=++generation;desired=target;preparing=false;
        if(server!=null){server.close();server=null;}address="";
        if(target==null){notifyState(ro.leo.i18n.L.t("Permanent în fundal · așteaptă Wi-Fi"));return;}
        preparing=true;notifyState(ro.leo.i18n.L.t("WebUI · conectare HTTPS"));
        new Thread(new Runnable(){public void run(){
            CompanionServer built=null;
            try{
                final String digest=CompanionTls.fingerprint();built=new CompanionServer(CompanionService.this,target,18764,auth);
                final CompanionServer ready=built;
                synchronized(CompanionService.this){if(destroyed||ticket!=generation){ready.close();return;}server=ready;}
                handler.post(new Runnable(){public void run(){
                    if(destroyed||ticket!=generation){ready.close();return;}
                    preparing=false;fingerprint=digest;address="https://"+target.getHostAddress()+":18764/";ready.start();
                    notifyState(ro.leo.i18n.L.t("Permanent în fundal · HTTPS activ"));
                }});
            }catch(Exception error){
                if(built!=null)built.close();
                handler.post(new Runnable(){public void run(){if(!destroyed&&ticket==generation){preparing=false;server=null;desired=null;notifyState(ro.leo.i18n.L.t("WebUI · reîncercare conexiune"));handler.postDelayed(refresh,30000);}}});
            }
        }},"KeyShellWebUIBind").start();
    }
    public synchronized void onDestroy(){
        destroyed=true;generation++;if(instance==this)instance=null;handler.removeCallbacksAndMessages(null);
        if(registered)connectivity.unregisterNetworkCallback(networks);if(server!=null){server.close();server=null;}
        auth.clear();address="";sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));stopForeground(true);super.onDestroy();
    }
}
