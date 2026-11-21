package ro.leo.visual;

import android.content.ComponentCallbacks2;
import java.util.WeakHashMap;

/** Process-local UI cache pressure. Services and active playback are never stopped. */
final class UiMemory {
    interface Client {void trimUiMemory(int level);}
    private static final WeakHashMap<Client,Boolean> clients=new WeakHashMap<Client,Boolean>();
    static void register(Client client){clients.put(client,Boolean.TRUE);}
    static void unregister(Client client){clients.remove(client);}
    static boolean hidden(int level){return level>=ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;}
    static void trim(int level){
        if(level<ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)return;
        for(Client client:clients.keySet().toArray(new Client[0]))if(client!=null)client.trimUiMemory(level);
    }
}
