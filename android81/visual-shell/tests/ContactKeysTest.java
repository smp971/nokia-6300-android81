package ro.leo.visual;
public final class ContactKeysTest {
    public static void main(String[] args){
        if(!ContactKeys.t9("Ștefan Țară").equals("7833268272"))throw new AssertionError("Romanian T9");
        if(!ContactKeys.t9("ĂÂÎșţ").equals("22478"))throw new AssertionError("diacritics");
        if(!ContactKeys.matches("Ștefan", "+40 (700) 000-001","783"))throw new AssertionError("name prefix");
        if(!ContactKeys.matches("Ana", "+40 (700) 000-001","0001"))throw new AssertionError("phone substring");
        if(ContactKeys.matches("Ana","123","999")||ContactKeys.matches(null,null,"")||!ContactKeys.t9(null).isEmpty())throw new AssertionError("negative / null");
        System.out.println("PASS T9 Romanian names, phone normalization and empty input");
    }
}
