package com.headset.press5;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.SystemClock;

/**
 * מחוות ווליום באוזנייה כטריגר. בשיחה האוזנייה לא שולחת נגיעה קצרה, אבל שינוי
 * ווליום (AT+VGS בבלוטוס) כן מגיע לטלפון ומשנה את עוצמת השיחה. כל שינוי כזה
 * נספר כנגיעה אחת, ומיד מחזירים את העוצמה לערך הקודם - כך שהמשתמש לא מרגיש בשינוי.
 *
 * בקצה הסקאלה (ווליום מקסימלי/מינימלי) שינוי בכיוון הקצה לא קורה, ולכן
 * המשתמש מחווה בכיוון ההפוך - שניהם נספרים.
 *
 * ה-intent ו-ה-extras הם קבועי מחרוזת של AudioManager שמוסתרים מה-SDK הציבורי,
 * אבל קיימים בכל הגרסאות (ב-4.4 ואילך).
 */
final class VolumeGestureWatcher {
    private VolumeGestureWatcher() {}

    private static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";
    private static final String EXTRA_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";
    private static final String EXTRA_PREV_STREAM_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    private static final int STREAM_VOICE_CALL = 0;
    private static final int STREAM_BLUETOOTH_SCO = 6; // מוסתר ב-SDK

    /** אחרי שמחזירים את העוצמה, מתעלמים מהאירוע שההחזרה עצמה יוצרת. */
    private static final long IGNORE_AFTER_RESTORE_MS = 500L;

    private static BroadcastReceiver receiver;
    private static long ignoreUntil;

    static void start(final Context c) {
        if (receiver != null) return;
        final Context app = c.getApplicationContext();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onVolumeChanged(app, intent);
            }
        };
        app.registerReceiver(receiver, new IntentFilter(ACTION_VOLUME_CHANGED));
        DiagnosticsLog.add(app, "מחוות ווליום: מאזין לשינויי עוצמה בשיחה");
    }

    static void stop(Context c) {
        if (receiver == null) return;
        try {
            c.getApplicationContext().unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        receiver = null;
    }

    private static void onVolumeChanged(Context app, Intent intent) {
        int stream = intent.getIntExtra(EXTRA_STREAM_TYPE, -1);
        int value = intent.getIntExtra(EXTRA_STREAM_VALUE, -1);
        int prev = intent.getIntExtra(EXTRA_PREV_STREAM_VALUE, -1);
        long now = SystemClock.uptimeMillis();

        // רושמים כל שינוי ביומן (גם של זרמים אחרים) - חשוב לאבחון.
        DiagnosticsLog.add(app, "ווליום: זרם=" + stream + " מ-" + prev + " ל-" + value
                + (now < ignoreUntil ? " (החזרה - מתעלם)" : ""));

        if (now < ignoreUntil) return;
        if (stream != STREAM_VOICE_CALL && stream != STREAM_BLUETOOTH_SCO) return;
        if (prev < 0 || value < 0 || prev == value) return;

        // מחזירים את העוצמה לערך הקודם, ורק אז סופרים את המחווה.
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            ignoreUntil = now + IGNORE_AFTER_RESTORE_MS;
            try {
                am.setStreamVolume(stream, prev, 0);
            } catch (Exception e) {
                DiagnosticsLog.add(app, "ווליום: החזרת העוצמה נכשלה (" + e.getClass().getSimpleName() + ")");
            }
        }
        HeadsetPressController.onVolumeGesture();
    }
}
