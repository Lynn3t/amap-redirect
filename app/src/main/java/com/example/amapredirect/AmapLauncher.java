package com.example.amapredirect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

public class AmapLauncher {

    private static final String AMAP_PACKAGE = "com.autonavi.minimap";

    public static boolean launch(Activity activity, IntentParser.Destination dest, String navMode) {
        if (dest == null || !dest.isValid()) return false;

        if (!isAmapInstalled(activity)) {
            Toast.makeText(activity, "Amap is not installed", Toast.LENGTH_SHORT).show();
            return false;
        }

        Uri amapUri;
        if (dest.type == IntentParser.IntentType.NAVIGATION) {
            amapUri = buildNavigationUri(dest.name, navMode);
        } else {
            amapUri = buildSearchUri(dest.name);
        }

        Intent amapIntent = new Intent(Intent.ACTION_VIEW, amapUri);
        amapIntent.setPackage(AMAP_PACKAGE);
        amapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            activity.startActivity(amapIntent);
            Toast.makeText(activity, "Redirecting to Amap…", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * amapuri://route/plan/?dname=Beijing+Airport&dev=0&t=0
     */
    private static Uri buildNavigationUri(String name, String navMode) {
        return new Uri.Builder()
                .scheme("amapuri")
                .authority("route")
                .appendPath("plan")
                .appendQueryParameter("sourceApplication", "AmapRedirect")
                .appendQueryParameter("dname", name)
                .appendQueryParameter("dev", "0")
                .appendQueryParameter("t", navMode != null ? navMode : "0")
                .build();
    }

    /**
     * amapuri://poi?sourceApplication=AmapRedirect&keyword=Beijing+Airport
     */
    private static Uri buildSearchUri(String name) {
        return new Uri.Builder()
                .scheme("amapuri")
                .authority("poi")
                .appendQueryParameter("sourceApplication", "AmapRedirect")
                .appendQueryParameter("keyword", name)
                .build();
    }

    private static boolean isAmapInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(AMAP_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
