package com.example.amapredirect;

import android.net.Uri;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntentParser {

    public enum IntentType {
        NAVIGATION,
        GEO_VIEW
    }

    public static class Destination {
        public final IntentType type;
        public final String name;  // may be null
        public final String lat;   // fallback, may be null
        public final String lon;   // fallback, may be null

        public Destination(IntentType type, String name, String lat, String lon) {
            this.type = type;
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }

        public boolean hasName() {
            return !TextUtils.isEmpty(name);
        }

        public boolean hasCoordinates() {
            return !TextUtils.isEmpty(lat) && !TextUtils.isEmpty(lon);
        }

        public boolean isValid() {
            return hasName() || hasCoordinates();
        }
    }

    // Matches Google's protobuf-encoded data: !1d<lon>...!2d<lat>
    private static final Pattern PB_LON = Pattern.compile("!1d(-?[0-9]+\\.?[0-9]*)");
    private static final Pattern PB_LAT = Pattern.compile("!2d(-?[0-9]+\\.?[0-9]*)");

    public static Destination parse(Uri uri) {
        if (uri == null) return null;

        String scheme = uri.getScheme();
        if (scheme == null) return null;

        switch (scheme) {
            case "google.navigation":
                return parseNavigation(uri);
            case "geo":
                return parseGeo(uri);
            case "http":
            case "https":
                return parseHttpUrl(uri);
            default:
                break;
        }

        // Last resort: try to decode protobuf data from the full URI
        return parseProtobufData(uri.toString(), IntentType.NAVIGATION);
    }

    /**
     * google.navigation:q=Beijing+Airport
     * google.navigation:q=天安门&mode=d
     */
    private static Destination parseNavigation(Uri uri) {
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) return null;

        // Check for protobuf data
        if (ssp.contains("!4m") || ssp.contains("!1d")) {
            return parseProtobufData(ssp, IntentType.NAVIGATION);
        }

        String query = extractParam(ssp, "q");
        if (query == null) {
            query = uri.getQueryParameter("q");
        }
        if (query == null) return null;

        query = query.trim();
        if (query.isEmpty()) return null;

        // If it's encoded Google data, parse it
        if (query.startsWith("!") || query.contains("!4m")) {
            return parseProtobufData(query, IntentType.NAVIGATION);
        }

        if (looksLikeCoordinates(query)) {
            return null; // skip bare coordinates
        }

        return new Destination(IntentType.NAVIGATION, query, null, null);
    }

    /**
     * geo:0,0?q=Beijing+Airport
     * geo:39.9,116.4?q=Tiananmen
     */
    private static Destination parseGeo(Uri uri) {
        String qParam = uri.getQueryParameter("q");
        if (qParam == null || qParam.trim().isEmpty()) return null;

        qParam = qParam.trim();

        // Handle "lat,lng(Label)" format
        int parenStart = qParam.indexOf('(');
        if (parenStart > 0 && qParam.endsWith(")")) {
            String label = qParam.substring(parenStart + 1, qParam.length() - 1).trim();
            if (!label.isEmpty()) {
                return new Destination(IntentType.GEO_VIEW, label, null, null);
            }
        }

        if (looksLikeCoordinates(qParam)) return null;

        return new Destination(IntentType.GEO_VIEW, qParam, null, null);
    }

    /**
     * Various Google Maps URL formats, including protobuf-encoded data URLs.
     */
    private static Destination parseHttpUrl(Uri uri) {
        String host = uri.getHost();
        if (host == null) return null;
        if (!host.contains("google.com") && !host.contains("google.cn")) return null;

        String fullUrl = uri.toString();

        // Check for protobuf-encoded data in URL (from Google Assistant)
        if (fullUrl.contains("data=!") || fullUrl.contains("/data/!")) {
            Destination pbDest = parseProtobufData(fullUrl, IntentType.NAVIGATION);
            if (pbDest != null && pbDest.isValid()) return pbDest;
        }

        String path = uri.getPath();

        // Direction URLs with human-readable names
        String destination = uri.getQueryParameter("destination");
        if (isUsableName(destination)) {
            return new Destination(IntentType.NAVIGATION, destination.trim(), null, null);
        }

        String daddr = uri.getQueryParameter("daddr");
        if (isUsableName(daddr)) {
            return new Destination(IntentType.NAVIGATION, daddr.trim(), null, null);
        }

        // /maps/dir/origin/destination path format
        if (path != null && path.contains("/dir/")) {
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length; i++) {
                if ("dir".equals(segments[i]) && i + 2 < segments.length) {
                    String dest = Uri.decode(segments[segments.length - 1]).trim();
                    if (isUsableName(dest)) {
                        return new Destination(IntentType.NAVIGATION, dest, null, null);
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
                        return new Destination(IntentType.GEO_VIEW, name, null, null);
                    }
                }
            }
        }

        // Generic q param
        String q = uri.getQueryParameter("q");
        if (isUsableName(q)) {
            return new Destination(IntentType.GEO_VIEW, q.trim(), null, null);
        }

        // Fallback: try to extract any coords from the full URL
        return parseProtobufData(fullUrl, IntentType.NAVIGATION);
    }

    /**
     * Parse Google's protobuf-encoded URL data.
     * Format: ...!1d<longitude>...!2d<latitude>...
     * Example: !4m9!4m8!1m0!1m5!1m1!19sChIJ...!2m2!1d119.007!2d25.425!3e0
     *
     * Returns coordinates as fallback when no name is available.
     */
    private static Destination parseProtobufData(String data, IntentType type) {
        if (data == null) return null;

        Matcher lonMatcher = PB_LON.matcher(data);
        Matcher latMatcher = PB_LAT.matcher(data);

        // Find the lon/lat pair inside !2m2 (coordinate container)
        String lon = null;
        String lat = null;

        while (lonMatcher.find()) {
            lon = lonMatcher.group(1);
        }
        while (latMatcher.find()) {
            lat = latMatcher.group(1);
        }

        if (lon != null && lat != null) {
            try {
                double latD = Double.parseDouble(lat);
                double lonD = Double.parseDouble(lon);
                if (latD >= -90 && latD <= 90 && lonD >= -180 && lonD <= 180) {
                    return new Destination(type, null, lat, lon);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        return null;
    }

    private static boolean isUsableName(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        String trimmed = s.trim();
        // Reject Google protobuf data
        if (trimmed.startsWith("!") || trimmed.contains("!4m") || trimmed.contains("!1d")) {
            return false;
        }
        // Reject bare coordinates
        if (looksLikeCoordinates(trimmed)) return false;
        // Reject strings starting with "data=" (encoded path segments)
        if (trimmed.startsWith("data=")) return false;
        return true;
    }

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
