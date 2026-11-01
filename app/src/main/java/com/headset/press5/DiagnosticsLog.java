package com.headset.press5;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * יומן אבחון קטן (קובץ טקסט, עד MAX_LINES שורות). הוא הכלי המרכזי לבדיקת
 * ההיתכנות: רואים בו אילו אירועי כפתור באמת הגיעו מהאוזנייה בזמן שיחה, ומה
 * קרה בניסיון ההקשה.
 *
 * הכתיבה אסינכרונית ב-thread יחיד (כתיבת קובץ בכל אירוע על ה-thread הראשי
 * האטה את הטיפול בלחצן). קריאה (readAll) רואה את מה שכבר נכתב.
 */
public final class DiagnosticsLog {
    private DiagnosticsLog() {}

    private static final String FILE = "press5_log.txt";
    private static final int MAX_LINES = 120;
    private static final char NL = (char) 10;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();

    public static void add(final Context c, final String msg) {
        // הזמן נקבע ברגע האירוע, לא ברגע הכתיבה.
        final String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        final Context app = c.getApplicationContext();
        try {
            WRITER.execute(new Runnable() {
                @Override
                public void run() {
                    append(app, time + " " + msg);
                }
            });
        } catch (Exception ignored) {
            // היומן לעולם לא מפיל את הפונקציה שהשתמשה בו.
        }
    }

    private static synchronized void append(Context c, String line) {
        try {
            List<String> lines = readLines(c);
            lines.add(line);
            while (lines.size() > MAX_LINES) lines.remove(0);
            write(c, lines);
        } catch (Exception ignored) {
        }
    }

    public static synchronized String readAll(Context c) {
        StringBuilder sb = new StringBuilder();
        List<String> lines = readLines(c);
        // החדש ביותר למעלה.
        for (int i = lines.size() - 1; i >= 0; i--) sb.append(lines.get(i)).append(NL);
        return sb.toString();
    }

    public static void clear(final Context c) {
        final Context app = c.getApplicationContext();
        WRITER.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (DiagnosticsLog.class) {
                    new File(app.getFilesDir(), FILE).delete();
                }
            }
        });
    }

    private static List<String> readLines(Context c) {
        List<String> out = new ArrayList<>();
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return out;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) out.add(line);
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void write(Context c, List<String> lines) throws Exception {
        FileOutputStream os = new FileOutputStream(new File(c.getFilesDir(), FILE));
        try {
            StringBuilder sb = new StringBuilder();
            for (String l : lines) sb.append(l).append(NL);
            os.write(sb.toString().getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }
}
