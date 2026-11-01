package com.headset.press5;

import android.content.Context;

/**
 * מבצע את הקשת 5 בפועל. שתי שיטות:
 * - נגישות: KeypadClickService לוחץ על מקש 5 במסך השיחה (בלי רוט).
 * - רוט: "input keyevent 12" (KEYCODE_5). לא מובטח שהמחייגן מתרגם מקש
 *   פיזי לצליל DTMF - זה אחד הדברים שבודקים בניסוי.
 * במצב אוטומטי: נגישות אם מחוברת, אחרת רוט.
 */
final class Key5Sender {
    private Key5Sender() {}

    private static final int KEYCODE_5 = 12;
    private static final long ROOT_TIMEOUT_MS = 6000L;

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
                RootShell.Result r = RootShell.exec("input keyevent " + KEYCODE_5, ROOT_TIMEOUT_MS);
                DiagnosticsLog.add(c, r.success ? "רוט: נשלח KEYCODE_5"
                        : (r.timedOut ? "רוט: su לא הגיב בזמן" : "רוט: הפקודה נכשלה"));
            }
        }, "press5-root").start();
    }
}
