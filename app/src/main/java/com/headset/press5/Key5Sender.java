package com.headset.press5;

import android.content.Context;

/**
 * מבצע את הקשת 5 בפועל. שתי שיטות:
 * - נגישות: KeypadClickService לוחץ על מקש 5 במסך השיחה (בלי רוט).
 * - רוט: מביא את מסך השיחה לחזית (אם צריך), שולח "input keyevent 12"
 *   (KEYCODE_5) ומחזיר את האפליקציה הקודמת.
 * במצב אוטומטי: נגישות אם מחוברת, אחרת רוט.
 */
final class Key5Sender {
    private Key5Sender() {}

    private static final int KEYCODE_5 = 12;
    private static final long ROOT_TIMEOUT_MS = 6000L;
    private static final long CALL_SCREEN_SETTLE_MS = 700L;
    private static final long BEFORE_RESTORE_MS = 400L;

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
        // su חוסם - חייב thread רקע (ראו RootShell).
        new Thread(new Runnable() {
            @Override
            public void run() {
                sendViaRoot(c);
            }
        }, "press5-root").start();
    }

    private static void sendViaRoot(Context c) {
        String top = CallScreen.resumed();
        String restore = null;

        // מקש פיזי נשלח לחלון שעל המסך. אם זו אפליקציה אחרת - מעלים את מסך השיחה
        // לחזית (אחרת 5 היה מוקלד לתוכה), ובסוף מחזירים אותה.
        if (top != null && !CallScreen.isCallScreen(top)) {
            String call = CallScreen.findCall();
            if (call == null) {
                DiagnosticsLog.add(c, "רוט: מסך השיחה לא נמצא (על המסך: " + top + ") - לא מקיש 5");
                return;
            }
            CallScreen.bringToFront(call);
            DiagnosticsLog.add(c, "רוט: מסך השיחה הובא לחזית");
            CallScreen.sleep(CALL_SCREEN_SETTLE_MS);
            // חלון הבקשה של מנהל ה-root הוא לא אפליקציה שחוזרים אליה.
            if (!top.toLowerCase().contains("magisk")) restore = top;
        }

        RootShell.Result r = RootShell.exec("input keyevent " + KEYCODE_5, ROOT_TIMEOUT_MS);
        DiagnosticsLog.add(c, r.success ? "רוט: נשלח KEYCODE_5"
                : (r.timedOut ? "רוט: su לא הגיב בזמן" : "רוט: הפקודה נכשלה"));

        if (restore != null) {
            CallScreen.sleep(BEFORE_RESTORE_MS);
            CallScreen.bringToFront(restore);
            DiagnosticsLog.add(c, "רוט: האפליקציה הקודמת הוחזרה");
        }
    }
}
