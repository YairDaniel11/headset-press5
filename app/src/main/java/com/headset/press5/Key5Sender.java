package com.headset.press5;

import android.content.Context;
import android.os.PowerManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * מבצע את הקשת 5 בפועל. שתי שיטות:
 * - נגישות: KeypadClickService לוחץ על מקש 5 במסך השיחה (בלי רוט).
 * - רוט: מעיר את המסך, מעלה את מסך השיחה לחזית כשלוח המקשים שלו פתוח
 *   (ב-4.4 מסך השיחה מקבל מקשי ספרה רק כשלוח המקשים גלוי), שולח
 *   "input keyevent 12" (KEYCODE_5) ומחזיר את האפליקציה הקודמת.
 * במצב אוטומטי: נגישות אם מחוברת, אחרת רוט.
 */
final class Key5Sender {
    private Key5Sender() {}

    private static final int KEYCODE_5 = 12;
    private static final long ROOT_TIMEOUT_MS = 6000L;
    private static final long CALL_SCREEN_SETTLE_MS = 800L;
    private static final long BEFORE_RESTORE_MS = 400L;
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
        String top = CallScreen.resumed();
        // אם על המסך אפליקציה אחרת (או שלא זוהתה) - מחפשים את מסך השיחה. בלי מסך
        // שיחה לא שולחים: מקש פיזי היה מוקלד לתוך אפליקציה אחרת.
        String call = CallScreen.isCallScreen(top) ? top : CallScreen.findCall();
        if (call == null) {
            DiagnosticsLog.add(c, "רוט: מסך השיחה לא נמצא (על המסך: " + top + ") - לא מקיש 5");
            return;
        }

        wakeScreen(c);

        // חלון הבקשה של מנהל ה-root הוא לא אפליקציה שחוזרים אליה.
        String restore = (top != null && !CallScreen.isCallScreen(top) && !top.toLowerCase().contains("magisk"))
                ? top : null;

        boolean shown = CallScreen.showCallWithDialpad(call);
        DiagnosticsLog.add(c, shown ? "רוט: מסך השיחה בחזית עם לוח מקשים" : "רוט: הבאת מסך השיחה נכשלה");
        CallScreen.sleep(CALL_SCREEN_SETTLE_MS);

        RootShell.Result r = RootShell.exec("input keyevent " + KEYCODE_5, ROOT_TIMEOUT_MS);
        DiagnosticsLog.add(c, r.success ? "רוט: נשלח KEYCODE_5"
                : (r.timedOut ? "רוט: su לא הגיב בזמן" : "רוט: הפקודה נכשלה"));

        if (restore != null) {
            CallScreen.sleep(BEFORE_RESTORE_MS);
            CallScreen.bringToFront(restore);
            DiagnosticsLog.add(c, "רוט: האפליקציה הקודמת הוחזרה");
        }
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
