package ro.leo.keypad;
import java.util.*;
/** Private, bounded word preferences; no editor text or addresses are read. */
final class PersonalWords {
 private final LinkedHashMap<String,Integer> words=new LinkedHashMap<String,Integer>();
 static String clean(String word){if(word==null)return "";String s=word.trim().toLowerCase(new Locale("ro"));if(s.length()<2||s.length()>32)return "";for(int i=0;i<s.length();i++)if(!Character.isLetter(s.charAt(i)))return "";return PredictiveDictionary.digits(s).isEmpty()?"":s;}
 void load(String data){words.clear();for(String line:data.split("\n")){String[] row=line.split("\t");if(row.length!=2)continue;String word=clean(row[0]);try{if(!word.isEmpty()&&words.size()<256)words.put(word,Math.max(1,Math.min(999,Integer.parseInt(row[1]))));}catch(NumberFormatException ignored){}}}
 boolean add(String raw){String word=clean(raw);if(word.isEmpty()||(!words.containsKey(word)&&words.size()>=256))return false;words.put(word,Math.min(999,words.containsKey(word)?words.get(word)+1:1));return true;}
 void remove(String word){words.remove(word);}
 List<String> words(){return new ArrayList<String>(words.keySet());}
 String save(){StringBuilder out=new StringBuilder();for(Map.Entry<String,Integer> e:words.entrySet())out.append(e.getKey()).append('\t').append(e.getValue()).append('\n');return out.toString();}
 int count(String word){Integer n=words.get(word);return n==null?0:n;}
 boolean replace(String before,String after){String next=clean(after);if(!words.containsKey(before)||next.isEmpty()||!before.equals(next)&&words.containsKey(next))return false;int n=count(before);words.remove(before);words.put(next,n);return true;}
 void resetRanking(){for(String word:new ArrayList<String>(words.keySet()))words.put(word,1);}
 List<String> candidates(final String sequence){
  final ArrayList<String> result=new ArrayList<String>(PredictiveDictionary.matches(sequence));
  if(sequence==null||!sequence.matches("[2-9]{1,32}"))return result;
  final HashMap<String,String> codes=new HashMap<String,String>();
  for(String word:words.keySet())if(PredictiveDictionary.digits(word).startsWith(sequence)&&!result.contains(word))result.add(word);
  final HashMap<String,Integer> order=new HashMap<String,Integer>();
  for(int i=0;i<result.size();i++){String word=result.get(i);codes.put(word,PredictiveDictionary.digits(word));order.put(word,i);}
  Collections.sort(result,new Comparator<String>(){public int compare(String a,String b){
   boolean ae=codes.get(a).equals(sequence),be=codes.get(b).equals(sequence);if(ae!=be)return ae?-1:1;
   int frequency=Integer.compare(count(b),count(a));if(frequency!=0)return frequency;
   int length=Integer.compare(codes.get(a).length(),codes.get(b).length());return length!=0?length:Integer.compare(order.get(a),order.get(b));
  }});
  if(result.size()>12)return new ArrayList<String>(result.subList(0,12));
  if(result.isEmpty())result.add(sequence);return result;
 }
}
