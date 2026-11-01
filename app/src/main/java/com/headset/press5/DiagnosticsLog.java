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

/**
 * יומן אבחון קטן (קובץ טקסט, עד MAX_LINES שורות). הוא הכלי המרכזי לבדיקת
 * ההיתכנות: רואים בו אילו אירועי כפתור באמת הגיעו מהאוזנייה בזמן שיחה, ומה
 * קרה בניסיון ההקשה.
 */
public final class DiagnosticsLog {
    private DiagnosticsLog() {}

    private static final String FILE = "press5_log.txt";
    private static final int MAX_LINES = 120;

    public static synchronized void add(Context c, String msg) {
        try {
            List<String> lines = readLines(c);
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            lines.add(time + " " + msg);
            while (lines.size() > MAX_LINES) lines.remove(0);
            write(c, lines);
        } catch (Exception ignored) {
            // היומן לעולם לא מפיל את הפונקציה שהשתמשה בו.
        }
    }

    public static synchronized String readAll(Context c) {
        StringBuilder sb = new StringBuilder();
        List<String> lines = readLines(c);
        // החדש ביותר למעלה.
        for (int i = lines.size() - 1; i >= 0; i--) sb.append(lines.get(i)).append('\n');
        return sb.toString();
    }

    public static synchronized void clear(Context c) {
        new File(c.getFilesDir(), FILE).delete();
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
            for (String l : lines) sb.append(l).append('\n');
            os.write(sb.toString().getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }
}
