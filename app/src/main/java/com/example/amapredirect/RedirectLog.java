package com.example.amapredirect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RedirectLog {

    static final String LOG_PATH = "/data/data/com.example.amapredirect/files/redirect.log";

    public static synchronized void append(String message) {
        try {
            File file = new File(LOG_PATH);
            String ts = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
            FileWriter fw = new FileWriter(file, true);
            fw.write(ts + "  " + message + "\n");
            fw.close();
            file.setReadable(true, false);
        } catch (Exception ignored) {
        }
    }

    public static String read() {
        try {
            File file = new File(LOG_PATH);
            if (!file.exists()) return "No logs yet.\n\nLogs appear here after a Google Maps intent is intercepted.";
            BufferedReader br = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
            return sb.length() == 0 ? "No logs yet." : sb.toString();
        } catch (Exception e) {
            return "Error reading log: " + e.getMessage();
        }
    }

    public static void clear() {
        try {
            new File(LOG_PATH).delete();
        } catch (Exception ignored) {
        }
    }
}
