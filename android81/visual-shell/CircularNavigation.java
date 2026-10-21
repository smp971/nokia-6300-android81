package ro.leo.visual;
import android.app.Activity;import android.view.*;import android.widget.ListView;
/** Wrap only focused lists, never text fields or a form's focus chain. */
final class CircularNavigation {
 static int step(int selected,int delta,int count){return count<=0?-1:Math.floorMod(selected+delta,count);}
 static boolean dispatch(Activity a,KeyEvent e){if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;int k=e.getKeyCode();if(k!=KeyEvent.KEYCODE_DPAD_UP&&k!=KeyEvent.KEYCODE_DPAD_DOWN)return false;View focus=a.getCurrentFocus();if(focus instanceof android.widget.EditText)return false;while(focus!=null&&!(focus instanceof ListView)){android.view.ViewParent p=focus.getParent();focus=p instanceof View?(View)p:null;}if(!(focus instanceof ListView))return false;ListView list=(ListView)focus;int count=list.getCount(),at=list.getSelectedItemPosition();if(count<1||at<0)return false;if((k==KeyEvent.KEYCODE_DPAD_UP&&at==0)||(k==KeyEvent.KEYCODE_DPAD_DOWN&&at==count-1)){int target=k==KeyEvent.KEYCODE_DPAD_UP?count-1:0;list.setSelection(target);return true;}return false;}
}
