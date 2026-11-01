package com.headset.press5;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/**
 * הלב של האפליקציה: בזמן שיחה ("חמוש") תופס את אירועי הלחצן של האוזנייה,
 * סופר נגיעות, וכשמספר הנגיעות שווה להגדרה - מפעיל את Key5Sender.
 *
 * שני מסלולי קליטה לאותו אירוע, כי אין מסלול אחד שעובד בכל גרסה:
 * - אנדרואיד 5+: MediaSession פעיל (MediaSessionHelper).
 * - אנדרואיד 4.4: registerMediaButtonEventReceiver + MediaButtonReceiver.
 * מנגנון הסרת כפילויות מונע ספירה כפולה אם שניהם מקבלים את אותו אירוע.
 *
 * כל פעולה רצה על ה-thread הראשי.
 */
public final class HeadsetPressController {
    private HeadsetPressController() {}

    /** חלון הזמן בין נגיעות שנחשבות לאותה סדרה. */
    private static final long TAP_WINDOW_MS = 400L;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private static final long DEDUPE_MS = 120L;
    private static long lastTapTime;

    private static boolean armed;
    private static int tapCount;
    private static long lastEventTime = -1;
    private static int lastEventAction = -1;
    private static Context appContext;

    private static final Runnable FIRE = new Runnable() {
        @Override
        public void run() {
            int count = tapCount;
            tapCount = 0;
            if (appContext == null) return;
            int target = AppPrefs.getTaps(appContext);
            if (count == target) {
                DiagnosticsLog.add(appContext, "זוהו " + count + " נגיעות - מקיש 5");
                Key5Sender.send(appContext);
            } else {
                DiagnosticsLog.add(appContext, "זוהו " + count + " נגיעות - לא תואם להגדרה (" + target + ")");
            }
        }
    };

    /** נקרא כשהשיחה מתחילה. */
    public static void arm(Context c) {
        if (armed) return;
        appContext = c.getApplicationContext();
        armed = true;
        tapCount = 0;
        DiagnosticsLog.add(appContext, "שיחה החלה - תופס את לחצן האוזנייה");
        if (!AppPrefs.METHOD_ACCESSIBILITY.equals(AppPrefs.getMethod(appContext))) {
            RootInputListener.start(appContext);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaSessionHelper.start(appContext);
        } else {
            registerLegacy(appContext);
            DiagnosticsLog.add(appContext, "נרשם מקלט לחצן מדיה (מסלול 4.4)");
        }
    }

    /** נקרא כשהשיחה נגמרת - משחרר את הלחצן בחזרה למערכת. */
    public static void disarm(Context c) {
        if (!armed) return;
        armed = false;
        HANDLER.removeCallbacks(FIRE);
        tapCount = 0;
        DiagnosticsLog.add(appContext, "שיחה הסתיימה - משחרר את לחצן האוזנייה");
        RootInputListener.stop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaSessionHelper.stop();
        } else {
            unregisterLegacy(appContext);
        }
    }

    /** האם האירוע נצרך (אז המחייגן לא יקבל אותו - למשל לא ינתק את השיחה). */
    public static boolean onKeyEvent(Context c, KeyEvent ev) {
        if (!armed || ev == null) return false;
        int code = ev.getKeyCode();
        // נגיעה בודדת באוזנייה: הפעלה/השהיה. נגיעה כפולה שולחת בדרך כלל "הבא"
        // (נספרת כ-2), ושלוש נגיעות "הקודם" (נספרת כ-3) - זה מה שנראה ביומן.
        int weight = weightOf(code);
        boolean tapKey = weight > 0;

        // הסרת כפילויות: אותו אירוע שהגיע משני המסלולים.
        if (ev.getEventTime() == lastEventTime && ev.getAction() == lastEventAction) return tapKey;
        lastEventTime = ev.getEventTime();
        lastEventAction = ev.getAction();

        DiagnosticsLog.add(c, "אירוע לחצן: " + KeyEvent.keyCodeToString(code)
                + (ev.getAction() == KeyEvent.ACTION_DOWN ? " DOWN" : " UP"));
        if (!tapKey) return false;

        if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) registerTap(weight);
        return true;
    }

    /** נגיעה שזוהתה ישירות מהקלט הגולמי (רוט) - נקרא מ-thread אחר. */
    public static void onRawTap(final int weight) {
        HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (armed) registerTap(weight);
            }
        });
    }

    /** כמה נגיעות מייצג מקש מדיה: 0 = לא מקש נגיעה. */
    static int weightOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return 1;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return 2;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return 3;
            default:
                return 0;
        }
    }

    /** נגיעה אחת; הסרת כפילויות בין מסלול המערכת למסלול הקלט הגולמי. */
    private static void registerTap(int weight) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastTapTime < DEDUPE_MS) return;
        lastTapTime = now;
        tapCount += weight;
        HANDLER.removeCallbacks(FIRE);
        HANDLER.postDelayed(FIRE, TAP_WINDOW_MS);
    }

    // ---- מסלול אנדרואיד 4.4 ----

    private static ComponentName receiverName(Context c) {
        return new ComponentName(c, MediaButtonReceiver.class);
    }

    @SuppressWarnings("deprecation")
    private static void registerLegacy(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.registerMediaButtonEventReceiver(receiverName(c));
    }

    @SuppressWarnings("deprecation")
    private static void unregisterLegacy(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.unregisterMediaButtonEventReceiver(receiverName(c));
    }

    /** חילוץ KeyEvent מ-Intent של MEDIA_BUTTON. */
    @SuppressWarnings({"deprecation", "unchecked"})
    static KeyEvent extractKeyEvent(Intent intent) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class);
        }
        return (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
    }
}
