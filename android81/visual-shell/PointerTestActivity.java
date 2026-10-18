package ro.leo.visual;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

/** Private local click target for checking gesture injection without touching real apps. */
public final class PointerTestActivity extends Activity {
    int clicks;
    public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root=new FrameLayout(this);
        final Button button=new Button(this); button.setText("Test cursor : 0");
        root.addView(button,new FrameLayout.LayoutParams(-1,100,Gravity.CENTER));
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { clicks++; button.setText("Test cursor : "+clicks); Log.i("LeoPointerTest","clicks="+clicks); }
        });
        setContentView(root);
        Log.i("LeoPointerTest","pointer ready="+LeoPointerService.startCenteredTest());
    }
}
