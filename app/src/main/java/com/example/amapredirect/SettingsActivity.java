package com.example.amapredirect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import java.io.File;

public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use MODE_WORLD_READABLE so XSharedPreferences can read the prefs
        // from the hooked process
        getPreferenceManager().setSharedPreferencesName("settings");
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);

        addPreferencesFromResource(R.xml.pref_settings);

        // Set up files dir so the hook (running in Maps' process) can write logs
        setupLogDir();

        findPreference("view_log").setOnPreferenceClickListener(pref -> {
            startActivity(new Intent(this, LogActivity.class));
            return true;
        });
    }

    private void setupLogDir() {
        File dataDir = getApplicationInfo().dataDir != null
                ? new File(getApplicationInfo().dataDir)
                : new File("/data/data/com.example.amapredirect");
        File filesDir = new File(dataDir, "files");
        filesDir.mkdirs();
        dataDir.setExecutable(true, false);
        filesDir.setExecutable(true, false);
        filesDir.setWritable(true, false);
        filesDir.setReadable(true, false);
    }
}
