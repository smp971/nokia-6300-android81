package ro.leo.visual;
import android.text.*;import android.text.style.*;import android.graphics.Typeface;
/** Small offline presentation subset. Links/scripts are never executed. */
final class MarkdownText {
    static CharSequence display(String source){SpannableStringBuilder out=new SpannableStringBuilder();boolean code=false;for(String raw:source.split("\n",-1)){if(raw.startsWith("```")){code=!code;continue;}String line=raw;int heading=0;while(heading<6&&heading<line.length()&&line.charAt(heading)=='#')heading++;boolean title=!code&&heading>0&&line.length()>heading&&line.charAt(heading)==' ';if(title)line=line.substring(heading+1);else if(!code&&line.startsWith("- "))line="• "+line.substring(2);int start=out.length();out.append(line).append('\n');if(title){out.setSpan(new StyleSpan(Typeface.BOLD),start,start+line.length(),33);out.setSpan(new RelativeSizeSpan(heading<3?1.2f:1.1f),start,start+line.length(),33);}if(code)out.setSpan(new TypefaceSpan("monospace"),start,start+line.length(),33);}return out;}
}
