package com.headset.press5;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * סשן su אחד שנשאר פתוח לאורך השיחה. כל קריאת su חדשה מקפיצה את ההודעה של Magisk
 * ("הוענקו הרשאות"), ולכן פותחים פעם אחת (בהקשה הראשונה בשיחה) וסוגרים בסוף השיחה.
 * כל פקודה נשלחת ל-stdin, והפלט נקרא עד שורת סמן.
 */
final class RootSession {
    private RootSession() {}

    private static final String MARK = "__P5_DONE__";
    private static final String EOF = "__P5_EOF__";

    private static Process proc;
    private static BufferedWriter out;
    private static BlockingQueue<String> lines;

    private static void open() throws Exception {
        proc = Runtime.getRuntime().exec("su");
        out = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
        final BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        final BlockingQueue<String> q = new LinkedBlockingQueue<String>();
        lines = q;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String l;
                    while ((l = in.readLine()) != null) q.offer(l);
                } catch (Exception ignored) {
                }
                q.offer(EOF);
            }
        }, "press5-root-read");
        t.setDaemon(true);
        t.start();
    }

    /** מריץ פקודה ומחזיר את הפלט, או null אם נכשל / נתקע (אז הסשן נסגר). */
    static synchronized String run(String cmd, long timeoutMs) {
        try {
            if (proc == null) open();
            lines.clear();
            out.write(cmd + "\necho " + MARK + "\n");
            out.flush();
            StringBuilder sb = new StringBuilder();
            long end = System.currentTimeMillis() + timeoutMs;
            while (true) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                String l = lines.poll(left, TimeUnit.MILLISECONDS);
                if (l == null || l.equals(EOF)) break;
                if (l.contains(MARK)) return sb.toString();
                sb.append(l).append('\n');
            }
        } catch (Exception ignored) {
        }
        close();
        return null;
    }

    static synchronized void close() {
        if (proc == null) return;
        try { out.write("exit\n"); out.flush(); } catch (Exception ignored) {}
        try { proc.destroy(); } catch (Exception ignored) {}
        proc = null;
        out = null;
        lines = null;
    }
}
