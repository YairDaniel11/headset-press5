package com.headset.press5;

import android.app.Activity;
import android.content.res.Configuration;

/** בוחר ערכת נושא כהה/בהירה לפי המערכת (כמו באפליקציות האחרות); ללא מידע - כהה. */
public final class ThemeUtil {
    private ThemeUtil() {}

    /** לקרוא ב-onCreate, לפני setContentView. */
    public static void apply(Activity activity) {
        int night = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        activity.setTheme(night == Configuration.UI_MODE_NIGHT_NO ? R.style.AppTheme_Light : R.style.AppTheme_Dark);
    }
}
