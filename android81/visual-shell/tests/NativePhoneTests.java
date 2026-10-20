package ro.leo.visual;
import android.app.*;import android.content.*;import android.content.pm.*;import android.net.Uri;import android.os.Bundle;
/** Short package-contract checks only. No calls, radio changes or UI instrumentation. */
public final class NativePhoneTests extends Instrumentation {
 private void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public void onCreate(Bundle args){start();}
 public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();PackageManager p=c.getPackageManager();
  for(Intent i:new Intent[]{new Intent(Intent.ACTION_DIAL),new Intent(Intent.ACTION_DIAL,Uri.parse("tel:123"))}){boolean found=false;for(ResolveInfo r:p.queryIntentActivities(i,PackageManager.MATCH_DEFAULT_ONLY))if(c.getPackageName().equals(r.activityInfo.packageName)&&PhoneActivity.class.getName().equals(r.activityInfo.name))found=true;check(found,"Leo handles DIAL with/without tel data");}
  ServiceInfo s=p.getServiceInfo(new ComponentName(c,LeoInCallService.class),PackageManager.GET_META_DATA);check(s.exported&&"android.permission.BIND_INCALL_SERVICE".equals(s.permission),"Telecom-only binding");check(s.metaData.getBoolean("android.telecom.IN_CALL_SERVICE_UI"),"native call UI declared");check(!s.metaData.getBoolean("android.telecom.IN_CALL_SERVICE_RINGING",false),"platform retains ringer ownership");
  ActivityInfo a=p.getActivityInfo(new ComponentName(c,InCallActivity.class),0);check(!a.exported,"demo/current call screen is private");ActivityInfo r=p.getReceiverInfo(new ComponentName(c,MissedCallReceiver.class),0);check(r.exported&&"android.permission.MODIFY_PHONE_STATE".equals(r.permission),"missed-call receiver requires privileged sender");
  check("00:00".equals(CallPresentation.duration(-1)),"negative duration");check("01:05".equals(CallPresentation.duration(65000)),"minute duration");check("1:01:01".equals(CallPresentation.duration(3661000)),"hour duration");check(CallPresentation.priority(2)<CallPresentation.priority(4)&&CallPresentation.priority(4)<CallPresentation.priority(7),"ringing/active/ended priority");out.putString("result","PASS: DIAL intents, Telecom binding, ringer ownership, private UI, missed-call permission and presentation");
 }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}
