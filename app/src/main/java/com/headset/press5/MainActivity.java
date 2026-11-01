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
        setContentView(R.layout.activity_main);

        btnToggle = (Button) findViewById(R.id.btnToggle);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvLog = (TextView) findViewById(R.id.tvLog);
        rgTaps = (RadioGroup) findViewById(R.id.rgTaps);
        rgMethod = (RadioGroup) findViewById(R.id.rgMethod);

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

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean on = AppPrefs.isEnabled(this);
        btnToggle.setText(on ? R.string.btn_stop : R.string.btn_start);
        StringBuilder sb = new StringBuilder();
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
