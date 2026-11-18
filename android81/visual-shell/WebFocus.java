package ro.leo.visual;

import android.os.SystemClock;
import android.view.*;
import android.webkit.*;

/** Cycle focusable controls in the current document; never reads field contents. */
final class WebFocus {
    static void move(final WebView web,final boolean previous,final boolean first){
        web.post(new Runnable(){int attempts;public void run(){if(web.getParent()==null)return;if(!web.hasWindowFocus()){if(++attempts<20)web.postDelayed(this,20);return;}focus(web,previous,first);}});
    }
    private static void focus(final WebView web,final boolean previous,boolean first){
        if(!web.getSettings().getJavaScriptEnabled()){nativeTab(web,previous);return;}
        String script="(function(){var a=[].slice.call(document.querySelectorAll('a[href],area[href],button,input:not([type=hidden]),select,textarea,[contenteditable=true],[tabindex]')).filter(function(e){var s=getComputedStyle(e);return !e.disabled&&!(e.hasAttribute('tabindex')&&e.tabIndex<0)&&e.getClientRects().length&&s.visibility!=='hidden'&&s.display!=='none'});a.sort(function(x,y){var p=x.tabIndex>0?x.tabIndex:2147483647,q=y.tabIndex>0?y.tabIndex:2147483647;return p-q||(x===y?0:(x.compareDocumentPosition(y)&4)?-1:1)});if(!a.length)return false;var i="+(first?"-1":"a.indexOf(document.activeElement)")+",e=a["+(previous?"i<0?a.length-1:(i+a.length-1)%a.length":"(i+1)%a.length")+"];e.focus();e.scrollIntoView(false);return document.activeElement===e})()";
        web.evaluateJavascript(script,new ValueCallback<String>(){public void onReceiveValue(String value){if(web.getParent()!=null&&!"true".equals(value))nativeTab(web,previous);}});
    }
    private static void nativeTab(WebView web,boolean previous){
        if(!web.hasFocus())web.requestFocusFromTouch();
        long now=SystemClock.uptimeMillis();int meta=previous?KeyEvent.META_SHIFT_ON:0;
        for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP})web.dispatchKeyEvent(new KeyEvent(now,now,action,KeyEvent.KEYCODE_TAB,0,meta,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD));
    }
}
