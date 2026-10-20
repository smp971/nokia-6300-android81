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
 List<String> candidates(final String sequence){final ArrayList<String> result=new ArrayList<String>();if(sequence.isEmpty())return result;for(String word:PredictiveDictionary.candidates(sequence))if(!word.equals(sequence))result.add(word);for(String word:words.keySet())if(PredictiveDictionary.digits(word).startsWith(sequence)&&!result.contains(word))result.add(word);Collections.sort(result,new Comparator<String>(){public int compare(String a,String b){boolean ae=PredictiveDictionary.digits(a).equals(sequence),be=PredictiveDictionary.digits(b).equals(sequence);if(ae!=be)return ae?-1:1;return Integer.compare(words.containsKey(b)?words.get(b):0,words.containsKey(a)?words.get(a):0);}});if(result.size()>12)return new ArrayList<String>(result.subList(0,12));if(result.isEmpty()&&!sequence.isEmpty())result.add(sequence);return result;}
}
