package ro.leo.visual;
import java.util.*;
public final class MusicQueueTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){MusicQueue q=new MusicQueue();check(!q.advance(true,true),"empty queue");q.set(Arrays.asList("a","b","c"),Arrays.asList("A","B","C"),0);check(q.advance(true,true)&&q.index==1,"automatic next");q.repeat=2;check(q.advance(true,true)&&q.index==1,"repeat one");check(q.advance(true,false)&&q.index==2,"manual next bypasses repeat one");q.repeat=0;check(!q.advance(true,true),"stop at end");q.repeat=1;check(q.advance(true,true)&&q.index==0,"repeat all wraps");q.move(0,2);check(q.index==2&&q.sources.get(2).equals("a"),"move preserves current song");check(!q.remove(0)&&q.index==1&&q.sources.get(1).equals("a"),"remove earlier preserves song");check(q.remove(1)&&q.index==0,"remove current selects remaining");q.remove(0);check(q.index==-1&&!q.advance(false,false),"remove final item");
        for(int run=0;run<50;run++){q.set(Arrays.asList("a","b","c"),Arrays.asList("A","B","C"),0);q.shuffle=true;q.repeat=0;HashSet<Integer> played=new HashSet<Integer>();played.add(q.index);while(q.advance(true,true))check(played.add(q.index),"shuffle must not repeat before exhaustion");check(played.size()==3,"shuffle visits every song");q.repeat=1;check(q.advance(true,true),"shuffle repeat-all starts another cycle");}
        q.set(Arrays.asList("a"),Arrays.asList("A"),99);q.repeat=1;check(q.index==0&&q.advance(true,true),"single-item shuffle repeat");System.out.println("PASS empty/end/repeat/shuffle exhaustion, queue move/remove and stable current song");
    }
}
