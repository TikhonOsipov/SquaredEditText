package com.tixon.squarededittext;

import android.content.Context;
import android.util.DisplayMetrics;

/**
 * Created by tikhon.osipov on 15.06.2016
 */
public class Utils {
    public static float dpToPx(float dp, Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return dp * (displayMetrics.xdpi / (float) DisplayMetrics.DENSITY_DEFAULT);
    }

    public static int pxToDp(int px, Context context) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }
}
