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
 * שני מסלולי קליטה, כי ב-4.4 המערכת בולעת בשיחה את מקשי play/pause לפני
 * שהם מגיעים לאפליקציות רגילות - לכן המסלול האמין בשיחה הוא הקלט הגולמי:
 * - מערכת: MediaSession (5+) או registerMediaButtonEventReceiver (4.4).
 * - גולמי: getevent דרך רוט (RootInputListener).
 * נגיעה פיזית אחת מגיעה בדרך כלל משני המסלולים. כדי לא לספור אותה פעמיים,
 * כל סדרת נגיעות ננעלת על המסלול שהתחיל אותה, והשני מתעלם עד סוף הסדרה.
 *
 * כל פעולה רצה על ה-thread הראשי.
 */
public final class HeadsetPressController {
    private HeadsetPressController() {}

    /** חלון הזמן בין נגיעות שנחשבות לאותה סדרה. */
    private static final long TAP_WINDOW_MS = 650L;

    private static final int SRC_NONE = -1;
    private static final int SRC_SYSTEM = 0;
    private static final int SRC_RAW = 1;
    private static final int SRC_VOLUME = 2;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    /**
     * המחייגן נרשם מחדש כמקבל לחצן מדיה בתחילת השיחה ודורס אותנו - לכן מרעננים
     * בתדירות גבוהה בהתחלה, ואחר כך לאט.
     */
    private static final long REASSERT_FAST_MS = 700L;
    private static final long REASSERT_SLOW_MS = 2500L;
    private static int reassertRuns;
    private static final Runnable REASSERT = new Runnable() {
        @Override
        public void run() {
            if (!armed || appContext == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                MediaSessionHelper.reassert();
            } else {
                registerLegacy(appContext);
            }
            reassertRuns++;
            HANDLER.postDelayed(this, reassertRuns < 5 ? REASSERT_FAST_MS : REASSERT_SLOW_MS);
        }
    };

    /** רושם ביומן לאן מנותב קול השיחה - קובע אם האוזנייה במצב שיחה (HFP) ולכן לא שולחת נגיעות. */
    private static final Runnable LOG_ROUTING = new Runnable() {
        @Override
        public void run() {
            if (!armed || appContext == null) return;
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            DiagnosticsLog.add(appContext, "ניתוב שיחה: מצב=" + am.getMode()
                    + " (2=שיחה), SCO-בלוטוס=" + am.isBluetoothScoOn()
                    + ", מדיה-בלוטוס=" + am.isBluetoothA2dpOn()
                    + ", רמקול=" + am.isSpeakerphoneOn());
        }
    };

    private static boolean armed;
    private static int tapCount;
    private static int seriesSource = SRC_NONE;
    private static long lastEventTime = -1;
    private static int lastEventAction = -1;
    private static Context appContext;

    private static final Runnable FIRE = new Runnable() {
        @Override
        public void run() {
            int count = tapCount;
            tapCount = 0;
            seriesSource = SRC_NONE;
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
        seriesSource = SRC_NONE;
        reassertRuns = 0;
        DiagnosticsLog.add(appContext, "שיחה החלה - תופס את לחצן האוזנייה");
        if (AppPrefs.TRIGGER_VOLUME.equals(AppPrefs.getTrigger(appContext))) {
            // מחוות ווליום: מגיעה לטלפון גם בשיחה (בניגוד לנגיעה רגילה).
            VolumeGestureWatcher.start(appContext);
        } else {
            if (!AppPrefs.METHOD_ACCESSIBILITY.equals(AppPrefs.getMethod(appContext))) {
                RootInputListener.start(appContext);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                MediaSessionHelper.start(appContext);
            } else {
                registerLegacy(appContext);
                DiagnosticsLog.add(appContext, "נרשם מקלט לחצן מדיה (מסלול 4.4)");
            }
            HANDLER.postDelayed(REASSERT, 300L);
        }
        HANDLER.postDelayed(LOG_ROUTING, 1500L);
        HANDLER.postDelayed(LOG_ROUTING, 6000L);
    }

    /** נקרא כשהשיחה נגמרת - משחרר את הלחצן בחזרה למערכת. */
    public static void disarm(Context c) {
        if (!armed) return;
        armed = false;
        HANDLER.removeCallbacks(FIRE);
        HANDLER.removeCallbacks(REASSERT);
        HANDLER.removeCallbacks(LOG_ROUTING);
        tapCount = 0;
        seriesSource = SRC_NONE;
        DiagnosticsLog.add(appContext, "שיחה הסתיימה - משחרר את לחצן האוזנייה");
        VolumeGestureWatcher.stop(appContext);
        RootInputListener.stop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaSessionHelper.stop();
        } else {
            unregisterLegacy(appContext);
        }
    }

    /** האם האירוע נצרך (אז המחייגן לא יקבל אותו - למשל לא ינתק את השיחה). */
    public static boolean onKeyEvent(Context c, KeyEvent ev) {
        if (ev == null) return false;
        if (!armed) {
            // לחצן שהגיע בלי שהשיחה זוהתה - חשוב לאבחון (מצב השיחה לא זוהה / אירוע מאוחר).
            if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                DiagnosticsLog.add(c, "אירוע לחצן (לא בשיחה): " + KeyEvent.keyCodeToString(ev.getKeyCode()));
            }
            return false;
        }
        int code = ev.getKeyCode();
        // נגיעה בודדת באוזנייה: הפעלה/השהיה. נגיעה כפולה שולחת בדרך כלל "הבא"
        // (נספרת כ-2), ושלוש נגיעות "הקודם" (נספרת כ-3) - זה מה שנראה ביומן.
        int weight = AppPrefs.TRIGGER_TAP.equals(AppPrefs.getTrigger(c)) ? weightOf(code) : 0;
        boolean tapKey = weight > 0;

        // אותו אירוע שהגיע משני ערוצי המערכת (MediaSession וגם מקלט).
        if (ev.getEventTime() == lastEventTime && ev.getAction() == lastEventAction) return tapKey;
        lastEventTime = ev.getEventTime();
        lastEventAction = ev.getAction();

        DiagnosticsLog.add(c, "אירוע לחצן: " + KeyEvent.keyCodeToString(code)
                + (ev.getAction() == KeyEvent.ACTION_DOWN ? " DOWN" : " UP"));
        if (!tapKey) return false;

        if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
            registerTap(weight, SRC_SYSTEM);
        }
        return true;
    }

    /** מחוות ווליום מהאוזנייה (VolumeGestureWatcher) - רצה על ה-thread הראשי. */
    public static void onVolumeGesture() {
        if (armed) registerTap(1, SRC_VOLUME);
    }

    /** נגיעה שזוהתה ישירות מהקלט הגולמי (רוט) - נקרא מ-thread אחר. */
    public static void onRawTap(final int weight) {
        HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (armed) registerTap(weight, SRC_RAW);
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

    /** מוסיף נגיעה לסדרה הנוכחית; סדרה ננעלת על המסלול הראשון שהתחיל אותה. */
    private static void registerTap(int weight, int source) {
        if (seriesSource != SRC_NONE && seriesSource != source) return;
        seriesSource = source;
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
