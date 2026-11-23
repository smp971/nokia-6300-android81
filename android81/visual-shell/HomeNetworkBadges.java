package ro.leo.visual;

import android.content.Context;
import android.net.*;
import android.telephony.SubscriptionManager;

/** Uses Android's validated default cellular network; never probes a server. */
final class HomeNetworkBadges {
    static String technology(String rat,boolean voiceIms){return voiceIms&&"4G".equals(rat)?"VoLTE "+rat:rat;}
    static boolean internetFor(int subscription,int dataSubscription,boolean validatedCellular){
        return validatedCellular&&subscription>=0&&subscription==dataSubscription;
    }
    static boolean validatedCellular(NetworkCapabilities caps){
        return caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            &&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            &&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
    static int internetSubscription(Context context){
        try{
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network=manager.getActiveNetwork();
            NetworkCapabilities caps=network==null?null:manager.getNetworkCapabilities(network);
            if(!validatedCellular(caps))return -1;
            return SubscriptionManager.getDefaultDataSubscriptionId();
        }catch(RuntimeException unavailable){return -1;}
    }
}
