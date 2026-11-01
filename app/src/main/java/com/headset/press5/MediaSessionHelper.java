package com.headset.press5;

import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

/**
 * מופרד למחלקה משלו (API 21+) כדי שמכשיר 4.4 לא ייטען כלל את סוגי
 * MediaSession - ראו הכלל "הגנת גרסה" במדריך. נקרא רק מתוך בדיקת SDK_INT.
 */
final class MediaSessionHelper {
    private MediaSessionHelper() {}

    private static MediaSession session;

    private static PlaybackState state(int s) {
        return new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE)
                .setState(s, 0, 1f)
                .build();
    }

    @SuppressWarnings("deprecation")
    static void start(final Context c) {
        if (session != null) return;
        try {
            session = new MediaSession(c, "press5");
            session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            session.setCallback(new MediaSession.Callback() {
                @Override
                public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                    // נצרך רק אם זה לחצן נגיעה, כדי שהמחייגן לא יענה/ינתק עליו בשיחה.
                    return HeadsetPressController.onKeyEvent(c,
                            HeadsetPressController.extractKeyEvent(mediaButtonIntent));
                }
            });
            // סשן "מנגן" - מערכת ההפעלה שולחת את הלחצנים לסשן שהכי לאחרונה פעיל ומנגן.
            session.setPlaybackState(state(PlaybackState.STATE_PLAYING));
            session.setActive(true);
            DiagnosticsLog.add(c, "סשן מדיה פעיל (מסלול 5+)");
        } catch (Exception e) {
            DiagnosticsLog.add(c, "כשל ביצירת MediaSession: " + e);
            session = null;
        }
    }

    /**
     * מחדש את מעמד הסשן. מעבר השהיה->ניגון הוא שינוי מצב אמיתי, שדוחף את הסשן
     * לראש סדר העדיפויות (קביעה חוזרת של אותו מצב לא עושה זאת).
     */
    static void reassert() {
        if (session == null) return;
        try {
            session.setPlaybackState(state(PlaybackState.STATE_PAUSED));
            session.setPlaybackState(state(PlaybackState.STATE_PLAYING));
            session.setActive(true);
        } catch (Exception ignored) {
        }
    }

    static void stop() {
        if (session == null) return;
        try {
            session.setActive(false);
            session.release();
        } catch (Exception ignored) {
        }
        session = null;
    }
}
