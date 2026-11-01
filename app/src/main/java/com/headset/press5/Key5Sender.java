package com.headset.press5;

import android.content.Context;
import android.os.PowerManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * מבצע את הקשת 5 בפועל. שתי שיטות:
 * - נגישות: KeypadClickService לוחץ על מקש 5 במסך השיחה (בלי רוט).
 * - רוט: קריאת su אחת שמבצעת את הכול:
 *     1. "service call phone 4 i32 1" = ITelephony.showCallScreenWithDialpad(true)
 *        (טרנזקציה 4 ב-4.4): אותה פעולה שהמערכת עצמה עושה בלחיצה על התראת
 *        השיחה - מעלה את מסך השיחה לחזית עם לוח המקשים פתוח. ב-4.4 מסך השיחה
 *        מקבל מקשי ספרה רק כשלוח המקשים שלו גלוי. (am start ישיר למסך השיחה
 *        הקריס את המחייגן ב-ROM הזה, ולכן לא משתמשים בו.)
 *     2. "input keyevent 12" = KEYCODE_5 לחלון שבפוקוס (מסך השיחה).
 *   קריאה אחת בלבד ולא כמה: כל קריאת su מקפיצה הודעת Magisk "הוענקו הרשאות".
 * במצב אוטומטי: נגישות אם מחוברת, אחרת רוט.
 */
final class Key5Sender {
    private Key5Sender() {}

    private static final int KEYCODE_5 = 12;
    private static final long ROOT_TIMEOUT_MS = 8000L;
    private static final long SCREEN_WAKE_MS = 4000L;

    /** מונע שתי הקשות במקביל (הקשה חוזרת בזמן שהקודמת עדיין רצה). */
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    static void send(final Context c) {
        final String method = AppPrefs.getMethod(c);
        final boolean useAccessibility = !AppPrefs.METHOD_ROOT.equals(method);
        final boolean useRoot = !AppPrefs.METHOD_ACCESSIBILITY.equals(method);

        if (useAccessibility && KeypadClickService.isConnected()) {
            KeypadClickService.requestPressFive();
            return;
        }
        if (!useRoot) {
            DiagnosticsLog.add(c, "שירות הנגישות לא מחובר - לא ניתן להקיש 5");
            return;
        }
        if (!BUSY.compareAndSet(false, true)) {
            DiagnosticsLog.add(c, "רוט: הקשה קודמת עדיין רצה - מדלג");
            return;
        }
        // su חוסם - חייב thread רקע (ראו RootShell).
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sendViaRoot(c);
                } finally {
                    BUSY.set(false);
                }
            }
        }, "press5-root").start();
    }

    private static void sendViaRoot(Context c) {
        wakeScreen(c);
        // 2>&1: שגיאות נכנסות לפלט, כדי שאפשר יהיה לראות אותן ביומן.
        String cmd = "service call phone 4 i32 1 2>&1; sleep 1; input keyevent " + KEYCODE_5 + " 2>&1";
        String out = RootShell.execOutput(cmd, ROOT_TIMEOUT_MS);
        if (out == null) {
            DiagnosticsLog.add(c, "רוט: su לא הגיב בזמן");
            return;
        }
        String shown = out.trim().replace(String.valueOf((char) 10), " | ");
        if (shown.length() > 120) shown = shown.substring(0, 120);
        DiagnosticsLog.add(c, "רוט: נשלח מסך שיחה + מקש 5. פלט: " + shown);
    }

    /** מדליק את המסך לכמה שניות - אחרת (טלפון בכיס) הקשה לא מגיעה לחלון. */
    @SuppressWarnings("deprecation")
    private static void wakeScreen(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP, "Press5::Screen");
            wl.acquire(SCREEN_WAKE_MS);
        } catch (Exception ignored) {
        }
    }
}
