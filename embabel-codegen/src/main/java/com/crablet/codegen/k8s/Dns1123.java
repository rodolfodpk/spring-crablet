package com.crablet.codegen.k8s;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sanitizes arbitrary strings to a DNS-1123 subdomain label (lowercase, hyphens, max 63 chars).
 */
public final class Dns1123 {

    private static final Pattern NOT_LABEL = Pattern.compile("[^a-z0-9]+");
    private static final int MAX = 63;

    private Dns1123() {
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "app";
        }
        String s = raw.toLowerCase(Locale.ROOT);
        s = NOT_LABEL.matcher(s).replaceAll("-");
        s = s.replaceAll("-{2,}", "-");
        s = trimHyphens(s);
        if (s.isEmpty()) {
            return "app";
        }
        if (s.length() > MAX) {
            s = s.substring(0, MAX);
            s = trimHyphens(s);
        }
        if (s.isEmpty()) {
            return "app";
        }
        return s;
    }

    private static String trimHyphens(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '-') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '-') {
            end--;
        }
        return s.substring(start, end);
    }
}
