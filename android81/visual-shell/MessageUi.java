package ro.leo.visual;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import ro.leo.i18n.L;

/** Recycled native message cards; no bitmaps, animation or retained message data. */
final class MessageUi {
    static final String[] TABS=L.array(new String[]{"Conversații","Ciorne","Arhivă"});
    static String time(long when){
        Calendar today=Calendar.getInstance(),date=Calendar.getInstance();date.setTimeInMillis(when);
        boolean sameYear=today.get(Calendar.YEAR)==date.get(Calendar.YEAR);
        String pattern=sameYear&&today.get(Calendar.DAY_OF_YEAR)==date.get(Calendar.DAY_OF_YEAR)?"HH:mm":sameYear?"dd MMM":"dd.MM.yy";
        return new SimpleDateFormat(pattern,L.locale()).format(new Date(when));
    }
    static GradientDrawable card(LeoActivity owner,boolean selected){
        GradientDrawable shape=Appearance.surface(selected?owner.palette.selection:owner.palette.card,owner.dp(10));
        shape.setStroke(owner.dp(selected?2:1),selected?owner.palette.accent:owner.palette.divider());return shape;
    }
    static final class Row extends LinearLayout {
        private final LeoActivity owner;
        private final ImageView avatar;
        private final TextView title,snippet,date,meta;
        private boolean unread;
        Row(LeoActivity owner){
            super(owner);this.owner=owner;setOrientation(HORIZONTAL);setGravity(Gravity.TOP);
            setPadding(owner.dp(10),owner.dp(9),owner.dp(10),owner.dp(9));setMinimumHeight(owner.dp(76));
            avatar=new ImageView(owner);LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(owner.dp(30),owner.dp(30));icon.rightMargin=owner.dp(9);addView(avatar,icon);
            LinearLayout content=new LinearLayout(owner);content.setOrientation(VERTICAL);addView(content,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout heading=new LinearLayout(owner);heading.setGravity(Gravity.CENTER_VERTICAL);content.addView(heading);
            title=owner.text("",19);title.setPadding(0,0,0,0);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            date=owner.text("",12);date.setPadding(owner.dp(5),0,0,0);date.setSingleLine();heading.addView(date);
            snippet=owner.text("",16);snippet.setPadding(0,owner.dp(3),0,owner.dp(4));snippet.setMaxLines(2);snippet.setEllipsize(TextUtils.TruncateAt.END);content.addView(snippet);
            meta=owner.text("",12);meta.setPadding(0,0,0,0);meta.setSingleLine();meta.setEllipsize(TextUtils.TruncateAt.END);content.addView(meta);
        }
        void bind(String name,String body,long when,String status,int slot,boolean unread,boolean pinned){
            this.unread=unread;
            title.setText((pinned?"★ ":"")+(unread?"● ":"")+name);
            title.setTypeface(unread?Typeface.DEFAULT_BOLD:UiChrome.TITLE);
            snippet.setText(body.replace('\n',' '));date.setText(time(when));meta.setText(status);
            meta.setCompoundDrawables(slot>=0&&slot<2?new SimIcon(slot,owner.dp(13),owner.palette.accent):null,null,null,null);meta.setCompoundDrawablePadding(owner.dp(4));
            avatar.setImageDrawable(new AvatarArt(name,owner.dp(30)));paint();
            setContentDescription(title.getText()+". "+body+". "+status);
        }
        @Override public void setSelected(boolean selected){super.setSelected(selected);if(title!=null)paint();}
        private void paint(){
            boolean selected=isSelected();int primary=selected?owner.palette.ink:Color.WHITE;
            int secondary=selected?Appearance.mix(owner.palette.ink,owner.palette.selection,.22f):owner.palette.secondary();
            setBackground(card(owner,selected));title.setTextColor(primary);snippet.setTextColor(primary);date.setTextColor(secondary);meta.setTextColor(unread&&!selected?owner.palette.accent:secondary);
        }
    }
    static LinearLayout empty(LeoActivity owner,int page){
        LinearLayout box=new LinearLayout(owner);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(owner.dp(22),owner.dp(14),owner.dp(22),owner.dp(14));
        TextView title=owner.text(page==1?L.t("Nicio ciornă"):page==2?L.t("Arhiva este goală"):L.t("Nicio conversație"),21);title.setTypeface(UiChrome.TITLE);title.setGravity(Gravity.CENTER);box.addView(title);
        TextView detail=owner.text(page==1?L.t("Mesajele netrimise se păstrează aici."):page==2?L.t("Conversațiile arhivate apar aici."):L.t("Începe un SMS nou sau schimbă filtrul."),15);detail.setTextColor(owner.palette.secondary());detail.setGravity(Gravity.CENTER);box.addView(detail);
        owner.root.addView(box,new LinearLayout.LayoutParams(-1,0,1));
        return box;
    }
}
