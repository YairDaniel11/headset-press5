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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaSessionHelper.start(appContext);
        } else {
            registerLegacy(appContext);
        }
    }

    /** נקרא כשהשיחה נגמרת - משחרר את הלחצן בחזרה למערכת. */
    public static void disarm(Context c) {
        if (!armed) return;
        armed = false;
        HANDLER.removeCallbacks(FIRE);
        tapCount = 0;
        DiagnosticsLog.add(appContext, "שיחה הסתיימה - משחרר את לחצן האוזנייה");
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
        boolean tapKey = code == KeyEvent.KEYCODE_HEADSETHOOK
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE;

        // הסרת כפילויות: אותו אירוע שהגיע משני המסלולים.
        if (ev.getEventTime() == lastEventTime && ev.getAction() == lastEventAction) return tapKey;
        lastEventTime = ev.getEventTime();
        lastEventAction = ev.getAction();

        DiagnosticsLog.add(c, "אירוע לחצן: " + KeyEvent.keyCodeToString(code)
                + (ev.getAction() == KeyEvent.ACTION_DOWN ? " DOWN" : " UP"));
        if (!tapKey) return false;

        if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
            tapCount++;
            HANDLER.removeCallbacks(FIRE);
            HANDLER.postDelayed(FIRE, TAP_WINDOW_MS);
        }
        return true;
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
