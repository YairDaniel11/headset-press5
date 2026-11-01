package com.headset.press5;

import android.content.Context;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * מאזין לקלט הגולמי של המכשיר (getevent, דורש רוט) בזמן שיחה. זה המסלול האמין
 * לנגיעות אוזנייה בשיחה ב-4.4, כי הקרנל מדווח עליהן לפני שמדיניות המקשים
 * והמחייגן יכולים לבלוע אותן. מקשי מדיה נספרים כנגיעה; שאר המקשים נרשמים ליומן
 * רק לאבחון (בלי ספרות - הן יכולות להיות מידע רגיש שהמשתמש מקיש בשיחה).
 *
 * start/stop לא חוסמים את ה-thread הקורא. כל האזנה היא Session עצמאי, כך
 * שעצירה לפני שהתהליך נוצר, או התחלה חדשה בזמן שהישנה נסגרת, לא מתנגשות.
 *
 * פורמט שורה של "getevent -l":  /dev/input/event5: EV_KEY  KEY_PLAYPAUSE  DOWN
 */
final class RootInputListener {
    private RootInputListener() {}

    private static final class Session {
        volatile boolean stopped;
        volatile Process process;
    }

    private static final Object LOCK = new Object();
    private static Session current;

    static void start(final Context c) {
        final Context app = c.getApplicationContext();
        final Session s;
        synchronized (LOCK) {
            if (current != null) return;
            s = new Session();
            current = s;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                listen(app, s);
            }
        }, "press5-getevent").start();
    }

    /** סוגר את ההאזנה; ה-destroy ב-thread נפרד כי הוא עלול לחסום. */
    static void stop() {
        final Session s;
        synchronized (LOCK) {
            s = current;
            current = null;
        }
        if (s == null) return;
        s.stopped = true;
        final Process p = s.process;
        if (p != null) kill(p);
    }

    private static void kill(final Process p) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    p.destroy();
                } catch (Exception ignored) {
                }
            }
        }, "press5-getevent-stop").start();
    }

    private static void listen(Context app, Session s) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            s.process = p;
            // stop() נקרא לפני שהתהליך נוצר - סוגרים מיד.
            if (s.stopped) {
                kill(p);
                return;
            }
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            // exec: ה-shell מוחלף ב-getevent עצמו, כך שסגירת התהליך סוגרת אותו.
            os.writeBytes("exec getevent -l" + (char) 10);
            os.flush();
            DiagnosticsLog.add(app, "קלט גולמי: האזנה התחילה");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            while (!s.stopped && (line = r.readLine()) != null) handleLine(app, line);
            DiagnosticsLog.add(app, "קלט גולמי: ההאזנה הסתיימה");
        } catch (Exception e) {
            if (!s.stopped) {
                DiagnosticsLog.add(app, "קלט גולמי: נעצר (" + e.getClass().getSimpleName() + ")");
            }
        } finally {
            Process p = s.process;
            if (p != null) kill(p);
            synchronized (LOCK) {
                if (current == s) current = null;
            }
        }
    }

    private static void handleLine(Context app, String line) {
        if (!line.contains("EV_KEY") || !line.contains("DOWN")) return;
        String[] parts = line.trim().split("[ \t]+");
        // [device:, EV_KEY, KEY_NAME, DOWN]
        String key = null;
        for (String p : parts) {
            if (p.startsWith("KEY_") || p.startsWith("BTN_")) {
                key = p;
                break;
            }
        }
        if (key == null) return;
        int w = tapWeight(key);
        if (w > 0) {
            String dev = parts[0].endsWith(":") ? parts[0].substring(0, parts[0].length() - 1) : parts[0];
            DiagnosticsLog.add(app, "קלט גולמי: " + key + " (" + dev + ")");
            HeadsetPressController.onRawTap(w);
            return;
        }
        if (isNoise(key)) return;
        DiagnosticsLog.add(app, "קלט גולמי: " + key);
    }

    /** כמו HeadsetPressController.weightOf, לפי שמות getevent. */
    private static int tapWeight(String key) {
        if (key.equals("KEY_PLAYPAUSE") || key.equals("KEY_PLAYCD") || key.equals("KEY_PAUSECD")
                || key.equals("KEY_MEDIA") || key.equals("KEY_PLAY") || key.equals("KEY_PAUSE")) return 1;
        if (key.equals("KEY_NEXTSONG")) return 2;
        if (key.equals("KEY_PREVIOUSSONG")) return 3;
        return 0;
    }

    /** מקשי ניווט (רעש), כפתורי מגע, וספרות/מקלדת מספרים (מידע רגיש). */
    private static boolean isNoise(String key) {
        if (key.startsWith("BTN_") || key.startsWith("KEY_KP") || key.startsWith("KEY_NUMERIC")) return true;
        if (key.length() == 5 && key.charAt(4) >= '0' && key.charAt(4) <= '9') return true; // KEY_0..KEY_9
        return key.equals("KEY_UP") || key.equals("KEY_DOWN") || key.equals("KEY_LEFT")
                || key.equals("KEY_RIGHT") || key.equals("KEY_ENTER") || key.equals("KEY_BACK")
                || key.equals("KEY_MENU") || key.equals("KEY_POWER") || key.equals("KEY_KBDILLUMTOGGLE");
    }
}
