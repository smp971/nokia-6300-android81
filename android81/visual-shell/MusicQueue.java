package ro.leo.visual;
import ro.leo.i18n.L;
import java.util.*;
/** Bounded in-memory queue; no media I/O or Android dependencies. */
final class MusicQueue {
    static final int LIMIT=500;
    final ArrayList<String> sources=new ArrayList<String>(),titles=new ArrayList<String>();
    int index=-1,repeat;boolean shuffle;private final Random random=new Random();private final HashSet<String> visited=new HashSet<String>();private final ArrayList<Integer> history=new ArrayList<Integer>();
    private final ArrayList<Integer> playNext=new ArrayList<Integer>();
    void set(List<String> uris,List<String> labels,int selected){sources.clear();titles.clear();visited.clear();history.clear();playNext.clear();for(int i=0;i<Math.min(LIMIT,Math.min(uris.size(),labels.size()));i++){sources.add(uris.get(i));titles.add(labels.get(i));}index=sources.isEmpty()?-1:Math.max(0,Math.min(selected,sources.size()-1));}

    boolean enqueue(String source,String title,boolean next) {
        if(sources.size()>=LIMIT)return false;
        int at=next&&index>=0?index+1:sources.size();
        for(int i=0;i<history.size();i++)if(history.get(i)>=at)history.set(i,history.get(i)+1);
        for(int i=0;i<playNext.size();i++)if(playNext.get(i)>=at)playNext.set(i,playNext.get(i)+1);
        sources.add(at,source);
        titles.add(at,title);
        visited.remove(source);
        if(index<0)index=0;
        else if(next)playNext.add(0,at);
        return true;
    }

    private void rememberCurrent() {
        history.add(index);
        if(history.size()>LIMIT)history.remove(0);
    }

    boolean advance(boolean forward,boolean automatic){
        if(index<0)return false;
        playNext.remove(Integer.valueOf(index));
        if(forward&&!playNext.isEmpty()){
            if(shuffle){visited.add(sources.get(index));rememberCurrent();}
            index=playNext.remove(0);
            return true;
        }
        if(automatic&&repeat==2)return true;
        if(shuffle){
            if(!forward){if(history.isEmpty())return false;index=history.remove(history.size()-1);return true;}
            visited.add(sources.get(index));ArrayList<Integer> candidates=new ArrayList<Integer>();for(int i=0;i<sources.size();i++)if(!visited.contains(sources.get(i)))candidates.add(i);
            if(candidates.isEmpty()){if(repeat==0)return false;visited.clear();for(int i=0;i<sources.size();i++)if(i!=index||sources.size()==1)candidates.add(i);}
            rememberCurrent();index=candidates.get(random.nextInt(candidates.size()));return true;
        }
        int next=index+(forward?1:-1);if(next<0||next>=sources.size()){if(repeat==0)return false;next=Math.floorMod(next,sources.size());}index=next;return true;
    }

    boolean remove(int at){if(at<0||at>=sources.size())return false;history.clear();playNext.remove(Integer.valueOf(at));for(int i=0;i<playNext.size();i++)if(playNext.get(i)>at)playNext.set(i,playNext.get(i)-1);boolean current=at==index;sources.remove(at);titles.remove(at);if(sources.isEmpty())index=-1;else if(at<index)index--;else if(index>=sources.size())index=sources.size()-1;return current;}
    void move(int from,int to){if(from<0||to<0||from>=sources.size()||to>=sources.size()||from==to)return;history.clear();playNext.clear();String uri=sources.remove(from),title=titles.remove(from);sources.add(to,uri);titles.add(to,title);if(index==from)index=to;else if(from<index&&to>=index)index--;else if(from>index&&to<=index)index++;}
    String mode(){return (shuffle?L.t("Aleatoriu"):L.t("În ordine"))+" · "+(repeat==2?L.t("Repetă piesa"):repeat==1?L.t("Repetă lista"):L.t("Fără repetare"));}
}
