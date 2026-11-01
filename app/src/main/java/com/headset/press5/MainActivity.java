package com.headset.press5;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** מסך יחיד: הפעלה/כיבוי, הגדרת מספר הנגיעות, שיטת ההקשה ויומן אבחון. */
public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSIONS = 100;

    private Button btnToggle;
    private TextView tvStatus;
    private TextView tvLog;
    private RadioGroup rgTaps;
    private RadioGroup rgMethod;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.apply(this);
        setContentView(R.layout.activity_main);

        btnToggle = (Button) findViewById(R.id.btnToggle);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvLog = (TextView) findViewById(R.id.tvLog);
        rgTaps = (RadioGroup) findViewById(R.id.rgTaps);
        rgMethod = (RadioGroup) findViewById(R.id.rgMethod);

        setupFocusChain();

        rgTaps.check(AppPrefs.getTaps(this) == 1 ? R.id.rbTapsOne : R.id.rbTapsTwo);
        String m = AppPrefs.getMethod(this);
        rgMethod.check(AppPrefs.METHOD_ROOT.equals(m) ? R.id.rbRoot
                : AppPrefs.METHOD_ACCESSIBILITY.equals(m) ? R.id.rbAccessibility : R.id.rbAuto);

        rgTaps.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int id) {
                AppPrefs.setTaps(MainActivity.this, id == R.id.rbTapsOne ? 1 : 2);
            }
        });
        rgMethod.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int id) {
                AppPrefs.setMethod(MainActivity.this, id == R.id.rbRoot ? AppPrefs.METHOD_ROOT
                        : id == R.id.rbAccessibility ? AppPrefs.METHOD_ACCESSIBILITY : AppPrefs.METHOD_AUTO);
            }
        });

        final android.widget.CheckBox cbHide = (android.widget.CheckBox) findViewById(R.id.cbHideNotif);
        cbHide.setChecked(AppPrefs.getHideNotification(this));
        cbHide.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                AppPrefs.setHideNotification(MainActivity.this, checked);
                // ההתראה נבנית בעת הפעלת השירות - אם הוא פעיל, מפעילים אותו מחדש.
                if (AppPrefs.isEnabled(MainActivity.this)) {
                    stopService(new Intent(MainActivity.this, CallWatcherService.class));
                    startWatcher();
                }
            }
        });

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (AppPrefs.isEnabled(MainActivity.this)) {
                    stopWatcher();
                } else {
                    startWithPermissions();
                }
            }
        });
        findViewById(R.id.btnAccessibility).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        findViewById(R.id.btnTestSend).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ההקשה נעשית על מה שמוצג במסך באותו רגע - לכן מחכים 5 שניות,
                // כדי שאפשר יהיה לעבור למסך השיחה (ולא לבדוק את מסך האפליקציה עצמה).
                DiagnosticsLog.add(MainActivity.this, "בדיקה ידנית: הקשת 5 בעוד 5 שניות - עבור למסך השיחה");
                final Context appCtx = getApplicationContext();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Key5Sender.send(appCtx);
                    }
                }, 5000L);
                refresh();
            }
        });
        findViewById(R.id.btnDiagHeadset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // מאזין 30 שניות כאילו יש שיחה: גע באוזנייה ובדוק מה מופיע ביומן.
                final Context app = getApplicationContext();
                DiagnosticsLog.add(app, "אבחון אוזנייה: 30 שניות - גע באוזנייה עכשיו");
                HeadsetPressController.arm(app);
                logConnectionState(app);
                clearSystemLog();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager)
                                app.getSystemService(Context.TELEPHONY_SERVICE);
                        // לא משחררים אם בינתיים התחילה שיחה אמיתית.
                        if (tm == null || tm.getCallState() == android.telephony.TelephonyManager.CALL_STATE_IDLE) {
                            HeadsetPressController.disarm(app);
                        }
                        dumpBluetoothSystemLog(app);
                        DiagnosticsLog.add(app, "אבחון אוזנייה: הסתיים");
                    }
                }, 30000L);
            }
        });
        findViewById(R.id.btnRequestRoot).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // קריאת su בזמן שהאפליקציה על המסך - חלון ההרשאה של מנהל ה-root יקפוץ עכשיו.
                // תקרת זמן ארוכה, כדי שיהיה זמן ללחוץ "אישור" (ולבחור "לצמיתות" אם יש).
                final Context app = getApplicationContext();
                DiagnosticsLog.add(app, "בקשת root: ממתין לאישור בחלון (עד 40 שניות)");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        RootShell.Result r = RootShell.exec("id", 40000L);
                        DiagnosticsLog.add(app, r.success ? "root: מאושר"
                                : (r.timedOut ? "root: לא אושר בזמן" : "root: נדחה/לא זמין"));
                    }
                }, "press5-rootcheck").start();
            }
        });
        findViewById(R.id.btnRefreshLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        findViewById(R.id.btnClearLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DiagnosticsLog.clear(MainActivity.this);
                refresh();
            }
        });
    }

    /**
     * שרשרת פוקוס מפורשת לכל הפקדים לפי סדר הופעתם במסך - בלעדיה ה-D-pad
     * מדלג על כפתורים (במיוחד סביב ה-RadioGroup ותיבת הסימון).
     */
    private void setupFocusChain() {
        int[] order = {
                R.id.btnToggle,
                R.id.rbTapsOne, R.id.rbTapsTwo,
                R.id.rbAuto, R.id.rbRoot, R.id.rbAccessibility,
                R.id.btnAccessibility, R.id.btnRequestRoot,
                R.id.cbHideNotif,
                R.id.btnTestSend, R.id.btnDiagHeadset,
                R.id.btnRefreshLog
        };
        for (int i = 0; i < order.length; i++) {
            View v = findViewById(order[i]);
            if (v == null) continue;
            if (i > 0) v.setNextFocusUpId(order[i - 1]);
            if (i < order.length - 1) v.setNextFocusDownId(order[i + 1]);
        }
        // כפתור "נקה יומן" יושב לצד "רענן יומן": ימינה/שמאלה ביניהם, ולמעלה/למטה כמו חברו.
        View clear = findViewById(R.id.btnClearLog);
        if (clear != null) {
            clear.setNextFocusUpId(R.id.btnDiagHeadset);
            clear.setNextFocusLeftId(R.id.btnRefreshLog);
        }
        View refresh = findViewById(R.id.btnRefreshLog);
        if (refresh != null) refresh.setNextFocusRightId(R.id.btnClearLog);
    }

    /**
     * אבחון חיבור האוזנייה. קודם מה שהמערכת עצמה מדווחת (בלי רוט): האם בלוטוס פעיל,
     * האם פרופיל המדיה/השיחה (A2DP/HEADSET) מחובר, ולאן מנותב השמע. אחר כך (רוט):
     * התקני הקלט שקיימים.
     */
    private void logConnectionState(final Context app) {
        try {
            android.bluetooth.BluetoothAdapter ad = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            StringBuilder sb = new StringBuilder("בלוטוס: ");
            if (ad == null) {
                sb.append("אין מתאם");
            } else {
                sb.append(ad.isEnabled() ? "פעיל" : "כבוי");
                sb.append(", מדיה=").append(profileName(ad.getProfileConnectionState(
                        android.bluetooth.BluetoothProfile.A2DP)));
                sb.append(", שיחה=").append(profileName(ad.getProfileConnectionState(
                        android.bluetooth.BluetoothProfile.HEADSET)));
                sb.append(", מותאמים:");
                for (android.bluetooth.BluetoothDevice d : ad.getBondedDevices()) {
                    sb.append(' ').append(d.getName());
                }
            }
            DiagnosticsLog.add(app, sb.toString());
        } catch (Exception e) {
            DiagnosticsLog.add(app, "בלוטוס: שגיאה בקריאת המצב (" + e.getClass().getSimpleName() + ")");
        }
        android.media.AudioManager am = (android.media.AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            DiagnosticsLog.add(app, "ניתוב שמע: מדיה-בלוטוס=" + am.isBluetoothA2dpOn()
                    + ", שיחה-בלוטוס(SCO)=" + am.isBluetoothScoOn()
                    + ", אוזניות חוטיות=" + am.isWiredHeadsetOn());
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String nl = String.valueOf((char) 10);
                String devs = RootShell.execOutput("getevent -p | grep -E 'name:'", 8000L);
                DiagnosticsLog.add(app, "התקני קלט: " + (devs == null ? "לא זמין" : devs.trim().replace(nl, " | ")));
            }
        }, "press5-conn").start();
    }

    private static String profileName(int state) {
        switch (state) {
            case 2: return "מחובר";
            case 1: return "מתחבר";
            case 3: return "מתנתק";
            default: return "לא מחובר";
        }
    }

    /** מנקה את יומן המערכת כדי שבסוף האבחון יישארו רק האירועים מהחלון הזה. */
    private void clearSystemLog() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RootShell.exec("logcat -c", 5000L);
            }
        }, "press5-logcat-clear").start();
    }

    /**
     * אוסף מיומן המערכת את מה שהבלוטוס/הלחצנים דיווחו בזמן האבחון (פקודות HFP
     * כמו AT+CKPD, AVRCP, לחצני מדיה) - כך רואים מה האוזנייה שלחה בפועל.
     */
    private void dumpBluetoothSystemLog(final Context app) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out = RootShell.execOutput("logcat -d -v time | grep -i -E "
                        + "'ckpd|avrcp|bvra|btif_hf|btif_rc|headsetstatemachine|bluetoothheadset|"
                        + "mediabutton|media_button|AT[+]|vgs|vgm'", 10000L);
                if (out == null || out.trim().length() == 0) {
                    DiagnosticsLog.add(app, "יומן מערכת: אין אירועי בלוטוס/לחצנים בחלון האבחון");
                    return;
                }
                String[] lines = out.trim().split(String.valueOf((char) 10));
                int from = Math.max(0, lines.length - 25);
                DiagnosticsLog.add(app, "יומן מערכת: " + (lines.length - from) + " שורות אחרונות מתוך " + lines.length);
                for (int i = from; i < lines.length; i++) {
                    String l = lines[i].trim();
                    DiagnosticsLog.add(app, "sys: " + (l.length() > 110 ? l.substring(0, 110) : l));
                }
            }
        }, "press5-logcat-dump").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean on = AppPrefs.isEnabled(this);
        btnToggle.setText(on ? R.string.btn_stop : R.string.btn_start);
        btnToggle.setBackgroundResource(on ? R.drawable.btn_on : R.drawable.btn_primary);
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("גרסה ").append(getPackageManager().getPackageInfo(getPackageName(), 0).versionName).append('\n');
        } catch (Exception ignored) {
        }
        sb.append(getString(on ? R.string.status_service_on : R.string.status_service_off)).append('\n');
        sb.append(getString(KeypadClickService.isConnected()
                ? R.string.status_accessibility_on : R.string.status_accessibility_off));
        if (!hasPhonePermission()) sb.append('\n').append(getString(R.string.status_phone_perm_missing));
        tvStatus.setText(sb.toString());
        tvLog.setText(DiagnosticsLog.readAll(this));
    }

    private boolean hasPhonePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    /** מבקש מראש את כל ההרשאות, ורק אחרי אישור מפעיל (לפי המדריך - בלי נתיב חלקי). */
    private void startWithPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> missing = new ArrayList<>();
            if (!hasPhonePermission()) missing.add(Manifest.permission.READ_PHONE_STATE);
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (!missing.isEmpty()) {
                requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
                return;
            }
        }
        startWatcher();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // התראות לא חובה להפעלה; הרשאת הטלפון חובה.
        if (requestCode == REQUEST_PERMISSIONS && hasPhonePermission()) startWatcher();
        refresh();
    }

    private void startWatcher() {
        AppPrefs.setEnabled(this, true);
        Intent svc = new Intent(this, CallWatcherService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        requestBatteryExemption();
        refresh();
    }

    private void stopWatcher() {
        AppPrefs.setEnabled(this, false);
        stopService(new Intent(this, CallWatcherService.class));
        refresh();
    }

    /** ברגע ההפעלה - הכי הגיוני למשתמש (עד API 23 אין מנגנון כזה). */
    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
