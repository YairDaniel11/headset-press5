package com.headset.press5;

import android.content.Context;
import android.content.SharedPreferences;

/** מקור אמת יחיד להגדרות - כל הרכיבים קוראים מכאן. */
public final class AppPrefs {
    private AppPrefs() {}

    private static final String FILE = "press5_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_TAPS = "taps";
    private static final String KEY_METHOD = "method";

    public static final String METHOD_AUTO = "auto";
    public static final String METHOD_ROOT = "root";
    public static final String METHOD_ACCESSIBILITY = "accessibility";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_ENABLED, v).apply();
    }

    /** כמה נגיעות באוזנייה מקישות 5: 1 או 2. */
    public static int getTaps(Context c) {
        return prefs(c).getInt(KEY_TAPS, 2) == 1 ? 1 : 2;
    }

    public static void setTaps(Context c, int taps) {
        prefs(c).edit().putInt(KEY_TAPS, taps == 1 ? 1 : 2).apply();
    }

    public static String getMethod(Context c) {
        return prefs(c).getString(KEY_METHOD, METHOD_AUTO);
    }

    public static void setMethod(Context c, String m) {
        prefs(c).edit().putString(KEY_METHOD, m).apply();
    }
}
