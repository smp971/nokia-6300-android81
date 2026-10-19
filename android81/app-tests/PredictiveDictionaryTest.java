package ro.leo.keypad;
import java.util.*;
public final class PredictiveDictionaryTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        check(PredictiveDictionary.digits("salut").equals("72588"));check(PredictiveDictionary.digits("ȘȚĂÂÎ").equals("78224"));
        check(PredictiveDictionary.digits("ÎNAPOI").equals("462764"));check(PredictiveDictionary.digits("a/b").isEmpty());
        check(PredictiveDictionary.candidates("72588").contains("salut"));check(PredictiveDictionary.candidates("685886372").contains("mulțumesc"));
        check(PredictiveDictionary.candidates("").isEmpty());check(PredictiveDictionary.candidates("999999999999").equals(Arrays.asList("999999999999")));
        for(String word:new String[]{"bună","mesaj","acasă","școală","silențios","noiembrie","funcționează","română","înapoi","telefon","favorite"}){
            String sequence=PredictiveDictionary.digits(word);check(PredictiveDictionary.candidates(sequence).contains(word));
            for(int n=1;n<=sequence.length();n++){List<String> words=PredictiveDictionary.candidates(sequence.substring(0,n));check(words.size()<=12);for(String result:words)check(PredictiveDictionary.digits(result).startsWith(sequence.substring(0,n)));}
        }
        System.out.println("Predictive dictionary: "+checks+" checks passed");
    }
}
