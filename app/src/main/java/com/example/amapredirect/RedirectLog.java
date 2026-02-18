package com.example.amapredirect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RedirectLog {

    static final String LOG_PATH = "/data/data/com.example.amapredirect/files/redirect.log";

    public static void append(String message) {
        if (message == null || message.trim().isEmpty()) return;
        String line = formatLine(message);
        if (!appendWithRoot(line)) {
            appendLocal(line);
        }
    }

    private static String formatLine(String message) {
        String ts = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        return ts + "  " + message + "\n";
    }

    private static boolean appendWithRoot(String line) {
        Process proc = null;
        try {
            String cmd = "cat >> " + LOG_PATH + "; chmod 644 " + LOG_PATH;
            proc = new ProcessBuilder("su", "-c", cmd).start();
            OutputStream os = proc.getOutputStream();
            os.write(line.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            int exit = proc.waitFor();
            return exit == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (proc != null) {
                proc.destroy();
            }
        }
    }

    static synchronized void appendLocal(String line) {
        try {
            File file = new File(LOG_PATH);
            FileWriter fw = new FileWriter(file, true);
            fw.write(line);
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
