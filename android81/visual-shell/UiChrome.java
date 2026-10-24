package ro.leo.visual;

import android.graphics.*;

/** Compact shared chrome; callers retain their own content and navigation. */
final class UiChrome {
    static final int HEADER_DP=36, FOOTER_DP=28;
    static final Typeface TITLE=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    static void header(Canvas c,String title,Appearance.Palette a,Paint p){
        Typeface previous=p.getTypeface();
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(a.card);c.drawRect(0,0,240,34,p);
        p.setColor(a.accent);c.drawRect(0,33,240,34,p);p.setTypeface(TITLE);p.setTextAlign(Paint.Align.LEFT);p.setTextSize(18);p.setColor(Color.WHITE);
        String value=title;while(value.length()>1&&p.measureText(value)>216)value=value.substring(0,value.length()-1);
        if(!value.equals(title)&&value.length()>1)value=value.substring(0,value.length()-1)+"…";
        c.drawText(value,12,24,p);
        p.setTypeface(previous);
    }
}
