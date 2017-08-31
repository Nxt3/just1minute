package io.nxt3.just1minute;


import android.content.Context;
import android.util.TypedValue;

import java.util.Calendar;

/**
 * Contains helper functions which are used in Just1MinuteWatchFaceService
 * (Moved hear for readability)
 */
class HelperFunctions {

    /**
     * Converts scale pixels to pixels -- used for setting text sizes
     *
     * @param context context for getResources()
     * @param sp      desired scale pixels pixels
     * @return converted sp to pixels
     */
    static int spToPx(Context context, final float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                context.getResources().getDisplayMetrics());
    }

    /**
     * Scales using the center coordinate given
     * In practice, I find this the best way to manage varying screen sizes / dimensions when
     * positioning elements
     *
     * @param centerCoord to scale relative to
     * @param scale       amount to scale by
     * @return scaled value
     */
    static float scalePosition(int centerCoord, float scale) {
        return ((centerCoord * 2) / scale);
    }

    /**
     * Determines if the current time is between fromMillis and toMillis
     * Does so by calculating an inverse time range based on the minutes
     *
     * @param currentMillis current time in milliseconds
     * @param fromMillis    start time in milliseconds
     * @param toMillis      end time in milliseconds
     * @return whether or not the currentMillis is between fromMillis and toMillis
     */
    static boolean isTimeInRange(long currentMillis, long fromMillis, long toMillis) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentMillis);

        final int currentMinuteOfDay
                = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        calendar.setTimeInMillis(fromMillis);

        final int fromMinuteOfDay
                = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        calendar.setTimeInMillis(toMillis);

        final int toMinuteOfDay
                = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);

        if (fromMinuteOfDay <= toMinuteOfDay) {
            return (currentMinuteOfDay >= fromMinuteOfDay && currentMinuteOfDay < toMinuteOfDay);
        } else {
            return (currentMinuteOfDay >= fromMinuteOfDay || currentMinuteOfDay < toMinuteOfDay);
        }
    }
}
