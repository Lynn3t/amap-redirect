package com.example.amapredirect;

import android.net.Uri;
import android.text.TextUtils;

public class IntentParser {

    public enum IntentType {
        NAVIGATION,
        GEO_VIEW
    }

    public static class Destination {
        public final IntentType type;
        public final String name;

        public Destination(IntentType type, String name) {
            this.type = type;
            this.name = name;
        }

        public boolean isValid() {
            return !TextUtils.isEmpty(name);
        }
    }

    public static Destination parse(Uri uri) {
        if (uri == null) return null;

        String scheme = uri.getScheme();
        if (scheme == null) return null;

        Destination dest;
        switch (scheme) {
            case "google.navigation":
                dest = parseNavigation(uri);
                break;
            case "geo":
                dest = parseGeo(uri);
                break;
            case "http":
            case "https":
                dest = parseHttpUrl(uri);
                break;
            default:
                return null;
        }

        return (dest != null && dest.isValid()) ? dest : null;
    }

    /**
     * google.navigation:q=Beijing+Airport
     * google.navigation:q=天安门&mode=d
     */
    private static Destination parseNavigation(Uri uri) {
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) return null;

        String query = extractParam(ssp, "q");
        if (query == null) {
            query = uri.getQueryParameter("q");
        }
        if (query == null) return null;

        query = query.trim();
        if (query.isEmpty() || looksLikeCoordinates(query)) return null;

        return new Destination(IntentType.NAVIGATION, query);
    }

    /**
     * geo:0,0?q=Beijing+Airport
     * geo:39.9,116.4?q=Tiananmen
     */
    private static Destination parseGeo(Uri uri) {
        String qParam = uri.getQueryParameter("q");
        if (qParam == null || qParam.trim().isEmpty()) return null;

        qParam = qParam.trim();

        // Strip coordinate prefix from "lat,lng(Label)" format
        int parenStart = qParam.indexOf('(');
        if (parenStart > 0 && qParam.endsWith(")")) {
            String label = qParam.substring(parenStart + 1, qParam.length() - 1).trim();
            if (!label.isEmpty()) {
                return new Destination(IntentType.GEO_VIEW, label);
            }
        }

        // Skip if q is just coordinates
        if (looksLikeCoordinates(qParam)) return null;

        return new Destination(IntentType.GEO_VIEW, qParam);
    }

    /**
     * https://maps.google.com/maps?daddr=Beijing
     * https://www.google.com/maps/dir/?api=1&destination=Beijing+Airport
     * https://www.google.com/maps/place/Tiananmen/...
     * https://www.google.com/maps/search/coffee/...
     */
    private static Destination parseHttpUrl(Uri uri) {
        String host = uri.getHost();
        if (host == null) return null;
        if (!host.contains("google.com") && !host.contains("google.cn")) return null;

        String path = uri.getPath();

        // Direction URLs
        String destination = uri.getQueryParameter("destination");
        if (isUsableName(destination)) {
            return new Destination(IntentType.NAVIGATION, destination.trim());
        }

        String daddr = uri.getQueryParameter("daddr");
        if (isUsableName(daddr)) {
            return new Destination(IntentType.NAVIGATION, daddr.trim());
        }

        // /maps/dir/origin/destination path format
        if (path != null && path.contains("/dir/")) {
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length; i++) {
                if ("dir".equals(segments[i]) && i + 2 < segments.length) {
                    String dest = Uri.decode(segments[segments.length - 1]).trim();
                    if (isUsableName(dest)) {
                        return new Destination(IntentType.NAVIGATION, dest);
                    }
                }
            }
        }

        // /maps/place/Name or /maps/search/Name
        if (path != null) {
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length; i++) {
                if (("place".equals(segments[i]) || "search".equals(segments[i]))
                        && i + 1 < segments.length) {
                    String name = Uri.decode(segments[i + 1]).trim();
                    if (isUsableName(name)) {
                        return new Destination(IntentType.GEO_VIEW, name);
                    }
                }
            }
        }

        // Generic q param
        String q = uri.getQueryParameter("q");
        if (isUsableName(q)) {
            return new Destination(IntentType.GEO_VIEW, q.trim());
        }

        return null;
    }

    private static boolean isUsableName(String s) {
        return s != null && !s.trim().isEmpty() && !looksLikeCoordinates(s.trim());
    }

    /**
     * Returns true if the string looks like "lat,lng" coordinates.
     * We skip these — only names/addresses are useful.
     */
    private static boolean looksLikeCoordinates(String s) {
        if (s == null) return false;
        int commaIdx = s.indexOf(',');
        if (commaIdx <= 0 || commaIdx >= s.length() - 1) return false;
        try {
            Double.parseDouble(s.substring(0, commaIdx).trim());
            Double.parseDouble(s.substring(commaIdx + 1).trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Extract a param value from a scheme-specific-part string like "q=value&mode=d".
     */
    private static String extractParam(String ssp, String key) {
        String prefix = key + "=";
        int start;
        if (ssp.startsWith(prefix)) {
            start = prefix.length();
        } else {
            int idx = ssp.indexOf("&" + prefix);
            if (idx < 0) return null;
            start = idx + prefix.length() + 1;
        }
        int end = ssp.indexOf('&', start);
        if (end < 0) end = ssp.length();
        try {
            return Uri.decode(ssp.substring(start, end));
        } catch (Exception e) {
            return ssp.substring(start, end);
        }
    }
}
