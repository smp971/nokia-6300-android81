package ro.leo.visual;
import android.content.*;import java.util.*;
/** Bounded local per-track progress; URIs are hashed rather than used as preference keys. */
final class TrackBookmarks {
 static String key(String uri){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(uri.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 static int position(Context c,String uri){return Math.max(0,c.getSharedPreferences("track-progress",0).getInt(key(uri)+"-position",0));}
 static void save(Context c,String uri,int position,int duration){if(uri==null||duration<600000)return;android.content.SharedPreferences p=c.getSharedPreferences("track-progress",0);String k=key(uri);android.content.SharedPreferences.Editor e=p.edit().putInt(k+"-position",position>=duration-3000?0:Math.max(0,position)).putLong(k+"-time",System.currentTimeMillis());ArrayList<String> ids=new ArrayList<String>();for(String id:p.getAll().keySet())if(id.endsWith("-time")&&!id.equals(k+"-time"))ids.add(id.substring(0,id.length()-5));Collections.sort(ids,new Comparator<String>(){public int compare(String a,String b){return Long.compare(p.getLong(b+"-time",0),p.getLong(a+"-time",0));}});for(int i=199;i<ids.size();i++){e.remove(ids.get(i)+"-time");e.remove(ids.get(i)+"-position");}e.apply();}
}
