package com.headset.press5;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.regex.Pattern;

/**
 * שירות נגישות שלוחץ על מקש 5 במסך השיחה של המחייגן, בלי רוט ובלי להחליף מחייגן.
 * אם המקלדת סגורה: קודם לוחץ על כפתור "מקלדת" ואז על 5.
 *
 * מגבלה מהותית: זה תלוי בממשק המחייגן. המזהים/הטקסטים נבדקים בכמה דרכים
 * (טקסט, contentDescription, מזהה), ואם לא נמצא - נכתב ליומן כדי שאפשר
 * יהיה להוסיף כלל למחייגן הספציפי.
 */
public class KeypadClickService extends AccessibilityService {

    private static volatile KeypadClickService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static final Pattern TOGGLE_PATTERN = Pattern.compile(
            "(?i).*(dial ?pad|keypad|key pad|מקלדת|לוח חיוג|מקשים).*");
    private static final long KEYPAD_OPEN_DELAY_MS = 600L;

    public static boolean isConnected() {
        return instance != null;
    }

    /** בטוח לקרוא מכל thread. */
    public static void requestPressFive() {
        final KeypadClickService s = instance;
        if (s == null) return;
        s.handler.post(new Runnable() {
            @Override
            public void run() {
                s.pressFive();
            }
        });
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        DiagnosticsLog.add(this, "שירות הנגישות התחבר");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // לא קוראים תוכן ולא מגיבים לאירועים - השירות פועל רק לפי בקשה מפורשת.
    }

    @Override
    public void onInterrupt() {
    }

    private void pressFive() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            DiagnosticsLog.add(this, "נגישות: אין חלון פעיל (המסך כבוי?)");
            return;
        }
        if (clickKey(root, "5")) {
            DiagnosticsLog.add(this, "נגישות: נלחץ מקש 5");
            return;
        }
        AccessibilityNodeInfo toggle = findToggle(root);
        if (toggle == null) {
            DiagnosticsLog.add(this, "נגישות: לא נמצא מקש 5 ולא כפתור מקלדת");
            return;
        }
        DiagnosticsLog.add(this, "נגישות: פותח מקלדת");
        click(toggle);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                AccessibilityNodeInfo root2 = getRootInActiveWindow();
                boolean ok = root2 != null && clickKey(root2, "5");
                DiagnosticsLog.add(KeypadClickService.this,
                        ok ? "נגישות: נלחץ מקש 5 (אחרי פתיחת מקלדת)" : "נגישות: מקש 5 לא נמצא גם אחרי פתיחת מקלדת");
            }
        }, KEYPAD_OPEN_DELAY_MS);
    }

    private boolean clickKey(AccessibilityNodeInfo root, String digit) {
        AccessibilityNodeInfo node = findKey(root, digit);
        return node != null && click(node);
    }

    /** מקש הספרה: טקסט או תיאור שהם בדיוק הספרה (או מתחילים בה ובפסיק, כמו "5, JKL"). */
    private AccessibilityNodeInfo findKey(AccessibilityNodeInfo node, String digit) {
        if (node == null) return null;
        if (!node.isEditable() && isDigitMatch(node, digit) && clickableTarget(node) != null) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findKey(node.getChild(i), digit);
            if (found != null) return found;
        }
        return null;
    }

    private boolean isDigitMatch(AccessibilityNodeInfo n, String digit) {
        return matchesDigit(n.getText(), digit) || matchesDigit(n.getContentDescription(), digit);
    }

    private boolean matchesDigit(CharSequence cs, String digit) {
        if (cs == null) return false;
        String s = cs.toString().trim();
        return s.equals(digit) || s.startsWith(digit + ",") || s.startsWith(digit + " ");
    }

    private AccessibilityNodeInfo findToggle(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (looksLikeToggle(node) && clickableTarget(node) != null) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findToggle(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean looksLikeToggle(AccessibilityNodeInfo n) {
        CharSequence desc = n.getContentDescription();
        CharSequence text = n.getText();
        String id = n.getViewIdResourceName();
        return (desc != null && TOGGLE_PATTERN.matcher(desc).matches())
                || (text != null && TOGGLE_PATTERN.matcher(text).matches())
                || (id != null && TOGGLE_PATTERN.matcher(id).matches());
    }

    /** הצומת עצמו או האב הקרוב ביותר שניתן ללחיצה. */
    private AccessibilityNodeInfo clickableTarget(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        while (cur != null) {
            if (cur.isClickable()) return cur;
            cur = cur.getParent();
        }
        return null;
    }

    private boolean click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo target = clickableTarget(n);
        return target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
}
