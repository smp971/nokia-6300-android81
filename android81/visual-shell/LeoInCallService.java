package ro.leo.visual;
import android.content.*;import android.telecom.*;import java.util.*;
/** Telecom owns the connections. Leo owns their normal in-call UI. No radio or audio HAL code. */
public final class LeoInCallService extends InCallService {
 static final String CHANGED="ro.leo.visual.CALL_CHANGED";
 static volatile LeoInCallService active;
 static String lastEnd="Apel încheiat";
 private Call selected;
 private final Map<Call,Call.Callback> callbacks=new HashMap<Call,Call.Callback>();
 public void onCreate(){super.onCreate();active=this;}
 public void onDestroy(){for(Map.Entry<Call,Call.Callback> e:callbacks.entrySet())e.getKey().unregisterCallback(e.getValue());callbacks.clear();active=null;CallNotices.cancelOngoing(this);sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));super.onDestroy();}
 static List<Call> calls(){LeoInCallService s=active;return s==null?Collections.<Call>emptyList():new ArrayList<Call>(s.getCalls());}
 static Call current(){LeoInCallService s=active;if(s==null)return null;List<Call> all=calls();if(s.selected!=null&&all.contains(s.selected)&&s.selected.getState()!=Call.STATE_DISCONNECTED)return s.selected;Call best=null;for(Call c:all)if(best==null||CallPresentation.priority(c.getState())<CallPresentation.priority(best.getState()))best=c;s.selected=best;return best;}
 static void select(Call call){LeoInCallService s=active;if(s!=null&&calls().contains(call)){s.selected=call;s.publish(false);}}
 public void onCallAdded(final Call call){super.onCallAdded(call);Call.Callback cb=new Call.Callback(){public void onStateChanged(Call c,int state){if(state==Call.STATE_RINGING)selected=c;publish(state==Call.STATE_RINGING);}public void onDetailsChanged(Call c,Call.Details d){publish(false);}public void onConferenceableCallsChanged(Call c,List<Call> others){publish(false);}};callbacks.put(call,cb);call.registerCallback(cb);if(selected==null||call.getState()==Call.STATE_RINGING)selected=call;publish(true);}
 public void onCallRemoved(Call call){Call.Details d=call.getDetails();if(d!=null&&d.getDisconnectCause()!=null){CharSequence label=d.getDisconnectCause().getLabel();lastEnd=label==null||label.length()==0?"Apel încheiat":label.toString();}Call.Callback cb=callbacks.remove(call);if(cb!=null)call.unregisterCallback(cb);if(selected==call)selected=null;super.onCallRemoved(call);publish(false);UnreadState.refresh(this);}
 public void onCallAudioStateChanged(CallAudioState audio){super.onCallAudioStateChanged(audio);publish(false);}
 public void onBringToForeground(boolean showDialpad){super.onBringToForeground(showDialpad);show();}
 private void show(){try{startActivity(new Intent(this,InCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}catch(RuntimeException unavailable){/* Telecom retains its platform fallback if this service cannot bind. */}}
 private void publish(boolean foreground){Call c=current();if(c==null)CallNotices.cancelOngoing(this);else CallNotices.ongoing(this,c);sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));if(foreground&&c!=null)show();}
 static String number(Call c){Call.Details d=c==null?null:c.getDetails();return d==null||d.getHandlePresentation()!=TelecomManager.PRESENTATION_ALLOWED||d.getHandle()==null?"":d.getHandle().getSchemeSpecificPart();}
 static String identity(Call c){Call.Details d=c==null?null:c.getDetails();if(d==null)return "Apel";if(d.getHandlePresentation()!=TelecomManager.PRESENTATION_ALLOWED)return "Număr privat";CharSequence name=d.getCallerDisplayNamePresentation()==TelecomManager.PRESENTATION_ALLOWED?d.getCallerDisplayName():null;String n=number(c);return name!=null&&name.length()>0?name.toString():n.isEmpty()?"Număr necunoscut":n;}
 static String account(android.content.Context context,Call c){try{PhoneAccount p=((TelecomManager)context.getSystemService(TELECOM_SERVICE)).getPhoneAccount(c.getDetails().getAccountHandle());return p==null||p.getLabel()==null?"Cont de apel":PhoneAccounts.slot(context,c.getDetails().getAccountHandle().getId())+" · "+p.getLabel().toString();}catch(RuntimeException e){return "Cont de apel";}}
}
