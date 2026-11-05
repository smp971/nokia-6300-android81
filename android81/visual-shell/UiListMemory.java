package ro.leo.visual;
import android.widget.ListView;import android.view.View;import java.util.*;
/** Bounded per-activity list bookmarks. Nothing is persisted or logged. */
final class UiListMemory {
 static final class Entry {final String row;final int index,top;Entry(String r,int i,int t){row=r;index=i;top=t;}}
 private final LinkedHashMap<String,Entry> pages=new LinkedHashMap<String,Entry>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,UiListMemory.Entry> e){return size()>24;}};
 void remember(String title,List<String> rows,ListView list){if(title==null||rows==null||rows.isEmpty()||list==null)return;int selected=list.getSelectedItemPosition();View view=list.getSelectedView();if(selected<0){selected=list.getFirstVisiblePosition();view=list.getChildAt(0);}if(selected<0||selected>=rows.size())return;String value=rows.get(selected);if(value.length()>1024)return;pages.put(title,new Entry(value,selected,view==null?0:view.getTop()));}
 void remember(String title,List<String> rows,int selected,int top){if(title!=null&&selected>=0&&selected<rows.size()&&rows.get(selected).length()<=1024)pages.put(title,new Entry(rows.get(selected),selected,top));}
 Entry restore(String title,List<String> rows){Entry e=pages.get(title);if(e==null)return null;int index=e.index<rows.size()&&e.row.equals(rows.get(e.index))?e.index:rows.indexOf(e.row);return index<0?null:new Entry(e.row,index,e.top);}
 int size(){return pages.size();}
}
