package ro.leo.visual;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.view.KeyEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Original native artwork and keypad layout. Never registers as HOME or a phone service. */
public final class MainActivity extends Activity {
    private Screen screen;
    private final ArrayList<TelephonyManager> phones = new ArrayList<TelephonyManager>();
    private final ArrayList<PhoneStateListener> phoneListeners = new ArrayList<PhoneStateListener>();
    private SubscriptionManager subscriptions;
    private final SubscriptionManager.OnSubscriptionsChangedListener subscriptionListener =
        new SubscriptionManager.OnSubscriptionsChangedListener() {
            public void onSubscriptionsChanged() { listenToPhones(); }
        };
    private final Handler handler = new Handler();
    private final Runnable clock = new Runnable() {
        public void run() {
            screen.refresh();
            handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(26, 14, 58));
        screen = new Screen();
        if (state != null) {
            screen.grid = state.getBoolean("grid");
            screen.selected = Math.max(0, Math.min(8, state.getInt("selected")));
        }
        setContentView(screen);
    }
    protected void onResume() {
        super.onResume();
        subscriptions = (SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
        try { subscriptions.addOnSubscriptionsChangedListener(subscriptionListener); }
        catch (RuntimeException ignored) { }
        listenToPhones();
        handler.removeCallbacks(clock); handler.post(clock);
    }
    protected void onPause() {
        handler.removeCallbacks(clock);
        if (subscriptions != null) subscriptions.removeOnSubscriptionsChangedListener(subscriptionListener);
        stopPhoneListeners();
        super.onPause();
    }
    private void stopPhoneListeners() {
        for (int i=0;i<phones.size();i++) {
            try { phones.get(i).listen(phoneListeners.get(i), PhoneStateListener.LISTEN_NONE); }
            catch (RuntimeException ignored) { }
        }
        phones.clear(); phoneListeners.clear();
    }
    private void listenToPhones() {
        stopPhoneListeners();
        try {
            List<SubscriptionInfo> active = subscriptions.getActiveSubscriptionInfoList();
            if (active != null) for (SubscriptionInfo info : active) {
                TelephonyManager phone = ((TelephonyManager)getSystemService(TELEPHONY_SERVICE))
                    .createForSubscriptionId(info.getSubscriptionId());
                PhoneStateListener listener = new PhoneStateListener() {
                    public void onServiceStateChanged(ServiceState state) { screen.refresh(); }
                    public void onDataConnectionStateChanged(int state, int type) { screen.refresh(); }
                };
                phones.add(phone); phoneListeners.add(listener);
                phone.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
            }
        } catch (RuntimeException ignored) { }
        screen.refresh();
    }
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("grid", screen.grid); state.putInt("selected", screen.selected);
        super.onSaveInstanceState(state);
    }
    public void onBackPressed() {
        if (screen.grid || !screen.message.isEmpty()) {
            screen.grid = false; screen.message = ""; screen.invalidate();
        } else finish();
    }
    public boolean onKeyDown(int key, KeyEvent event) {
        if (screen.key(key, event.getRepeatCount())) return true;
        return super.onKeyDown(key, event);
    }
    private void options() {
        new AlertDialog.Builder(this).setTitle("Leo Visual")
            .setItems(new String[]{"Schimbă fundalul", "Despre", "Ieși în Leo"}, new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                if (which == 0) { screen.warm = !screen.warm; screen.invalidate(); }
                else if (which == 1) new AlertDialog.Builder(MainActivity.this).setTitle("Prototip vizual")
                    .setMessage("Navigare din taste. OK deschide meniul și aplicațiile. Tasta roșie revine în Leo.")
                    .setPositiveButton("OK", null).show();
                else finish();
              }
            }).show();
    }
    private final class Screen extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box = new RectF();
        final Path path = new Path();
        final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        final String[] names = {"Telefon", "Contacte", "Mesaje", "Ceas", "Calculator", "Galerie", "Muzică", "Fișiere", "Setări"};
        final String[] packages = {"com.android.dialer", "com.android.contacts", "com.android.messaging", "com.android.deskclock", "com.android.calculator2", "com.android.gallery3d", "com.android.music", "com.android.documentsui", "com.android.settings"};
        final int[] colors = {0xff32c479,0xff23a8e1,0xffefb733,0xffa275ed,0xff687bfa,0xfff07596,0xffec6868,0xffefaa45,0xff8795ab};
        final LinearGradient violet = new LinearGradient(0,0,240,302,new int[]{0xff241047,0xff593185,0xffbc6e91},null,Shader.TileMode.CLAMP);
        final LinearGradient sunset = new LinearGradient(0,0,240,302,new int[]{0xff243959,0xff98707c,0xffedb17c},null,Shader.TileMode.CLAMP);
        boolean grid, warm;
        int selected;
        String time = "", date = "", message = "";
        final String[] simLabels = {"SIM 1: se verifică", "SIM 2: se verifică"};
        Screen() { super(MainActivity.this); setFocusable(true); setFocusableInTouchMode(true); requestFocus(); }
        void refresh() {
            Date now = new Date();
            time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            date = new SimpleDateFormat("EEEE, d MMMM", new Locale("ro")).format(now);
            updateSims();
            invalidate();
        }
        void updateSims() {
            TelephonyManager base = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            for(int slot=0;slot<2;slot++) {
                String prefix = "SIM " + (slot+1) + ": ";
                try {
                    int sim = base.getSimState(slot);
                    String status = sim == TelephonyManager.SIM_STATE_ABSENT ? "lipsă"
                        : sim == TelephonyManager.SIM_STATE_PIN_REQUIRED ? "PIN necesar"
                        : sim == TelephonyManager.SIM_STATE_PUK_REQUIRED ? "PUK necesar"
                        : sim == TelephonyManager.SIM_STATE_READY ? "se conectează" : "indisponibil";
                    simLabels[slot] = prefix + status;
                    if (sim != TelephonyManager.SIM_STATE_READY) continue;
                    List<SubscriptionInfo> active = ((SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE)).getActiveSubscriptionInfoList();
                    if(active == null) continue;
                    for(SubscriptionInfo info : active) if(info.getSimSlotIndex() == slot) {
                        TelephonyManager phone = base.createForSubscriptionId(info.getSubscriptionId());
                        String carrier = phone.getNetworkOperatorName();
                        if(carrier == null || carrier.isEmpty()) carrier = info.getCarrierName() == null ? "" : info.getCarrierName().toString();
                        ServiceState service = phone.getServiceState();
                        String name = carrier.isEmpty() ? "operator" : carrier;
                        if(service == null) simLabels[slot] = prefix + "se verifică";
                        else if(service.getState() == ServiceState.STATE_IN_SERVICE) {
                            int type = phone.getDataNetworkType();
                            String rat = type == TelephonyManager.NETWORK_TYPE_LTE ? " · 4G"
                                : (type == TelephonyManager.NETWORK_TYPE_UMTS || type == TelephonyManager.NETWORK_TYPE_HSDPA
                                || type == TelephonyManager.NETWORK_TYPE_HSUPA || type == TelephonyManager.NETWORK_TYPE_HSPA
                                || type == TelephonyManager.NETWORK_TYPE_HSPAP) ? " · 3G" : " · conectat";
                            simLabels[slot] = prefix + name + rat;
                        } else if(service.getState() == ServiceState.STATE_EMERGENCY_ONLY) simLabels[slot] = prefix + "doar urgențe";
                        else if(service.getState() == ServiceState.STATE_POWER_OFF) simLabels[slot] = prefix + "radio oprit";
                        else simLabels[slot] = prefix + "fără serviciu";
                        break;
                    }
                } catch(SecurityException ignored) { simLabels[slot] = prefix + "acces neacordat"; }
                catch(RuntimeException ignored) { simLabels[slot] = prefix + "indisponibil"; }
            }
        }
        boolean key(int key, int repeat) {
            if (key == KeyEvent.KEYCODE_BACK) { if (repeat == 0) onBackPressed(); return true; }
            if (key == KeyEvent.KEYCODE_MENU) { if (repeat == 0) options(); return true; }
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (repeat == 0) { if (grid) open(selected); else { grid = true; message = ""; invalidate(); } }
                return true;
            }
            // Green opens the dialer only; this app never places a call.
            if (key == KeyEvent.KEYCODE_CALL) { if (repeat == 0) open(0); return true; }
            int digit = key - KeyEvent.KEYCODE_1;
            if (grid && digit >= 0 && digit < 9) {
                selected = digit; invalidate(); return true;
            }
            int delta = key == KeyEvent.KEYCODE_DPAD_UP ? -3 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 3
                : key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (delta != 0) {
                if (!grid) grid = true;
                else if ((delta == -1 && selected % 3 != 0) || (delta == 1 && selected % 3 != 2)
                         || (delta == -3 && selected >= 3) || (delta == 3 && selected < 6)) selected += delta;
                message = ""; invalidate(); return true;
            }
            return false;
        }
        void open(int index) {
            Intent intent;
            if (index == 0) intent = new Intent(Intent.ACTION_DIAL);
            else if (index == 1) intent = new Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI);
            else if (index == 8) intent = new Intent(Settings.ACTION_SETTINGS);
            else {
                intent = getPackageManager().getLaunchIntentForPackage(packages[index]);
                if (intent == null) {
                    // Resolve launcher activities for OEM/AOSP package variants.
                    List<ResolveInfo> apps = getPackageManager().queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
                    for (ResolveInfo info : apps) {
                        String pkg = info.activityInfo.packageName;
                        if (pkg.equals(packages[index]) || (index == 4 && pkg.contains("calculator"))) {
                            intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, info.activityInfo.name)); break;
                        }
                    }
                }
            }
            if (intent == null || intent.resolveActivity(getPackageManager()) == null) {
                message = names[index] + ": indisponibil"; invalidate(); return;
            }
            try { startActivity(intent); }
            catch (RuntimeException ignored) { message = "Nu se poate deschide"; invalidate(); }
        }
        void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setTypeface(regular);
            p.setTextSize(size); p.setColor(color); p.setTextAlign(align); c.drawText(s, x, y, p);
        }
        void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
            p.setShader(null); p.setColor(color); p.setStyle(Paint.Style.FILL); box.set(l,t,r,b); c.drawRoundRect(box,radius,radius,p);
        }
        void line(Canvas c,float x1,float y1,float x2,float y2) { c.drawLine(x1,y1,x2,y2,p); }
        void icon(Canvas c,int index,float x,float y) {
            p.setShader(null); p.setColor(0x25000000); p.setStyle(Paint.Style.FILL); c.drawCircle(x,y+2,22,p);
            p.setColor(colors[index]); c.drawCircle(x,y,22,p);
            c.save(); c.translate(x,y); p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.4f); p.setStrokeCap(Paint.Cap.ROUND);
            switch(index) {
                case 0: c.drawArc(-11,-12,12,11,25,135,false,p); line(c,-10,-7,-7,-11); line(c,8,8,12,5); break;
                case 1: c.drawCircle(0,-5,5,p); c.drawArc(-10,3,10,17,190,160,false,p); break;
                case 2: box.set(-12,-9,12,7); c.drawRoundRect(box,3,3,p); line(c,-7,7,-10,12); line(c,-5,-3,6,-3); line(c,-5,2,3,2); break;
                case 3: c.drawCircle(0,0,12,p); line(c,0,0,0,-7); line(c,0,0,6,3); break;
                case 4: box.set(-10,-13,10,13); c.drawRoundRect(box,2,2,p); line(c,-5,-7,5,-7); line(c,-5,0,-1,0); line(c,3,0,6,0); line(c,-5,6,-1,6); line(c,3,6,6,6); break;
                case 5: box.set(-13,-10,13,10); c.drawRoundRect(box,2,2,p); c.drawCircle(6,-4,2,p); line(c,-10,6,-3,-2); line(c,-3,-2,2,3); line(c,2,3,5,0); line(c,5,0,10,6); break;
                case 6: line(c,0,-9,0,8); line(c,0,-9,10,-12); line(c,10,-12,10,5); c.drawOval(-7,5,0,11,p); c.drawOval(3,2,10,8,p); break;
                case 7: path.reset(); path.moveTo(-13,-8); path.lineTo(-3,-8); path.lineTo(0,-4); path.lineTo(13,-4); path.lineTo(13,10); path.lineTo(-13,10); path.close(); c.drawPath(path,p); break;
                case 8: c.drawCircle(0,0,9,p); c.drawCircle(0,0,3,p); for(int k=0;k<8;k++){c.save();c.rotate(k*45);line(c,0,-10,0,-14);c.restore();} break;
            }
            p.setStyle(Paint.Style.FILL); c.restore();
        }
        protected void onDraw(Canvas output) {
            super.onDraw(output);
            output.save(); output.scale(getWidth()/240f,getHeight()/302f);
            p.setStyle(Paint.Style.FILL); p.setShader(warm?sunset:violet); output.drawRect(0,0,240,302,p); p.setShader(null);
            if (!grid) {
                p.setColor(0x25ffffff); output.drawCircle(192,149,37,p);
                path.reset(); path.moveTo(0,238); path.lineTo(74,137); path.lineTo(147,238); path.close(); p.setColor(0xff372454); output.drawPath(path,p);
                path.reset(); path.moveTo(78,254); path.lineTo(172,157); path.lineTo(240,235); path.lineTo(240,302); path.lineTo(0,302); path.close(); p.setColor(0xff281d42); output.drawPath(path,p);
                path.reset(); path.moveTo(0,261); path.cubicTo(75,224,140,290,240,245); path.lineTo(240,302); path.lineTo(0,302); path.close(); p.setColor(0xff17162d); output.drawPath(path,p);
                text(output,"L E O",120,22,10,0xffdac8ef,Paint.Align.CENTER);
                text(output,time,120,86,54,Color.WHITE,Paint.Align.CENTER);
                text(output,date,120,108,13,0xfff0e5f5,Paint.Align.CENTER);
                for(int slot=0;slot<2;slot++) {
                    String label = simLabels[slot]; p.setTypeface(regular); p.setTextSize(11);
                    while(label.length()>1 && p.measureText(label)>220) label=label.substring(0,label.length()-2)+"…";
                    text(output,label,120,128+slot*15,11,0xffe4cdec,Paint.Align.CENTER);
                }
                round(output,88,214,152,233,9,0x26ffffff);
                text(output,"PROTOTIP",120,227,9,0xffe5d7f5,Paint.Align.CENTER);
                text(output,"OK pentru aplicații",120,256,12,0xffc8bdd9,Paint.Align.CENTER);
            } else {
                p.setColor(0xc7161428); output.drawRect(0,0,240,276,p);
                text(output,names[selected],120,25,18,Color.WHITE,Paint.Align.CENTER);
                for(int i=0;i<9;i++) {
                    float x=40+(i%3)*80, y=67+(i/3)*76;
                    if(i==selected) round(output,x-34,y-29,x+34,y+39,10,0xfff5eefb);
                    icon(output,i,x,y);
                    text(output,names[i],x,y+33,10,i==selected?0xff38224f:0xffe8e1f1,Paint.Align.CENTER);
                }
                for(int i=0;i<3;i++) {p.setColor(i==selected/3?0xffe0b8ff:0xff64536f); output.drawCircle(110+i*10,267,2,p);}
            }
            if(!message.isEmpty()) {
                round(output,5,240,235,274,6,0xff302339);
                text(output,message,120,261,12,Color.WHITE,Paint.Align.CENTER);
            }
            p.setColor(0xff100f1d); output.drawRect(0,276,240,302,p);
            text(output,"Opțiuni",8,293,11,Color.WHITE,Paint.Align.LEFT);
            text(output,grid?"Deschide":"Meniu",120,293,12,0xffdfbdff,Paint.Align.CENTER);
            text(output,grid?"Înapoi":"Ieșire",232,293,11,Color.WHITE,Paint.Align.RIGHT);
            output.restore();
        }
    }
}
