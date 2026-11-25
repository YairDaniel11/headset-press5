package com.headset.press5;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * הגדרת הפעולה לכל מספר הקשות: איזה מקש ילחץ בשיחה (או כבוי).
 * הבחירה נעשית בחלון רשימה משלנו ולא ב-Spinner של המערכת: ב-4.4 ה-Spinner יצא עם טקסט
 * לבן על רקע לבן, ובלי סימון ברור של השורה הנוכחית.
 */
public class SettingsActivity extends Activity {

    private final int[] buttonIds = {R.id.spTaps1, R.id.spTaps2, R.id.spTaps3, R.id.spTaps4};
    private final Button[] buttons = new Button[AppPrefs.MAX_TAPS];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.apply(this);
        setContentView(R.layout.activity_settings);

        for (int i = 0; i < buttons.length; i++) {
            final int taps = i + 1;
            Button b = (Button) findViewById(buttonIds[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPicker(taps);
                }
            });
            buttons[i] = b;
            if (i > 0) b.setNextFocusUpId(buttonIds[i - 1]);
            if (i < buttons.length - 1) b.setNextFocusDownId(buttonIds[i + 1]);
        }
        View reset = findViewById(R.id.btnResetRules);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppPrefs.resetRules(SettingsActivity.this);
                refresh();
            }
        });
        reset.setNextFocusUpId(buttonIds[buttonIds.length - 1]);
        buttons[buttons.length - 1].setNextFocusDownId(R.id.btnResetRules);
        refresh();
    }

    private static String optionLabel(int code) {
        return code == 0 ? "כבוי (לא עושה כלום)" : "מקש " + AppPrefs.keyLabel(code);
    }

    private void refresh() {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setText(optionLabel(AppPrefs.getKeyForTaps(this, i + 1)) + "   ▾");
        }
    }

    /** חלון בחירה בהיר. השורה הנוכחית מוקפת במסגרת, והערך שנבחר כרגע מסומן ב-V. */
    private void showPicker(final int taps) {
        final int current = AppPrefs.getKeyForTaps(this, taps);
        final String[] labels = new String[AppPrefs.KEY_CODES.length];
        int currentPos = 0;
        for (int i = 0; i < labels.length; i++) {
            int code = AppPrefs.KEY_CODES[i];
            boolean isCurrent = code == current;
            if (isCurrent) currentPos = i;
            labels[i] = (isCurrent ? "✓  " : "") + optionLabel(code);
        }

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Holo_Light_Dialog_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText(getString(taps == 1 ? R.string.settings_tap_1 : taps == 2 ? R.string.settings_tap_2
                : taps == 3 ? R.string.settings_tap_3 : R.string.settings_tap_4));
        title.setTextColor(0xFFFFFFFF);
        title.setBackgroundColor(0xFF0B3D7A);
        title.setTextSize(18);
        title.setPadding(16, 14, 16, 14);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ListView list = new ListView(this);
        list.setBackgroundColor(0xFFFFFFFF);
        list.setCacheColorHint(0xFFFFFFFF);
        list.setDivider(new ColorDrawable(0xFFDDDDDD));
        list.setDividerHeight(1);
        // מסגרת עבה סביב השורה שעליה עומדים (מצוירת מעל השורה).
        list.setSelector(R.drawable.list_row_selector);
        list.setDrawSelectorOnTop(true);
        list.setAdapter(new ArrayAdapter<String>(this, R.layout.item_choice, labels));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                AppPrefs.setKeyForTaps(SettingsActivity.this, taps, AppPrefs.KEY_CODES[pos]);
                refresh();
                dialog.dismiss();
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
        list.setSelection(currentPos);
        list.requestFocus();
    }
}
