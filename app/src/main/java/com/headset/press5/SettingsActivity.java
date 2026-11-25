package com.headset.press5;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/** הגדרת הפעולה לכל מספר הקשות: איזה מקש ילחץ בשיחה (או כבוי). */
public class SettingsActivity extends Activity {

    private final int[] spinnerIds = {R.id.spTaps1, R.id.spTaps2, R.id.spTaps3, R.id.spTaps4};
    private final Spinner[] spinners = new Spinner[AppPrefs.MAX_TAPS];
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.apply(this);
        setContentView(R.layout.activity_settings);

        String[] labels = new String[AppPrefs.KEY_CODES.length];
        for (int i = 0; i < labels.length; i++) {
            int code = AppPrefs.KEY_CODES[i];
            labels[i] = code == 0 ? "כבוי (לא עושה כלום)" : "מקש " + AppPrefs.keyLabel(code);
        }
        for (int i = 0; i < spinners.length; i++) {
            final int taps = i + 1;
            Spinner sp = (Spinner) findViewById(spinnerIds[i]);
            ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, labels);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sp.setAdapter(ad);
            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    if (loading) return;
                    AppPrefs.setKeyForTaps(SettingsActivity.this, taps, AppPrefs.KEY_CODES[pos]);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            spinners[i] = sp;
            if (i > 0) sp.setNextFocusUpId(spinnerIds[i - 1]);
            if (i < spinners.length - 1) sp.setNextFocusDownId(spinnerIds[i + 1]);
        }
        View reset = findViewById(R.id.btnResetRules);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppPrefs.resetRules(SettingsActivity.this);
                load();
            }
        });
        reset.setNextFocusUpId(spinnerIds[spinnerIds.length - 1]);
        spinners[spinners.length - 1].setNextFocusDownId(R.id.btnResetRules);
        load();
    }

    private void load() {
        loading = true;
        for (int i = 0; i < spinners.length; i++) {
            int key = AppPrefs.getKeyForTaps(this, i + 1);
            int pos = 0;
            for (int j = 0; j < AppPrefs.KEY_CODES.length; j++) {
                if (AppPrefs.KEY_CODES[j] == key) pos = j;
            }
            spinners[i].setSelection(pos, false);
        }
        // ה-listener של Spinner נקרא אסינכרונית אחרי setSelection - משחררים את הדגל אחרי סיבוב נוסף.
        spinners[0].post(new Runnable() {
            @Override
            public void run() {
                loading = false;
            }
        });
    }
}
