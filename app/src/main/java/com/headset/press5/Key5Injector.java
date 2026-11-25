package com.headset.press5;

import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

import java.lang.reflect.Method;

/**
 * מזריק מקש ישירות ל-IInputManager. רץ כתהליך root דרך app_process (ראו Key5Sender),
 * בלי JVM כבד של "input keyevent". הרעיון והקוד נלקחו מ-Injector באפליקציית KeypadSender,
 * שם הוא הוכח כעובד בתוך שיחה: המקש הראשון נלחץ זמן ארוך יותר כי נתיב האודיו "מתחמם"
 * והצליל הקצר נחתך.
 *
 * שימוש: CLASSPATH=apk app_process /system/bin com.headset.press5.Key5Injector [keycode] [holdMs]
 */
public final class Key5Injector {
    private Key5Injector() {}

    public static void main(String[] args) {
        int code = KeyEvent.KEYCODE_5;
        long hold = 400;
        try {
            if (args.length > 0) code = Integer.parseInt(args[0]);
            if (args.length > 1) hold = Long.parseLong(args[1]);

            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, "input");
            Class<?> stub = Class.forName("android.hardware.input.IInputManager$Stub");
            Object im = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
            Method inject = null;
            for (Method m : im.getClass().getMethods()) {
                if (m.getName().equals("injectInputEvent") && m.getParameterTypes().length == 2) {
                    inject = m;
                    break;
                }
            }
            if (inject == null) {
                System.out.println("FAIL no injectInputEvent");
                return;
            }
            long now = SystemClock.uptimeMillis();
            boolean ok = send(im, inject, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0, -1, 0,
                    0, InputDevice.SOURCE_KEYBOARD));
            Thread.sleep(hold);
            long up = SystemClock.uptimeMillis();
            ok &= send(im, inject, new KeyEvent(now, up, KeyEvent.ACTION_UP, code, 0, 0, -1, 0,
                    0, InputDevice.SOURCE_KEYBOARD));
            System.out.println(ok ? "OK" : "ERR");
        } catch (Throwable e) {
            System.out.println("FAIL " + e);
        }
        System.out.flush();
    }

    private static boolean send(Object im, Method m, KeyEvent ev) throws Exception {
        Object r = m.invoke(im, ev, 0); // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        return !(r instanceof Boolean) || (Boolean) r;
    }
}
