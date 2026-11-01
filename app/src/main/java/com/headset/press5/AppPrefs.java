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
    private static final String KEY_HIDE_NOTIF = "hide_notif";
    private static final String KEY_TRIGGER = "trigger";

    /** נגיעה באוזנייה (לא מגיעה לטלפון בשיחה כשהקול באוזנייה) / מחוות ווליום (מגיעה). */
    public static final String TRIGGER_TAP = "tap";
    public static final String TRIGGER_VOLUME = "volume";

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

    /** להסתיר (ככל שהמערכת מאפשרת) את התראת "השירות פעיל" מסרגל המצב. */
    public static boolean getHideNotification(Context c) {
        return prefs(c).getBoolean(KEY_HIDE_NOTIF, false);
    }

    public static void setHideNotification(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_HIDE_NOTIF, v).apply();
    }

    public static String getTrigger(Context c) {
        return prefs(c).getString(KEY_TRIGGER, TRIGGER_VOLUME);
    }

    public static void setTrigger(Context c, String t) {
        prefs(c).edit().putString(KEY_TRIGGER, t).apply();
    }
}
