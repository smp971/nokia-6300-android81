package ro.leo.i18n;

import java.util.*;

/** Only source-owned labels call t(); user content is never passed to a translator. */
public final class L {
    private static final Map<String,String> EN=new HashMap<String,String>();
    private static final IdentityHashMap<String[],String[]> ARRAYS=new IdentityHashMap<String[],String[]>();
    private static final Locale RO=new Locale("ro"), ENGLISH=Locale.ENGLISH;
    private static volatile boolean english;
    static { EnglishCatalog.fill(EN); }
    private L(){}
    public static String t(String romanian){if(!english)return romanian;String value=EN.get(romanian);return value==null?romanian:value;}
    public static String t(String romanian,String context){if(!english)return romanian;String value=EN.get(romanian+" ["+context+"]");return value==null?t(romanian):value;}
    public static boolean english(){return english;}
    public static Locale locale(){return english?ENGLISH:RO;}
    public static synchronized void useEnglish(boolean enabled){
        if(english==enabled)return;
        english=enabled;
        for(Map.Entry<String[],String[]> entry:ARRAYS.entrySet())apply(entry.getKey(),entry.getValue());
    }
    private static void apply(String[] target,String[] original){for(int i=0;i<target.length;i++)target[i]=t(original[i]);}
    public static synchronized <T> T array(T array){register(array);return array;}
    private static void register(Object value){
        if(value instanceof String[]){String[] target=(String[])value;String[] original=target.clone();ARRAYS.put(target,original);apply(target,original);}
        else if(value instanceof Object[])for(Object child:(Object[])value)register(child);
    }
}
