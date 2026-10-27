package ro.leo.visual;

import ro.leo.i18n.L;
import android.content.Intent;
import android.os.Bundle;
import java.util.Arrays;

/** Explicit access to the actual Android panels, preserving the Leo alternatives. */
public final class AndroidControlsActivity extends LeoActivity {
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        list(L.t("Android din taste"), Arrays.asList(
                L.t("Notificări Android\nPanoul de sistem · D-pad și OK"),
                L.t("Configurări rapide Android\nStânga / dreapta schimbă panoul"),
                L.t("Setări Android\nSoft stânga: acțiuni Android / Leo"),
                L.t("Notificări Leo\nGrupuri și acțiuni detaliate"),
                L.t("Volum Android\nD-pad selectează · stânga/dreapta reglează")), 0, new Choice() {
            public void pick(int at) {
                if(at==4){((android.media.AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.ADJUST_SAME,android.media.AudioManager.FLAG_SHOW_UI);return;}
                if (at < 2) {
                    if (!LeoPointerService.openAndroidPanel(at == 1)) {
                        notice(L.t("Serviciul tastelor trebuie să fie conectat"));
                    }
                } else if (at == 2) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                                .setPackage("com.android.settings"));
                    } catch (RuntimeException missing) {
                        notice(L.t("Setări Android indisponibile"));
                    }
                } else startActivity(new Intent(AndroidControlsActivity.this, NotificationsActivity.class));
            }
        }, "", null);
    }
}
