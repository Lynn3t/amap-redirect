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
        public final String name;

        public Destination(IntentType type, String name) {
            this.type = type;
            this.name = name;
        }

        public boolean hasName() {
            return name != null && !name.trim().isEmpty();
        }

        public boolean isValid() {
            return hasName();
        }
    }

    // For extracting coordinates from Google's protobuf-encoded data
    private static final Pattern PB_LON = Pattern.compile("!1d(-?[0-9]+\\.?[0-9]*)");
    private static final Pattern PB_LAT = Pattern.compile("!2d(-?[0-9]+\\.?[0-9]*)");

    /**
     * Extract raw coordinates from a URI when no name is available.
     * Returns double[]{lat, lon} or null. Used as intermediate step for reverse geocoding.
     */
    public static double[] extractCoordinates(Uri uri) {
        if (uri == null) return null;

        String scheme = uri.getScheme();
        if (scheme == null) return null;

        String full = uri.toString();

        // geo:lat,lon or geo:lat,lon?q=...
        if ("geo".equals(scheme)) {
            String ssp = uri.getSchemeSpecificPart();
            if (ssp != null) {
                // Strip query part
                int qIdx = ssp.indexOf('?');
                String coords = qIdx > 0 ? ssp.substring(0, qIdx) : ssp;
                double[] parsed = parseCoordPair(coords);
                if (parsed != null) return parsed;
            }
        }

        // Protobuf-encoded data: !1d<lon>!2d<lat>
        if (full.contains("!1d") && full.contains("!2d")) {
            return extractProtobufCoords(full);
        }

        // Bare coordinate query param: q=39.9,116.4
        String q = uri.getQueryParameter("q");
        if (q != null) {
            double[] parsed = parseCoordPair(q.trim());
            if (parsed != null) return parsed;
        }

        // google.navigation:q=39.9,116.4
        if ("google.navigation".equals(scheme)) {
            String ssp = uri.getSchemeSpecificPart();
            if (ssp != null) {
                String qVal = extractParam(ssp, "q");
                if (qVal != null) {
                    double[] parsed = parseCoordPair(qVal.trim());
                    if (parsed != null) return parsed;
                }
            }
        }

        return null;
    }

    private static double[] extractProtobufCoords(String data) {
        Matcher lonMatcher = PB_LON.matcher(data);
        Matcher latMatcher = PB_LAT.matcher(data);

        String lon = null, lat = null;
        while (lonMatcher.find()) lon = lonMatcher.group(1);
        while (latMatcher.find()) lat = latMatcher.group(1);

        if (lon != null && lat != null) {
            try {
                double latD = Double.parseDouble(lat);
                double lonD = Double.parseDouble(lon);
                if (latD >= -90 && latD <= 90 && lonD >= -180 && lonD <= 180) {
                    return new double[]{latD, lonD};
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return null;
    }

    private static double[] parseCoordPair(String s) {
        if (s == null) return null;
        int commaIdx = s.indexOf(',');
        if (commaIdx <= 0 || commaIdx >= s.length() - 1) return null;
        try {
            double lat = Double.parseDouble(s.substring(0, commaIdx).trim());
            double lon = Double.parseDouble(s.substring(commaIdx + 1).trim());
            if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
                return new double[]{lat, lon};
            }
        } catch (NumberFormatException e) {
            // not coordinates
        }
        return null;
    }

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
                return null;
        }
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
        if (query.isEmpty()) return null;

        if (!isUsableName(query)) return null;

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

        // Handle "lat,lng(Label)" format — extract the label
        int parenStart = qParam.indexOf('(');
        if (parenStart > 0 && qParam.endsWith(")")) {
            String label = qParam.substring(parenStart + 1, qParam.length() - 1).trim();
            if (!label.isEmpty()) {
                return new Destination(IntentType.GEO_VIEW, label);
            }
        }

        if (!isUsableName(qParam)) return null;

        return new Destination(IntentType.GEO_VIEW, qParam);
    }

    /**
     * Various Google Maps URL formats.
     */
    private static Destination parseHttpUrl(Uri uri) {
        String host = uri.getHost();
        if (host == null) return null;
        if (!host.contains("google.com") && !host.contains("google.cn")) return null;

        String path = uri.getPath();

        // Direction URLs with human-readable names
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
