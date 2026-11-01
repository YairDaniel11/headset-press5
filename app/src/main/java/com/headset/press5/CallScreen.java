package com.headset.press5;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * עזרי רוט למסך השיחה: איזו פעילות על המסך עכשיו, איפה מסך השיחה של המחייגן,
 * והעלאתו לחזית (עם לוח מקשים פתוח). כל הפונקציות חוסמות (su) - לקרוא רק
 * מ-thread רקע.
 */
final class CallScreen {
    private CallScreen() {}

    private static final long CMD_TIMEOUT_MS = 5000L;

    // NEW_TASK | REORDER_TO_FRONT - מעלה פעילות קיימת לחזית בלי ליצור חדשה.
    private static final String FRONT_FLAGS = "0x10020000";

    /** ה-extra ש-InCallActivity של 4.4 מקבל כדי להציג את לוח המקשים. */
    private static final String SHOW_DIALPAD_EXTRA = "InCallActivity.show_dialpad";

    // חבילה/מחלקה. נקודה כתובה כ-[.] כדי לא להסתבך עם escaping.
    private static final Pattern COMPONENT = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_]*(?:[.][A-Za-z0-9_]+)+)/([.]?[A-Za-z0-9_.$]+)");

    /** שם מחלקה אמיתי מכיל נקודה (ולא למשל "u0a10" של מזהה משתמש). */
    private static String componentFrom(Matcher m) {
        String cls = m.group(2);
        if (cls.indexOf('.') < 0) return null;
        return m.group(1) + "/" + cls;
    }

    /** הפעילות שעל המסך עכשיו, בצורת pkg/cls, או null אם לא זוהתה. */
    static String resumed() {
        String out = RootShell.execOutput("dumpsys activity activities | grep -E "
                + "'mResumedActivity|mFocusedActivity|topResumedActivity'", CMD_TIMEOUT_MS);
        if (out == null) return null;
        Matcher m = COMPONENT.matcher(out);
        while (m.find()) {
            String comp = componentFrom(m);
            if (comp != null) return comp;
        }
        return null;
    }

    static boolean isCallScreen(String component) {
        return component != null && component.toLowerCase().contains("incall");
    }

    /** מסך השיחה (מחלקה ששמה כולל InCall) מתוך הפעילויות הקיימות, או null. */
    static String findCall() {
        String out = RootShell.execOutput("dumpsys activity activities | grep -i incall", CMD_TIMEOUT_MS);
        if (out == null) return null;
        Matcher m = COMPONENT.matcher(out);
        while (m.find()) {
            String comp = componentFrom(m);
            if (comp != null && m.group(2).toLowerCase().contains("incall")) return comp;
        }
        return null;
    }

    /** מעלה פעילות לחזית. */
    static boolean bringToFront(String component) {
        return RootShell.exec("am start -n '" + component + "' -f " + FRONT_FLAGS, CMD_TIMEOUT_MS).success;
    }

    /** מעלה את מסך השיחה לחזית ומבקש ממנו להציג את לוח המקשים. */
    static boolean showCallWithDialpad(String component) {
        return RootShell.exec("am start -n '" + component + "' -f " + FRONT_FLAGS
                + " --ez " + SHOW_DIALPAD_EXTRA + " true", CMD_TIMEOUT_MS).success;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
