package com.example.amapredirect;

import android.net.Uri;
import android.text.TextUtils;

/**
 * Parsed destination info from a Google Maps intent.
 */
public class IntentParser {

    public enum IntentType {
        NAVIGATION,  // Turn-by-turn navigation request
        GEO_VIEW     // Viewing a place / search
    }

    public static class Destination {
        public final IntentType type;
        public final String name;      // Place name or address (may be null)
        public final String lat;       // Latitude string (may be null)
        public final String lon;       // Longitude string (may be null)

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
    }

    /**
     * Parse a destination from an intent URI. Returns null if the URI
     * is not a recognized Google Maps destination format.
     */
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
     * google.navigation:q=39.9,116.4
     */
    private static Destination parseNavigation(Uri uri) {
        // The URI looks like: google.navigation:q=...&mode=...
        // Android parses "google.navigation" as scheme, rest as scheme-specific part
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) return null;

        // Parse the query parameters from scheme-specific part
        // Format: q=value&mode=d
        String query = null;
        if (ssp.startsWith("q=")) {
            query = ssp.substring(2);
        } else if (ssp.contains("&q=")) {
            int idx = ssp.indexOf("&q=") + 3;
            query = ssp.substring(idx);
        } else {
            // Try as standard URI query param
            query = uri.getQueryParameter("q");
        }

        if (query == null) return null;

        // Trim off any trailing params
        int ampIdx = query.indexOf('&');
        if (ampIdx > 0) {
            query = query.substring(0, ampIdx);
        }

        query = Uri.decode(query);

        // Check if it's coordinates (lat,lng) or a name
        String[] coords = tryParseCoords(query);
        if (coords != null) {
            return new Destination(IntentType.NAVIGATION, null, coords[0], coords[1]);
        }

        return new Destination(IntentType.NAVIGATION, query, null, null);
    }

    /**
     * geo:lat,lng
     * geo:lat,lng?q=label
     * geo:0,0?q=search+term
     */
    private static Destination parseGeo(Uri uri) {
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) return null;

        // Remove query part from ssp
        String coordPart = ssp;
        int qMark = coordPart.indexOf('?');
        if (qMark > 0) {
            coordPart = coordPart.substring(0, qMark);
        }

        // Get the q parameter (label or search term)
        String qParam = uri.getQueryParameter("q");
        if (qParam != null) {
            qParam = qParam.trim();
        }

        // Parse coordinates from the geo:lat,lng part
        String[] coords = tryParseCoords(coordPart);
        boolean hasRealCoords = coords != null
                && !(coords[0].equals("0") && coords[1].equals("0"))
                && !(coords[0].equals("0.0") && coords[1].equals("0.0"));

        // If we have a q param, prefer it as the name
        if (!TextUtils.isEmpty(qParam)) {
            // Check if q param itself is coordinates
            String[] qCoords = tryParseCoords(qParam);
            if (qCoords != null) {
                return new Destination(IntentType.GEO_VIEW, null, qCoords[0], qCoords[1]);
            }
            // Use q as name, and include coords if available
            if (hasRealCoords) {
                return new Destination(IntentType.GEO_VIEW, qParam, coords[0], coords[1]);
            }
            return new Destination(IntentType.GEO_VIEW, qParam, null, null);
        }

        // No q param, use coordinates
        if (hasRealCoords) {
            return new Destination(IntentType.GEO_VIEW, null, coords[0], coords[1]);
        }

        return null;
    }

    /**
     * https://maps.google.com/maps?daddr=...
     * https://www.google.com/maps/dir/?api=1&destination=...
     * https://maps.google.com/maps?q=...
     * https://www.google.com/maps/search/...
     * https://www.google.com/maps/place/...
     */
    private static Destination parseHttpUrl(Uri uri) {
        String host = uri.getHost();
        if (host == null) return null;
        if (!host.contains("google.com") && !host.contains("google.cn")) {
            return null;
        }

        String path = uri.getPath();

        // Check for direction/navigation URLs
        String destination = uri.getQueryParameter("destination");
        if (destination != null) {
            return parseDestString(destination, IntentType.NAVIGATION);
        }

        String daddr = uri.getQueryParameter("daddr");
        if (daddr != null) {
            return parseDestString(daddr, IntentType.NAVIGATION);
        }

        // Check for /maps/dir/origin/destination format
        if (path != null && path.contains("/dir/")) {
            String[] segments = path.split("/");
            // Find "dir" segment and take the last segment as destination
            for (int i = 0; i < segments.length; i++) {
                if ("dir".equals(segments[i]) && i + 2 < segments.length) {
                    String dest = Uri.decode(segments[segments.length - 1]);
                    if (!dest.isEmpty()) {
                        return parseDestString(dest, IntentType.NAVIGATION);
                    }
                }
            }
        }

        // Check for place/search URLs
        if (path != null && (path.contains("/place/") || path.contains("/search/"))) {
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length; i++) {
                if (("place".equals(segments[i]) || "search".equals(segments[i]))
                        && i + 1 < segments.length) {
                    String name = Uri.decode(segments[i + 1]);
                    if (!name.isEmpty() && !name.startsWith("@")) {
                        return new Destination(IntentType.GEO_VIEW, name, null, null);
                    }
                }
            }
        }

        // Generic q param
        String q = uri.getQueryParameter("q");
        if (q != null) {
            return parseDestString(q, IntentType.GEO_VIEW);
        }

        return null;
    }

    private static Destination parseDestString(String s, IntentType type) {
        s = s.trim();
        String[] coords = tryParseCoords(s);
        if (coords != null) {
            return new Destination(type, null, coords[0], coords[1]);
        }
        return new Destination(type, s, null, null);
    }

    /**
     * Try to parse "lat,lng" from a string. Returns [lat, lng] or null.
     */
    private static String[] tryParseCoords(String s) {
        if (s == null) return null;
        s = s.trim();
        int commaIdx = s.indexOf(',');
        if (commaIdx <= 0 || commaIdx >= s.length() - 1) return null;

        String latStr = s.substring(0, commaIdx).trim();
        String lonStr = s.substring(commaIdx + 1).trim();

        try {
            double lat = Double.parseDouble(latStr);
            double lon = Double.parseDouble(lonStr);
            if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
                return new String[]{latStr, lonStr};
            }
        } catch (NumberFormatException e) {
            // Not coordinates
        }
        return null;
    }
}
