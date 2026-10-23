package com.railway.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/** One place that decides what a valid user id looks like (lower-case letters, digits, underscore; 3-30 characters). */
public final class Usernames {
    private static final Pattern VALID = Pattern.compile("^[a-z0-9_]{3,30}$");
    private Usernames() {}

    public static String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    public static boolean isValid(String s) { return s != null && VALID.matcher(s).matches(); }
}
