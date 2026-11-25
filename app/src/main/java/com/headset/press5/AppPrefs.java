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

    /** כמה סוגי הקשות (1..MAX_TAPS) אפשר להגדיר. */
    public static final int MAX_TAPS = 4;

    /** המקשים שאפשר לבחור: 0 = כבוי, ואז ספרות, כוכבית וסולמית (קודי KeyEvent). */
    public static final int[] KEY_CODES = {0, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};

    /** שם תצוגה של מקש (או "כבוי"). */
    public static String keyLabel(int keyCode) {
        if (keyCode == 0) return "כבוי";
        if (keyCode >= 7 && keyCode <= 16) return String.valueOf(keyCode - 7);
        if (keyCode == 17) return "*";
        if (keyCode == 18) return "#";
        return "?";
    }

    /**
     * איזה מקש להקיש כשמזהים taps הקשות (1..MAX_TAPS). 0 = לא לעשות כלום.
     * ברירת מחדל: הקשה אחת = 5. הגדרה ישנה "כמה נגיעות מקישות 5" (taps) נשמרת:
     * מספר ההקשות שהיה מוגדר ימשיך להקיש 5.
     */
    public static int getKeyForTaps(Context c, int taps) {
        SharedPreferences p = prefs(c);
        String key = "rule_" + taps;
        if (p.contains(key)) return p.getInt(key, 0);
        int legacy = p.getInt(KEY_TAPS, 1);
        return taps == legacy ? 12 : 0;
    }

    public static void setKeyForTaps(Context c, int taps, int keyCode) {
        prefs(c).edit().putInt("rule_" + taps, keyCode).apply();
    }

    /** מחזיר את ההגדרות לברירת המחדל (הקשה אחת = 5). */
    public static void resetRules(Context c) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int t = 1; t <= MAX_TAPS; t++) e.putInt("rule_" + t, t == 1 ? 12 : 0);
        e.apply();
    }

    /** תיאור קצר של ההגדרות, למסך הראשי וליומן. */
    public static String rulesSummary(Context c) {
        StringBuilder sb = new StringBuilder();
        for (int t = 1; t <= MAX_TAPS; t++) {
            int k = getKeyForTaps(c, t);
            if (k == 0) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(t).append(" = ").append(keyLabel(k));
        }
        return sb.length() == 0 ? "אין פעולות מוגדרות" : sb.toString();
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
