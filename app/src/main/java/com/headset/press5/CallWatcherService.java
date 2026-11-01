package com.headset.press5;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

/**
 * שירות קדמי תמידי: מאזין למצב השיחה. כשהשיחה פעילה (OFFHOOK) "מחמש"
 * את HeadsetPressController, וכשהיא נגמרת - משחרר. שכבות האמינות לפי
 * המדריך: שירות קדמי + WakeLock + START_STICKY.
 */
public class CallWatcherService extends Service {

    private static final String CHANNEL_ID = "press5_channel";
    private static final int NOTIFICATION_ID = 1;

    private PowerManager.WakeLock wakeLock;
    private TelephonyManager telephony;
    private PhoneStateListener listener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannelIfNeeded();
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireWakeLock();
        registerCallListener();
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void registerCallListener() {
        if (listener != null) return;
        telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony == null) return;
        listener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    HeadsetPressController.arm(CallWatcherService.this);
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    HeadsetPressController.disarm(CallWatcherService.this);
                }
            }
        };
        try {
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (SecurityException e) {
            DiagnosticsLog.add(this, "חסרה הרשאת טלפון - לא ניתן להאזין לשיחות");
            listener = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (telephony != null && listener != null) {
            telephony.listen(listener, PhoneStateListener.LISTEN_NONE);
        }
        listener = null;
        HeadsetPressController.disarm(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Press5::CallWatcher");
            wakeLock.acquire();
        }
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notification_title), NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
