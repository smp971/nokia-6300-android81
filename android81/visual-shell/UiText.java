package ro.leo.visual;
/** Bounded, Unicode-safe secondary text for compact keypad rows. */
final class UiText {
 static String preview(String value,int max){if(value==null||max<=0)return "";StringBuilder out=new StringBuilder();boolean gap=false;for(int at=0;at<value.length();){int cp=value.codePointAt(at);at+=Character.charCount(cp);if(Character.isWhitespace(cp)||Character.isISOControl(cp)){gap=out.length()>0;continue;}if(gap){out.append(' ');gap=false;}out.appendCodePoint(cp);if(out.codePointCount(0,out.length())>max){out.setLength(out.offsetByCodePoints(0,Math.max(0,max-1)));out.append('…');break;}}return out.toString();}
 static String row(String value){if(value==null)return "";int split=value.indexOf('\n');return split<0?preview(value,300):preview(value.substring(0,split),150)+"\n"+preview(value.substring(split+1),300);}
}
