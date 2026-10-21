package ro.leo.keypad;
import java.util.*;
public final class KeypadAlgorithms {
 static int checks;static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 public static void main(String[] args)throws Exception{
  check(PredictiveDictionary.digits("ȘȚĂÂÎ").equals("78224"),"Romanian diacritics normalized");
  check(PredictiveDictionary.digits("şţ").equals("78"),"legacy cedilla supported");
  check(PredictiveDictionary.matches("1").isEmpty(),"punctuation digit is not a prediction");
  check(PredictiveDictionary.candidates("").isEmpty(),"empty sequence has no invented text");
  check(PredictiveDictionary.matches(PredictiveDictionary.digits("temperatură")).contains("temperatură"),"expanded original dictionary");
  check(PredictiveDictionary.cased("țară",3,true).equals("Țară"),"sentence case keeps Romanian accent");
  check(PredictiveDictionary.cased("țară",4,false).equals("ȚARĂ"),"explicit uppercase mode retained");
  PersonalWords p=new PersonalWords();check(!p.add("a@b.ro")&&!p.add("123")&&!p.add("🙂"),"addresses digits and emoji excluded");
  check(p.add("Sâmbure")&&p.words().contains("sâmbure"),"local additions canonical lowercase");
  check(p.candidates(PredictiveDictionary.digits("sâmbure")).get(0).equals("sâmbure"),"personal exact word ranks first");
  for(String word:new String[]{"aaa","aab","aac","aba","abb","abc","aca","acb","acc","baa","bab","bac","bba"})p.add(word);
  for(int i=0;i<40;i++)p.add("bba");check(p.candidates("222").get(0).equals("bba"),"learned collision beyond twelve is not lost");
  for(int i=0;i<30;i++)p.add("fabulos");List<String> candidates=p.candidates("32");check(candidates.indexOf("da")>=0&&candidates.indexOf("fabulos")>candidates.indexOf("da"),"exact built-in beats learned completion");
  check(p.candidates("222").size()<=12,"suggestion strip bounded");
  int frequency=p.count("bba");check(p.replace("bba","brad")&&p.count("brad")==frequency&&!p.words().contains("bba"),"rename preserves ranking");
  check(!p.replace("brad","aaa")&&p.words().contains("brad"),"rename refuses collision");
  p.resetRanking();check(p.count("brad")==1&&p.words().contains("sâmbure"),"reset keeps words");
  String saved=p.save();PersonalWords restored=new PersonalWords();restored.load(saved);check(restored.save().equals(saved),"backup format round trip unchanged");
  restored.load("bad@word\t20\nnormal\t99999\ncorupt\tx\n");check(restored.words().size()==1&&restored.count("normal")==999,"corrupt records ignored and frequency clamped");
  PersonalWords bounded=new PersonalWords();for(int i=0;i<256;i++){String word="z"+(char)('a'+i/26)+(char)('a'+i%26);check(bounded.add(word),"bounded fixture insert");}check(!bounded.add("capacitate")&&bounded.words().size()==256,"personal dictionary capacity retained");
  System.out.println("PASS keypad ranking, editing, privacy and capacity checks ("+checks+" assertions)");
 }
}
