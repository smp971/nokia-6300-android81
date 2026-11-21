package ro.leo.visual;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Remember logical sizes without retaining pages; a scale change preserves their views and drafts. */
final class UiTypography {
    private static final WeakHashMap<TextView,Size> sizes=new WeakHashMap<TextView,Size>();
    private static final class Size {
        final int minimum,maximum;
        Size(int minimum,int maximum){this.minimum=minimum;this.maximum=maximum;}
        void apply(TextView view,float scale){
            if(minimum==0)view.setTextSize(TypedValue.COMPLEX_UNIT_SP,maximum*scale);
            else view.setAutoSizeTextTypeUniformWithConfiguration(Math.max(1,Math.round(minimum*scale)),
                Math.max(1,Math.round(maximum*scale)),1,TypedValue.COMPLEX_UNIT_SP);
        }
    }
    static void text(TextView view,int size){set(view,new Size(0,size));}
    static void fit(TextView view,int minimum,int maximum){set(view,new Size(minimum,maximum));}
    private static void set(TextView view,Size size){sizes.put(view,size);size.apply(view,Appearance.textScale(view.getContext()));}
    static void refresh(View view,float scale){
        if(view instanceof TextView){Size size=sizes.get(view);if(size!=null)size.apply((TextView)view,scale);}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)refresh(group.getChildAt(i),scale);}
    }
}
