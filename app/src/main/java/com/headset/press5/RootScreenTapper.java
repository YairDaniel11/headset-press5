package com.headset.press5;

import android.content.Context;
import android.graphics.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * הקשת 5 דרך רוט, בלי שירות נגישות: מצלם את עץ המסך (uiautomator dump),
 * מוצא את כפתור 5 במסך השיחה ומקיש עליו (input tap). אם המקלדת סגורה -
 * קודם מקיש על כפתור "מקלדת". דורש thread רקע (חוסם).
 */
final class RootScreenTapper {
    private RootScreenTapper() {}

    private static final String DUMP_FILE = "/data/local/tmp/press5_dump.xml";
    private static final long DUMP_TIMEOUT_MS = 15000L;
    private static final long TAP_TIMEOUT_MS = 5000L;
    private static final long KEYPAD_OPEN_DELAY_MS = 700L;

    private static final Pattern NODE = Pattern.compile("<node [^>]*>");
    private static final Pattern BOUNDS = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

    /** true אם נמצא ולחצנו על מקש 5. */
    static boolean pressFive(Context c) {
        appCtx = c.getApplicationContext();
        String top = resumedComponent();
        boolean broughtCall = false;
        if (AppPrefs.getBringFront(c) && (top == null || !top.toLowerCase().contains("incall"))) {
            // על המסך אפליקציה אחרת: מביאים את מסך השיחה לחזית, מקישים, ומחזירים.
            String call = findCallComponent();
            if (call != null) {
                RootShell.exec("am start -n '" + call + "' -f " + FRONT_FLAGS, TAP_TIMEOUT_MS);
                DiagnosticsLog.add(c, "מסך: מביא את מסך השיחה לחזית (" + call + ")");
                sleep(FRONT_DELAY_MS);
                broughtCall = true;
            } else {
                DiagnosticsLog.add(c, "מסך: מסך השיחה לא נמצא, מנסה על המסך הנוכחי (" + top + ")");
            }
        }
        try {
            return doPress(c);
        } finally {
            if (broughtCall && top != null) {
                sleep(300L);
                RootShell.exec("am start -n '" + top + "' -f " + FRONT_FLAGS, TAP_TIMEOUT_MS);
                DiagnosticsLog.add(c, "מסך: מחזיר את האפליקציה הקודמת (" + top + ")");
            }
        }
    }

    // NEW_TASK | REORDER_TO_FRONT - מעלה פעילות קיימת לחזית בלי ליצור חדשה.
    private static final String FRONT_FLAGS = "0x10020000";
    private static final long FRONT_DELAY_MS = 900L;
    private static final Pattern COMPONENT = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/(\\.?[A-Za-z0-9_.$]+)");

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** הפעילות שעל המסך עכשיו, בצורת pkg/cls, או null אם לא זוהתה. */
    static String resumedComponent() {
        String out = RootShell.execOutput("dumpsys activity activities | grep -E "
                + "'mResumedActivity|mFocusedActivity|topResumedActivity'", TAP_TIMEOUT_MS);
        if (out == null) return null;
        Matcher m = COMPONENT.matcher(out);
        return m.find() ? m.group(1) + "/" + m.group(2) : null;
    }

    /** מסך השיחה (פעילות שהשם שלה כולל InCall) מתוך הפעילויות הקיימות. */
    private static String findCallComponent() {
        String out = RootShell.execOutput("dumpsys activity activities | grep -i incall", TAP_TIMEOUT_MS);
        if (out == null) return null;
        Matcher m = COMPONENT.matcher(out);
        while (m.find()) {
            String comp = m.group(1) + "/" + m.group(2);
            if (comp.toLowerCase().contains("incall")) return comp;
        }
        return null;
    }

