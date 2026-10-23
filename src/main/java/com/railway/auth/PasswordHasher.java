package com.railway.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256, which ships with the JDK (no extra library).
 * Stored form: pbkdf2-sha256$iterations$salt$hash  (salt and hash are Base64).
 * The iteration count is stored with each hash, so it can be raised later without breaking old passwords.
 */
public final class PasswordHasher {
    /** OWASP's 2023 recommendation for PBKDF2-HMAC-SHA256. */
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String TAG = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16, KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int iterations;

    public PasswordHasher() { this(DEFAULT_ITERATIONS); }
    public PasswordHasher(int iterations) { this.iterations = iterations; }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return TAG + "$" + iterations + "$" + b64(salt) + "$" + b64(derive(password.toCharArray(), salt, iterations));
    }

    /** True only if `password` produces the stored hash. Malformed stored values simply return false. */
    public boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] p = stored.split("\\$", -1);
        if (p.length != 4 || !TAG.equals(p[0])) return false;
        try {
            int rounds = Integer.parseInt(p[1]);
            if (rounds < 1_000 || rounds > 5_000_000) return false;      // refuse absurd values from corrupted data
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            byte[] actual = derive(password.toCharArray(), salt, rounds);
            return MessageDigest.isEqual(expected, actual);               // constant-time comparison
        } catch (IllegalArgumentException e) {                             // bad number or bad Base64
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String b64(byte[] b) { return Base64.getEncoder().withoutPadding().encodeToString(b); }
}
