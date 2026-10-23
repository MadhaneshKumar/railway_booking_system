package com.railway.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Signed session tokens: base64url(payload) + "." + base64url(HMAC-SHA256(payload)).
 * The payload is "v1|userId|expiresAtEpochSeconds". Nothing is stored on the server; a token is valid if the
 * signature matches and it has not expired. (That also means a token cannot be revoked before it expires.)
 */
public final class TokenService {
    private static final String VERSION = "v1";
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration lifetime;
    private final Clock clock;

    public TokenService(byte[] secret, Duration lifetime, Clock clock) {
        if (secret == null || secret.length < 32) throw new IllegalArgumentException("The token secret must be at least 32 bytes");
        this.secret = secret.clone();
        this.lifetime = lifetime;
        this.clock = clock;
    }

    public String issue(String userId) {
        if (!Usernames.isValid(userId)) throw new IllegalArgumentException("Invalid user id");
        long expires = clock.instant().plus(lifetime).getEpochSecond();
        byte[] payload = (VERSION + "|" + userId + "|" + expires).getBytes(StandardCharsets.UTF_8);
        return ENC.encodeToString(payload) + "." + ENC.encodeToString(hmac(payload));
    }

    /** The user id inside a genuine, unexpired token; empty for anything else. Never throws on bad input. */
    public Optional<String> verify(String token) {
        if (token == null || token.length() > 512) return Optional.empty();
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) return Optional.empty();
        try {
            byte[] payload = DEC.decode(token.substring(0, dot));
            byte[] signature = DEC.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(hmac(payload), signature)) return Optional.empty();   // constant-time comparison
            String[] f = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
            if (f.length != 3 || !VERSION.equals(f[0]) || !Usernames.isValid(f[1])) return Optional.empty();
            long expires = Long.parseLong(f[2]);
            if (clock.instant().getEpochSecond() >= expires) return Optional.empty();
            return Optional.of(f[1]);
        } catch (IllegalArgumentException e) {      // bad Base64 or a bad number
            return Optional.empty();
        }
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available on this JVM", e);
        }
    }
}
