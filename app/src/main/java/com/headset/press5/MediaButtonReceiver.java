package com.headset.press5;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * מסלול הגיבוי לתפיסת לחצן האוזנייה (בעיקר אנדרואיד 4.4, שם אין MediaSession).
 * אם האירוע נצרך (שיחה פעילה והלחצן הוא לחצן נגיעה) - מבטלים את השידור כדי
 * שהמחייגן לא יקבל אותו.
 */
public class MediaButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
        boolean consumed = HeadsetPressController.onKeyEvent(context, HeadsetPressController.extractKeyEvent(intent));
        if (consumed && isOrderedBroadcast()) abortBroadcast();
    }
}
