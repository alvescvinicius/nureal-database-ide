package com.nureal.ide.core.connection;

public final class JdbcUrlNormalizer {

    private JdbcUrlNormalizer() {
    }

    public static String normalize(String url) {

        if (url == null || url.isBlank()) {
            return url;
        }

        if (!url.startsWith("jdbc:mysql:")) {
            return url;
        }

        String normalized = url;

        normalized = appendIfMissing(
                normalized,
                "connectionTimeZone",
                "LOCAL");

        normalized = appendIfMissing(
                normalized,
                "forceConnectionTimeZoneToSession",
                "true");

        return normalized;
    }

    private static String appendIfMissing(
            String url,
            String key,
            String value) {

        if (url.matches(".*([?&])" + key + "=.*")) {
            return url;
        }

        return url +
                (url.contains("?") ? "&" : "?") +
                key +
                "=" +
                value;
    }

}