package com.example.amapredirect;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

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

        XposedBridge.log(TAG + ": Hooking Google Maps");

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

        XposedBridge.log(TAG + ": Incoming URI: " + data.toString());

        // Only intercept ACTION_VIEW or google.navigation scheme
        String action = intent.getAction();
        if (action == null || !Intent.ACTION_VIEW.equals(action)) {
            String scheme = data.getScheme();
            if (!"google.navigation".equals(scheme)) {
                return false;
            }
        }

        IntentParser.Destination dest = IntentParser.parse(data);
        if (dest == null || !dest.isValid()) {
            XposedBridge.log(TAG + ": Could not parse destination");
            return false;
        }

        String info = dest.hasName() ? dest.name : (dest.lat + "," + dest.lon);
        XposedBridge.log(TAG + ": Redirecting: " + info);

        String navMode = prefs.getString("nav_mode", "0");

        if (AmapLauncher.launch(activity, dest, navMode)) {
            XposedBridge.log(TAG + ": Launched Amap, killing Maps");
            activity.finishAffinity();
            System.exit(0);
            return true;
        }

        return false;
    }
}
