package ro.leo.visual;
import android.text.*;import android.text.style.*;import java.util.*;
/** Small per-screen cache; styled rows are built once, not on every D-pad bind. */
final class UiRowCache {
 private final LinkedHashMap<String,CharSequence> rows=new LinkedHashMap<String,CharSequence>(32,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,CharSequence> e){return size()>128;}};
 int builds;
 CharSequence get(String value){CharSequence hit=rows.get(value);if(hit!=null)return hit;SpannableString styled=new SpannableString(value);int split=value.indexOf('\n');if(split>=0&&split<value.length()-1){styled.setSpan(new RelativeSizeSpan(.82f),split+1,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),0,split,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}rows.put(value,styled);builds++;return styled;}
 int size(){return rows.size();}
}
