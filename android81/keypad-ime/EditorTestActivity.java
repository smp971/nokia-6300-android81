package ro.leo.keypad;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Local editor with no save/send actions; test text is never logged. */
public final class EditorTestActivity extends Activity {
    EditText text, number;
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,8,10,0);
        TextView title=new TextView(this); title.setText("Test multi-tap\n2–9: litere · 0: spațiu\n#: abc / ABC / 123\nSoft dreapta: ștergere"); title.setTextSize(14); root.addView(title);
        text=new EditText(this); text.setHint("Scrie aici"); text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); text.setMinLines(2); root.addView(text);
        number=new EditText(this); number.setHint("Probă numerică"); number.setInputType(InputType.TYPE_CLASS_PHONE); root.addView(number);
        setContentView(root); text.requestFocus();
        text.postDelayed(new Runnable() { public void run() {
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(text,InputMethodManager.SHOW_IMPLICIT);
        }},300);
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String field=intent.getStringExtra("field");
        if(field!=null) {
            EditText editor="numeric".equals(field)?number:text;
            editor.setText(""); editor.requestFocus();
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).restartInput(editor);
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);
        }
        String expected=intent.getStringExtra("expect");
        if(expected!=null) {
            String id=intent.getStringExtra("test_id");
            EditText editor=number.hasFocus()?number:text;
            // Test metadata only. Never log the field contents or expected text.
            Log.i("LeoImeTest","case="+id+" passed="+expected.contentEquals(editor.getText()));
        }
    }
}
