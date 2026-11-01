package com.headset.press5;

import android.content.Context;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * מאזין לקלט הגולמי של המכשיר (getevent, דורש רוט) בזמן שיחה. שני שימושים:
 * 1. אבחון: רואים ביומן כל מקש ש-getevent מדווח עליו, כולל אירועי אוזנייה
 *    (למשל התקן AVRCP של בלוטוס) שלא מגיעים כלל כאירוע מדיה לאפליקציה.
 * 2. נגיעות: מקשי מדיה/אוזנייה נספרים כנגיעה באותו מנגנון כמו המסלול הרגיל.
 *
 * פורמט שורה של "getevent -l":  /dev/input/event5: EV_KEY  KEY_PLAYPAUSE  DOWN
 */
final class RootInputListener {
    private RootInputListener() {}

    private static Process process;
    private static Thread thread;

    static synchronized void start(final Context c) {
        if (thread != null) return;
        final Context app = c.getApplicationContext();
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Process p = Runtime.getRuntime().exec("su");
                    synchronized (RootInputListener.class) {
                        process = p;
                    }
                    DataOutputStream os = new DataOutputStream(p.getOutputStream());
                    os.writeBytes("getevent -l\n");
                    os.flush();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) handleLine(app, line);
                } catch (Exception e) {
                    DiagnosticsLog.add(app, "קלט גולמי: נעצר (" + e.getClass().getSimpleName() + ")");
                } finally {
                    synchronized (RootInputListener.class) {
                        process = null;
                        thread = null;
                    }
                }
            }
        }, "press5-getevent");
        thread.start();
    }

    static synchronized void stop() {
        if (process != null) process.destroy();
    }

    private static void handleLine(Context app, String line) {
        if (!line.contains("EV_KEY") || !line.contains("DOWN")) return;
        String[] parts = line.trim().split("\\s+");
        // [device:, EV_KEY, KEY_NAME, DOWN]
        String key = null;
        for (String p : parts) {
            if (p.startsWith("KEY_") || p.startsWith("BTN_")) {
                key = p;
                break;
            }
        }
        if (key == null) return;
        String dev = parts[0].endsWith(":") ? parts[0].substring(0, parts[0].length() - 1) : parts[0];
        if (isNavigationNoise(key)) return;
        DiagnosticsLog.add(app, "קלט גולמי: " + key + " (" + dev + ")");
        if (isTapKey(key)) HeadsetPressController.onRawTap();
    }

    /** מקשי הטלפון עצמו (ניווט) - רעש שמסתיר את אירועי האוזנייה ביומן. */
    private static boolean isNavigationNoise(String key) {
        return key.equals("KEY_UP") || key.equals("KEY_DOWN") || key.equals("KEY_LEFT")
                || key.equals("KEY_RIGHT") || key.equals("KEY_ENTER") || key.equals("KEY_BACK")
                || key.equals("KEY_MENU") || key.equals("KEY_POWER") || key.equals("KEY_KBDILLUMTOGGLE");
    }

    private static boolean isTapKey(String key) {
        return key.equals("KEY_PLAYPAUSE") || key.equals("KEY_PLAYCD") || key.equals("KEY_PAUSECD")
                || key.equals("KEY_MEDIA") || key.equals("KEY_PLAY") || key.equals("KEY_PAUSE");
    }
}
