package com.sentri.access_control.utils;

import android.text.format.DateFormat;
import android.util.Log;

import com.google.firebase.Timestamp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Centralized date parsing and formatting utility.
 * Handles Timestamp, Date, Long, Double, Map, and String formats.
 */
public final class DateUtils {
    private static final String TAG = "DateUtils";

    private DateUtils() {} // Prevent instantiation

    /**
     * Parses various date formats into a Date object.
     * Supports: Timestamp, Date, Long (millis), Double, Map (seconds/nanoseconds), String
     */
    public static Date parseFlexibleDate(Object raw) {
        if (raw == null) return null;
        try {
            if (raw instanceof Timestamp) {
                return ((Timestamp) raw).toDate();
            }
            if (raw instanceof Date) {
                return (Date) raw;
            }
            if (raw instanceof Long) {
                return new Date((Long) raw);
            }
            if (raw instanceof Double) {
                long millis = Math.round((Double) raw);
                return new Date(millis);
            }
            if (raw instanceof Map) {
                try {
                    Map<?, ?> m = (Map<?, ?>) raw;
                    Object sec = m.get("seconds");
                    Object nanos = m.get("nanoseconds");
                    if (sec instanceof Number) {
                        long s = ((Number) sec).longValue();
                        long ms = s * 1000L;
                        if (nanos instanceof Number) {
                            ms += Math.round(((Number) nanos).longValue() / 1_000_000.0);
                        }
                        return new Date(ms);
                    }
                } catch (Exception ignored) {}
            }
            
            String s = raw.toString().trim();
            if (s.isEmpty()) return null;

            // Common date patterns
            String[] patterns = new String[] {
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd",
                    "d MMM, yyyy",
                    "dd MMM, yyyy",
                    "d MMM yyyy",
                    "dd/MM/yyyy",
                    "MM/dd/yyyy",
                    "MMM d, yyyy",
                    "d-MMM-yyyy"
            };
            
            for (String p : patterns) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.getDefault());
                    if (p.contains("'Z'") || p.contains("XXX")) {
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    }
                    return sdf.parse(s);
                } catch (ParseException ignored) {}
            }

            // Try as millis
            try {
                long millis = Long.parseLong(s);
                return new Date(millis);
            } catch (NumberFormatException ignored) {}

            // Last resort
            long ms = Date.parse(s);
            return new Date(ms);
        } catch (Exception e) {
            Log.w(TAG, "parseFlexibleDate error for raw=" + raw + " : " + e.getMessage());
            return null;
        }
    }

    /**
     * Formats a date using the given pattern.
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) return "—";
        return DateFormat.format(pattern, date).toString();
    }

    /**
     * Formats date with default pattern: "d MMM, yyyy hh:mm a"
     */
    public static String formatDateTime(Date date) {
        return formatDate(date, "d MMM, yyyy hh:mm a");
    }

    /**
     * Formats date with default pattern: "d MMM, yyyy"
     */
    public static String formatDateOnly(Date date) {
        return formatDate(date, "d MMM, yyyy");
    }

    /**
     * Formats time with default pattern: "hh:mm a"
     */
    public static String formatTimeOnly(Date date) {
        return formatDate(date, "hh:mm a");
    }

    /**
     * Converts a Timestamp to formatted date string.
     */
    public static String formatTimestamp(Timestamp timestamp, String pattern) {
        if (timestamp == null) return "—";
        return formatDate(timestamp.toDate(), pattern);
    }
}
