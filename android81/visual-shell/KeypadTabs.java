package ro.leo.visual;
import android.view.*;import android.widget.*;import android.graphics.Color;
/** Shared focus and key rules for tab strips, including after a mouse click. */
final class KeypadTabs {
    static int next(int current,int delta,int count){return count<=0?0:Math.floorMod(current+delta,count);}
    static LinearLayout window(final LeoActivity owner,final String[] labels,final int selected,
                               final LeoActivity.Choice choose,Runnable down){
        LinearLayout bar=create(owner,new String[]{
            "‹ "+labels[next(selected,-1,labels.length)],labels[selected],
            labels[next(selected,1,labels.length)]+" ›"
        },1,new LeoActivity.Choice(){public void pick(int offset){
            choose.pick(next(selected,offset-1,labels.length));
        }},down);
        bar.setPadding(owner.dp(6),0,owner.dp(6),0);
        float scale=Appearance.textScale(owner);
        for(int index=0;index<bar.getChildCount();index++){
            TextView tab=(TextView)bar.getChildAt(index);
            boolean active=index==1;
            tab.setHorizontallyScrolling(false);
            tab.setAutoSizeTextTypeUniformWithConfiguration(active?15:12,
                Math.round((active?19:15)*scale),1,android.util.TypedValue.COMPLEX_UNIT_SP);
            tab.getLayoutParams().height=owner.dp(40);
            tab.setPadding(owner.dp(2),0,owner.dp(2),owner.dp(3));
            if(active){
                tab.setTypeface(UiChrome.TITLE);
                ((LinearLayout.LayoutParams)tab.getLayoutParams()).weight=1.18f;
            }
        }
        return bar;
    }
    static LinearLayout create(final LeoActivity owner,String[] labels,int selected,final LeoActivity.Choice choose,final Runnable down){
        LinearLayout bar=new LinearLayout(owner);bar.setBackgroundColor(owner.palette.bar);
        for(int i=0;i<labels.length;i++){
            final int index=i;TextView tab=owner.text(labels[i],13);tab.setPadding(0,0,0,0);tab.setGravity(Gravity.CENTER);tab.setSingleLine();tab.setEllipsize(android.text.TextUtils.TruncateAt.END);tab.setFocusableInTouchMode(true);
            tab.setSelected(i==selected);
            tab.setTextSize((i==selected?19:16)*Appearance.textScale(owner));
            UiChrome.tab(tab,owner.palette,i==selected,owner.dp(1));
            tab.setOnClickListener(new View.OnClickListener(){public void onClick(View v){choose.pick(index);}});
            tab.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent e){int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){v.requestFocus();v.setPressed(true);return true;}if(action==MotionEvent.ACTION_UP){v.setPressed(false);if(e.getX()>=0&&e.getX()<v.getWidth()&&e.getY()>=0&&e.getY()<v.getHeight())v.performClick();return true;}if(action==MotionEvent.ACTION_CANCEL)v.setPressed(false);return true;}});
            tab.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent e){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN)down.run();return true;}return false;}});
            bar.addView(tab,new LinearLayout.LayoutParams(0,owner.dp(34),1));
        }
        owner.registerTabs(bar);
        return bar;
    }
    static void focusList(LeoActivity owner){for(int i=0;i<owner.root.getChildCount();i++)if(owner.root.getChildAt(i) instanceof ListView){owner.root.getChildAt(i).requestFocus();return;}}
}
