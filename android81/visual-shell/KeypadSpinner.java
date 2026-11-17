package ro.leo.visual;

import android.content.Context;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

/** Keeps Spinner selection and listeners while using the shared keypad chooser. */
final class KeypadSpinner extends Spinner {
    KeypadSpinner(Context context) {
        super(context);
        setFocusableInTouchMode(true);
    }

    @Override public boolean performClick() {
        if (!isEnabled()) return false;
        SpinnerAdapter adapter = getAdapter();
        if (adapter == null || adapter.getCount() == 0) return false;
        String[] labels = new String[adapter.getCount()];
        for (int index = 0; index < labels.length; index++)
            labels[index] = String.valueOf(adapter.getItem(index));
        CharSequence prompt = getPrompt();
        new KeypadDialog(getContext(), prompt == null ? ro.leo.i18n.L.t("Alege") : prompt.toString(),
                labels, new KeypadDialog.Action() {
            public void select(int index) {
                setSelection(index);
                requestFocus();
            }
        }).initialSelection(getSelectedItemPosition()).show();
        return true;
    }
}
