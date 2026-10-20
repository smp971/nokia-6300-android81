package ro.leo.visual;
import java.util.*;
/** Bounded in-memory queue; no media I/O or Android dependencies. */
final class MusicQueue {
    final ArrayList<String> sources=new ArrayList<String>(),titles=new ArrayList<String>();
    int index=-1,repeat;boolean shuffle;private final Random random=new Random();private final HashSet<String> visited=new HashSet<String>();private final ArrayList<Integer> history=new ArrayList<Integer>();
    void set(List<String> uris,List<String> labels,int selected){sources.clear();titles.clear();visited.clear();history.clear();for(int i=0;i<Math.min(500,Math.min(uris.size(),labels.size()));i++){sources.add(uris.get(i));titles.add(labels.get(i));}index=sources.isEmpty()?-1:Math.max(0,Math.min(selected,sources.size()-1));}
    boolean advance(boolean forward,boolean automatic){
        if(index<0)return false;if(automatic&&repeat==2)return true;
        if(shuffle){
            if(!forward){if(history.isEmpty())return false;index=history.remove(history.size()-1);return true;}
            visited.add(sources.get(index));ArrayList<Integer> candidates=new ArrayList<Integer>();for(int i=0;i<sources.size();i++)if(!visited.contains(sources.get(i)))candidates.add(i);
            if(candidates.isEmpty()){if(repeat==0)return false;visited.clear();for(int i=0;i<sources.size();i++)if(i!=index||sources.size()==1)candidates.add(i);}
            history.add(index);if(history.size()>500)history.remove(0);index=candidates.get(random.nextInt(candidates.size()));return true;
        }
        int next=index+(forward?1:-1);if(next<0||next>=sources.size()){if(repeat==0)return false;next=Math.floorMod(next,sources.size());}index=next;return true;
    }

    boolean remove(int at){if(at<0||at>=sources.size())return false;history.clear();boolean current=at==index;sources.remove(at);titles.remove(at);if(sources.isEmpty())index=-1;else if(at<index)index--;else if(index>=sources.size())index=sources.size()-1;return current;}
    void move(int from,int to){if(from<0||to<0||from>=sources.size()||to>=sources.size()||from==to)return;history.clear();String uri=sources.remove(from),title=titles.remove(from);sources.add(to,uri);titles.add(to,title);if(index==from)index=to;else if(from<index&&to>=index)index--;else if(from>index&&to<=index)index++;}
    String mode(){return (shuffle?"Aleatoriu":"În ordine")+" · "+(repeat==2?"Repetă piesa":repeat==1?"Repetă lista":"Fără repetare");}
}
