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

        // Hook the general Activity class inside Google Maps process.
        // This catches all activities (MapsActivity, NavigationActivity, etc.)
        // that receive navigation/geo intents.
        XC_MethodHook intentHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                Intent intent = activity.getIntent();
                handleIntent(activity, intent);
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

        // Hook Activity.onCreate and Activity.onNewIntent in the Maps process
        Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
        XposedBridge.hookAllMethods(activityClass, "onCreate", intentHook);
        XposedBridge.hookAllMethods(activityClass, "onNewIntent", newIntentHook);
    }

    private void handleIntent(Activity activity, Intent intent) {
        if (intent == null) return;

        // Reload prefs to get latest settings
        prefs.reload();
        if (!prefs.getBoolean("enable_redirect", true)) return;

        Uri data = intent.getData();
        if (data == null) return;

        // Skip if this is an internal Google Maps intent (no external URI)
        String action = intent.getAction();
        if (action == null || !Intent.ACTION_VIEW.equals(action)) {
            // google.navigation intents may not have ACTION_VIEW, check scheme
            String scheme = data.getScheme();
            if (!"google.navigation".equals(scheme)) {
                return;
            }
        }

        IntentParser.Destination dest = IntentParser.parse(data);
        if (dest == null) return;

        XposedBridge.log(TAG + ": Intercepted destination: " + dest.name);

        String navMode = prefs.getString("nav_mode", "0");

        if (AmapLauncher.launch(activity, dest, navMode)) {
            // Kill Google Maps process so it doesn't linger in background
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
