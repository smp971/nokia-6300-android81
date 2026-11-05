package ro.leo.visual;

import android.graphics.*;

/** Compact shared chrome; callers retain their own content and navigation. */
final class UiChrome {
    static final int HEADER_DP=36, FOOTER_DP=28;
    static final Typeface TITLE=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    /** Reusable opaque surfaces: no shadows, bitmap layers or animation timers. */
    static android.graphics.drawable.Drawable panel(final Appearance.Palette a,final float density,final boolean footer){
        return new android.graphics.drawable.Drawable(){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            public void draw(Canvas c){Rect b=getBounds();if(a.material()){p.setColor(footer?a.bar:a.background);c.drawRect(b,p);if(footer){p.setColor(0xff49454f);c.drawRect(b.left,b.top,b.right,b.top+density,p);}return;}if(a.sxmo()||a.classic()){p.setColor(a.bar);c.drawRect(b,p);return;}p.setColor(footer?a.bar:a.card);c.drawRect(b,p);
                p.setColor(Appearance.mix(a.card,a.accent,.25f));
                if(footer)c.drawRect(b.left,b.top,b.right,b.top+density,p);
                else c.drawRect(b.left+10*density,b.bottom-density,b.left+34*density,b.bottom,p);
            }
            public void setAlpha(int v){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
        };
    }
    static android.graphics.drawable.Drawable row(Appearance.Palette a,int radius,int stripe){
        if(a.material()){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();android.graphics.drawable.GradientDrawable focus=Appearance.surface(a.selection,Math.max(radius,8));focus.setStroke(Math.max(1,stripe),a.accent);states.addState(new int[]{android.R.attr.state_selected},focus);states.addState(new int[]{},Appearance.surface(a.background,0));return states;}
        if(a.classic()){android.graphics.drawable.StateListDrawable classic=new android.graphics.drawable.StateListDrawable();android.graphics.drawable.GradientDrawable focus=Appearance.surface(a.selection,2);focus.setStroke(1,0xff73baff);classic.addState(new int[]{android.R.attr.state_selected},focus);classic.addState(new int[]{},Appearance.surface(a.background,0));return classic;}
        if(a.sxmo()){android.graphics.drawable.StateListDrawable flat=new android.graphics.drawable.StateListDrawable();flat.addState(new int[]{android.R.attr.state_selected},Appearance.surface(a.selection,0));flat.addState(new int[]{},Appearance.surface(a.background,0));return flat;}
        android.graphics.drawable.GradientDrawable focus=Appearance.surface(a.selection,radius);
        focus.setStroke(stripe,Appearance.mix(a.selection,a.accent,.55f));
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected},new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{focus,new FocusRail(a.accent,stripe)}));
        states.addState(new int[]{},Appearance.surface(a.card,radius));return states;
    }
    private static final class FocusRail extends android.graphics.drawable.Drawable{
        final Paint p=new Paint(3);final int width;
        FocusRail(int color,int width){p.setColor(color);this.width=Math.max(1,width)*3;}
        public void draw(Canvas c){Rect b=getBounds();c.drawRoundRect(b.left+width,b.top+8,b.left+width*2,b.bottom-8,width/2f,width/2f,p);}
        public void setAlpha(int v){p.setAlpha(v);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    static void header(Canvas c,String title,Appearance.Palette a,Paint p){
        Typeface previous=p.getTypeface();
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(a.material()?a.background:a.card);c.drawRect(0,0,240,34,p);
        p.setColor(a.accent);if(!a.material())c.drawRoundRect(10,10,13,24,1.5f,1.5f,p);p.setTypeface(TITLE);p.setTextAlign(Paint.Align.LEFT);p.setTextSize(17);p.setColor(Color.WHITE);
        String value=title;while(value.length()>1&&p.measureText(value)>210)value=value.substring(0,value.length()-1);
        if(!value.equals(title)&&value.length()>1)value=value.substring(0,value.length()-1)+"…";
        c.drawText(value,a.material()?12:21,24,p);
        p.setTypeface(previous);
    }
}
