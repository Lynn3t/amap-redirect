package com.example.amapredirect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

/**
 * Builds and launches Amap intents from parsed destination info.
 */
public class AmapLauncher {

    private static final String AMAP_PACKAGE = "com.autonavi.minimap";

    /**
     * Launch Amap with the given destination. Returns true if launched successfully.
     */
    public static boolean launch(Activity activity, IntentParser.Destination dest, String navMode) {
        if (dest == null) return false;

        if (!isAmapInstalled(activity)) {
            Toast.makeText(activity, "Amap is not installed", Toast.LENGTH_SHORT).show();
            return false;
        }

        Uri amapUri;
        if (dest.type == IntentParser.IntentType.NAVIGATION) {
            amapUri = buildNavigationUri(dest, navMode);
        } else {
            amapUri = buildGeoViewUri(dest);
        }

        if (amapUri == null) return false;

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
     * Build navigation URI for Amap.
     * amapuri://route/plan/?dname=...&dev=0&t=mode
     * amapuri://route/plan/?dlat=...&dlon=...&dev=1&t=mode
     */
    private static Uri buildNavigationUri(IntentParser.Destination dest, String navMode) {
        Uri.Builder builder = new Uri.Builder()
                .scheme("amapuri")
                .authority("route")
                .appendPath("plan")
                .appendQueryParameter("sourceApplication", "AmapRedirect")
                .appendQueryParameter("t", navMode != null ? navMode : "0");

        if (dest.hasName()) {
            builder.appendQueryParameter("dname", dest.name);
            // If we also have coordinates, include them
            if (dest.hasCoordinates()) {
                builder.appendQueryParameter("dlat", dest.lat);
                builder.appendQueryParameter("dlon", dest.lon);
                builder.appendQueryParameter("dev", "1"); // WGS-84
            } else {
                builder.appendQueryParameter("dev", "0");
            }
        } else if (dest.hasCoordinates()) {
            builder.appendQueryParameter("dlat", dest.lat);
            builder.appendQueryParameter("dlon", dest.lon);
            builder.appendQueryParameter("dev", "1"); // WGS-84
        } else {
            return null;
        }

        return builder.build();
    }

    /**
     * Build geo view URI for Amap.
     * amapuri://poi?keyword=...
     * amapuri://viewMap?sourceApplication=...&lat=...&lon=...&dev=1
     */
    private static Uri buildGeoViewUri(IntentParser.Destination dest) {
        if (dest.hasName()) {
            return new Uri.Builder()
                    .scheme("amapuri")
                    .authority("poi")
                    .appendQueryParameter("sourceApplication", "AmapRedirect")
                    .appendQueryParameter("keyword", dest.name)
                    .build();
        } else if (dest.hasCoordinates()) {
            return new Uri.Builder()
                    .scheme("amapuri")
                    .authority("viewMap")
                    .appendQueryParameter("sourceApplication", "AmapRedirect")
                    .appendQueryParameter("lat", dest.lat)
                    .appendQueryParameter("lon", dest.lon)
                    .appendQueryParameter("dev", "1") // WGS-84
                    .build();
        }
        return null;
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
