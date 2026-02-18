package com.example.amapredirect;

import android.app.Activity;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;

import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "AmapRedirect";
    private static final String GOOGLE_MAPS_PKG = "com.google.android.apps.maps";

    private XSharedPreferences prefs;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!GOOGLE_MAPS_PKG.equals(lpparam.packageName)) return;

        log("Hooking Google Maps");

        prefs = new XSharedPreferences("com.example.amapredirect", "settings");
        prefs.makeWorldReadable();

        XC_MethodHook intentHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                Intent intent = activity.getIntent();
                if (handleIntent(activity, intent)) {
                    param.setResult(null); // prevent onCreate from running
                }
            }
        };

        XC_MethodHook newIntentHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                Intent intent = (Intent) param.args[0];
                handleIntent(activity, intent);
            }
        };

        Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
        XposedBridge.hookAllMethods(activityClass, "onCreate", intentHook);
        XposedBridge.hookAllMethods(activityClass, "onNewIntent", newIntentHook);
    }

    /**
     * Returns true if the intent was intercepted and redirected to Amap.
     */
    private boolean handleIntent(Activity activity, Intent intent) {
        if (intent == null) return false;

        prefs.reload();
        if (!prefs.getBoolean("enable_redirect", true)) return false;

        Uri data = intent.getData();
        if (data == null) return false;

        log("Incoming URI: " + data.toString());

        // Only intercept ACTION_VIEW or google.navigation scheme
        String action = intent.getAction();
        if (action == null || !Intent.ACTION_VIEW.equals(action)) {
            String scheme = data.getScheme();
            if (!"google.navigation".equals(scheme)) {
                return false;
            }
        }

        IntentParser.Destination dest = IntentParser.parse(data);
        if (dest != null && dest.isValid()) {
            log("Parsed name: " + dest.name);
        }

        // Fallback: extract coordinates and reverse-geocode to a name
        if (dest == null || !dest.isValid()) {
            log("No name found, trying reverse-geocode…");
            dest = resolveFromCoordinates(activity, data);
        }

        if (dest == null || !dest.isValid()) {
            log("FAILED: Could not resolve destination to a name");
            return false;
        }

        log("Redirecting to Amap: " + dest.name);

        String navMode = prefs.getString("nav_mode", "0");

        if (AmapLauncher.launch(activity, dest, navMode)) {
            log("Launched Amap, killing Maps");
            activity.finishAffinity();
            System.exit(0);
            return true;
        }

        log("FAILED: AmapLauncher.launch returned false");
        return false;
    }

    /**
     * Extracts coordinates from the URI and uses Android's Geocoder
     * to reverse-geocode them into a place name.
     */
    private IntentParser.Destination resolveFromCoordinates(Activity activity, Uri data) {
        double[] coords = IntentParser.extractCoordinates(data);
        if (coords == null) {
            log("No coordinates found in URI");
            return null;
        }

        double lat = coords[0];
        double lon = coords[1];
        log("Found coordinates: " + lat + "," + lon);

        try {
            Geocoder geocoder = new Geocoder(activity, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses == null || addresses.isEmpty()) {
                log("Geocoder returned no results");
                return null;
            }

            Address addr = addresses.get(0);
            String name = addr.getAddressLine(0);
            if (name == null || name.trim().isEmpty()) {
                log("Geocoder returned blank address");
                return null;
            }

            name = name.trim();
            log("Geocoded to: " + name);

            IntentParser.IntentType type = "google.navigation".equals(data.getScheme())
                    ? IntentParser.IntentType.NAVIGATION
                    : IntentParser.IntentType.GEO_VIEW;
            return new IntentParser.Destination(type, name);
        } catch (Exception e) {
            log("Geocoder failed: " + e.getMessage());
        }

        return null;
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }
}
