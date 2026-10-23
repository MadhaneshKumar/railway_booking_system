package com.railway.auth;

import com.railway.service.BookingException;
import java.util.Optional;

/** The signed-in user of the request being handled. Set by AuthFilter and always cleared afterwards. */
public final class CurrentUser {
    private static final ThreadLocal<String> ID = new ThreadLocal<>();
    private CurrentUser() {}

    static void set(String userId) { ID.set(userId); }
    static void clear() { ID.remove(); }

    public static Optional<String> get() { return Optional.ofNullable(ID.get()); }

    /** The signed-in user id, or an UNAUTHENTICATED error (a SOAP fault / HTTP 401). */
    public static String require() {
        String id = ID.get();
        if (id == null) throw new BookingException("UNAUTHENTICATED", "Please sign in to continue");
        return id;
    }
}
