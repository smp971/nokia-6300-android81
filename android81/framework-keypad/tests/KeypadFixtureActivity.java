package ro.leo.systemtests;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

public final class KeypadFixtureActivity extends Activity {
    LinearLayout root;
    Button first, second;
    EditText editor;
    ListView list;
    int clicks;

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        first.setText(intent.getBooleanExtra("notification_probe",false)?"LOCAL NOTIFICATION OPENED":"First");
    }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setFocusableInTouchMode(true);
        first = new Button(this);
        first.setText(getIntent().getBooleanExtra("notification_probe",false)?"LOCAL NOTIFICATION OPENED":"First");
        second = new Button(this);
        second.setText("Second");
        second.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { clicks++; }
        });
        editor = new EditText(this);
        editor.setSingleLine(true);
        editor.setText("Fixture text");
        list = new ListView(this);
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                new String[]{"List one", "List two"}));
        root.addView(first);
        root.addView(second);
        root.addView(editor);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.requestFocus();
    }
}
