package ro.leo.visual;
import android.view.*;import android.widget.*;import android.graphics.Color;
/** Shared focus and key rules for tab strips, including after a mouse click. */
final class KeypadTabs {
    static int next(int current,int delta,int count){return count<=0?0:Math.floorMod(current+delta,count);}
    static LinearLayout create(final LeoActivity owner,String[] labels,int selected,final LeoActivity.Choice choose,final Runnable down){
        LinearLayout bar=new LinearLayout(owner);bar.setBackgroundColor(owner.palette.bar);
        for(int i=0;i<labels.length;i++){
            final int index=i;TextView tab=owner.text(labels[i],13);tab.setPadding(0,0,0,0);tab.setGravity(Gravity.CENTER);tab.setSingleLine();tab.setEllipsize(android.text.TextUtils.TruncateAt.END);tab.setFocusableInTouchMode(true);
            android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},PhoneTheme.surface(owner.palette,owner.palette.card,true));states.addState(new int[]{},Appearance.surface(i==selected?owner.palette.card:owner.palette.bar,owner.dp(5)));tab.setBackground(states);
            tab.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{owner.palette.ink,Color.WHITE}));
            tab.setOnClickListener(new View.OnClickListener(){public void onClick(View v){choose.pick(index);}});
            tab.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent e){int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){v.requestFocus();v.setPressed(true);return true;}if(action==MotionEvent.ACTION_UP){v.setPressed(false);if(e.getX()>=0&&e.getX()<v.getWidth()&&e.getY()>=0&&e.getY()<v.getHeight())v.performClick();return true;}if(action==MotionEvent.ACTION_CANCEL)v.setPressed(false);return true;}});
            tab.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent e){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(e.getAction()==KeyEvent.ACTION_DOWN)down.run();return true;}return false;}});
            bar.addView(tab,new LinearLayout.LayoutParams(0,owner.dp(28),1));
        }
        return bar;
    }
    static void focusList(LeoActivity owner){for(int i=0;i<owner.root.getChildCount();i++)if(owner.root.getChildAt(i) instanceof ListView){owner.root.getChildAt(i).requestFocus();return;}}
}
