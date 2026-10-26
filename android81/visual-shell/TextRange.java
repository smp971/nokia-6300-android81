package ro.leo.visual;
/** Positions count Unicode code points, so an emoji is never cut in half. */
final class TextRange {
    static String select(String text,int start,int end){int count=text.codePointCount(0,text.length());if(start<0||end<start||end>count)throw new IllegalArgumentException();return text.substring(text.offsetByCodePoints(0,start),text.offsetByCodePoints(0,end));}
}
