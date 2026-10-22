package ro.leo.visual;
import java.util.*;
/** Deterministic game rules, independent of Android and rendering. */
final class PocketGames {
 static final class Snake {
  final ArrayDeque<Integer> body=new ArrayDeque<Integer>();final Random random;int direction=1,queued=1,food,score;boolean over,won;
  Snake(long seed){random=new Random(seed);body.add(6*12+6);body.add(6*12+5);body.add(6*12+4);spawn();}
  void turn(int d){if(d>=0&&d<4&&d!=(direction+2)%4)queued=d;}
  void spawn(){if(body.size()==168){won=true;over=true;food=-1;return;}ArrayList<Integer> free=new ArrayList<Integer>();for(int n=0;n<168;n++)if(!body.contains(n))free.add(n);food=free.get(random.nextInt(free.size()));}
  void tick(){if(over)return;direction=queued;int head=body.peekFirst(),x=head%12,y=head/12;x+=direction==1?1:direction==3?-1:0;y+=direction==2?1:direction==0?-1:0;int next=y*12+x;boolean eat=next==food;if(x<0||x>=12||y<0||y>=14||body.contains(next)&&(eat||next!=body.peekLast())){over=true;return;}if(!eat)body.removeLast();body.addFirst(next);if(eat){score++;spawn();}}
 }
 static final class Sudoku {
  static final String BASE="530070000600195000098000060800060003400803001700020006060000280000419005000080079";
  final int[] given=new int[81],cells=new int[81];
  Sudoku(long seed){ArrayList<Integer> digits=new ArrayList<Integer>();for(int i=1;i<=9;i++)digits.add(i);Collections.shuffle(digits,new Random(seed));for(int i=0;i<81;i++){int n=BASE.charAt(i)-'0';given[i]=n==0?0:digits.get(n-1);cells[i]=given[i];}}
  boolean place(int at,int n){if(at<0||at>=81||n<0||n>9||given[at]!=0)return false;if(n!=0)for(int i=0;i<81;i++)if(i!=at&&cells[i]==n&&(i/9==at/9||i%9==at%9||i/27==at/27&&i%9/3==at%9/3))return false;cells[at]=n;return true;}
  boolean done(){for(int n:cells)if(n==0)return false;return true;}
  String save(){StringBuilder b=new StringBuilder();for(int n:given)b.append(n);b.append(':');for(int n:cells)b.append(n);return b.toString();}
  boolean restore(String data){if(data==null||!data.matches("[0-9]{81}:[0-9]{81}"))return false;int[] oldG=given.clone(),oldC=cells.clone();for(int i=0;i<81;i++){given[i]=data.charAt(i)-'0';cells[i]=0;}for(int i=0;i<81;i++){int n=data.charAt(82+i)-'0';if(given[i]!=0&&n!=given[i]){System.arraycopy(oldG,0,given,0,81);System.arraycopy(oldC,0,cells,0,81);return false;}cells[i]=n;}for(int i=0;i<81;i++)if(cells[i]!=0)for(int j=0;j<i;j++)if(cells[i]==cells[j]&&(i/9==j/9||i%9==j%9||i/27==j/27&&i%9/3==j%9/3)){System.arraycopy(oldG,0,given,0,81);System.arraycopy(oldC,0,cells,0,81);return false;}return true;}
 }
 static final class Tiles {
  final int[] cells=new int[16];int blank=15,moves;
  Tiles(long seed){for(int i=0;i<16;i++)cells[i]=(i+1)%16;Random r=new Random(seed);for(int i=0;i<200;i++)move(r.nextInt(4));moves=0;if(done())move(3);moves=0;}
  boolean move(int d){if(d<0||d>3)return false;int x=blank%4+(d==1?1:d==3?-1:0),y=blank/4+(d==2?1:d==0?-1:0);if(x<0||x>3||y<0||y>3)return false;int at=y*4+x;cells[blank]=cells[at];cells[at]=0;blank=at;moves++;return true;}
  boolean done(){for(int i=0;i<16;i++)if(cells[i]!=(i+1)%16)return false;return true;}
  String save(){StringBuilder b=new StringBuilder();for(int n:cells){if(b.length()>0)b.append(',');b.append(n);}return b+":"+moves;}
  boolean restore(String data){try{String[] pair=data.split(":");if(pair.length!=2)return false;String[] parts=pair[0].split(",");if(parts.length!=16)return false;int[] copy=new int[16];boolean[] seen=new boolean[16];int zero=-1;for(int i=0;i<16;i++){int n=Integer.parseInt(parts[i]);if(n<0||n>15||seen[n])return false;seen[n]=true;copy[i]=n;if(n==0)zero=i;}int inv=0;for(int i=0;i<16;i++)for(int j=i+1;j<16;j++)if(copy[i]!=0&&copy[j]!=0&&copy[i]>copy[j])inv++;if((inv+4-zero/4)%2!=1)return false;int count=Integer.parseInt(pair[1]);if(count<0||count>1000000)return false;System.arraycopy(copy,0,cells,0,16);blank=zero;moves=count;return true;}catch(Exception e){return false;}}
 }
}