    private static boolean doPress(Context c) {
        String xml = dump();
        if (xml == null) {
            DiagnosticsLog.add(c, "מסך: צילום עץ המסך נכשל (uiautomator/su)");
            return false;
        }
        Point five = find(xml, true);
        if (five != null) return tap(c, five, "נלחץ מקש 5");

        Point toggle = find(xml, false);
        if (toggle == null) {
            DiagnosticsLog.add(c, "מסך: לא נמצא מקש 5 ולא כפתור מקלדת. מופיע במסך: " + summarize(xml));
            return false;
        }
        if (!tap(c, toggle, "נפתחה מקלדת")) return false;
        try {
            Thread.sleep(KEYPAD_OPEN_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        xml = dump();
        five = xml == null ? null : find(xml, true);
        if (five == null) {
            DiagnosticsLog.add(c, "מסך: מקש 5 לא נמצא גם אחרי פתיחת מקלדת. מופיע במסך: "
                    + (xml == null ? "-" : summarize(xml)));
            return false;
        }
        return tap(c, five, "נלחץ מקש 5 (אחרי פתיחת מקלדת)");
    }

    /** עד שני ניסיונות; אם נכשל - מחזיר null ושגיאת uiautomator נרשמת ביומן. */
    private static String dump() {
        String lastOut = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String out = RootShell.execOutput("rm -f " + DUMP_FILE + "; uiautomator dump " + DUMP_FILE
                    + " 2>&1; cat " + DUMP_FILE + " 2>&1", DUMP_TIMEOUT_MS);
            if (out != null && out.contains("<node ")) return out;
            lastOut = out;
        }
        if (appCtx != null) {
            String head = lastOut == null ? "ללא פלט (נתקע?)" : lastOut.trim();
            if (head.length() > 120) head = head.substring(0, 120);
            DiagnosticsLog.add(appCtx, "מסך: שגיאת uiautomator: " + head);
        }
        return null;
    }

    private static Context appCtx;

    private static boolean tap(Context c, Point p, String okMsg) {
        RootShell.Result r = RootShell.exec("input tap " + p.x + " " + p.y, TAP_TIMEOUT_MS);
        DiagnosticsLog.add(c, r.success ? "מסך: " + okMsg + " (" + p.x + "," + p.y + ")"
                : "מסך: הקשה על המסך נכשלה");
        return r.success;
    }

    /** מחפש מקש ספרה 5 (key=true) או כפתור "מקלדת" (key=false); מחזיר את מרכזו. */
    private static Point find(String xml, boolean key) {
        Matcher m = NODE.matcher(xml);
        while (m.find()) {
            String tag = m.group();
            String cls = attr(tag, "class");
            if (cls.contains("EditText")) continue;
            String text = attr(tag, "text");
            String desc = attr(tag, "content-desc");
            boolean hit;
            if (key) {
                hit = isDigit(text, "5") || isDigit(desc, "5");
            } else {
                hit = KeypadClickService.TOGGLE_PATTERN.matcher(text).matches()
                        || KeypadClickService.TOGGLE_PATTERN.matcher(desc).matches()
                        || KeypadClickService.TOGGLE_PATTERN.matcher(attr(tag, "resource-id")).matches();
            }
            if (!hit) continue;
            Matcher b = BOUNDS.matcher(attr(tag, "bounds"));
            if (!b.matches()) continue;
            int x1 = Integer.parseInt(b.group(1)), y1 = Integer.parseInt(b.group(2));
            int x2 = Integer.parseInt(b.group(3)), y2 = Integer.parseInt(b.group(4));
            if (x2 <= x1 || y2 <= y1) continue;
            return new Point((x1 + x2) / 2, (y1 + y2) / 2);
        }
        return null;
    }

    private static boolean isDigit(String s, String digit) {
        String t = s.trim();
        return t.equals(digit) || t.startsWith(digit + ",") || t.startsWith(digit + " ");
    }

    private static String attr(String tag, String name) {
        Matcher m = Pattern.compile("\\s" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : "";
    }

    /** תקציר קצר של הטקסטים שבמסך - כדי שאפשר יהיה לראות ביומן למה לא נמצא. */
    private static String summarize(String xml) {
        List<String> seen = new ArrayList<>();
        Matcher m = NODE.matcher(xml);
        while (m.find() && seen.size() < 12) {
            String tag = m.group();
            String v = attr(tag, "text");
            if (v.length() == 0) v = attr(tag, "content-desc");
            if (v.length() == 0) v = attr(tag, "resource-id");
            if (v.length() > 0 && !seen.contains(v)) seen.add(v.length() > 24 ? v.substring(0, 24) : v);
        }
        return seen.toString();
    }
}
