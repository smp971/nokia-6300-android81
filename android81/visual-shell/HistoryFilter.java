package ro.leo.visual;
import android.provider.CallLog;
final class HistoryFilter {
    static boolean matches(String number,String name,int type,int slot,String query,int sim,boolean missed){
        return (sim<0||slot==sim)&&(!missed||type==CallLog.Calls.MISSED_TYPE)
            &&(query.isEmpty()||GlobalSearchActivity.matches(name+" "+number,query));
    }
}
