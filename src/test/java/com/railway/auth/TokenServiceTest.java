package com.railway.auth;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static TokenService at(Instant now, byte[] key) {
        return new TokenService(key, Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void issuedTokenVerifiesToTheSameUser() {
        TokenService t = at(Instant.parse("2026-01-01T00:00:00Z"), KEY);
        assertEquals(Optional.of("alice_1"), t.verify(t.issue("alice_1")));
    }

    @Test void expiredTokenIsRejected() {
        String tok = at(Instant.parse("2026-01-01T00:00:00Z"), KEY).issue("alice");
        assertTrue(at(Instant.parse("2026-01-01T00:59:59Z"), KEY).verify(tok).isPresent());
        assertTrue(at(Instant.parse("2026-01-01T01:00:01Z"), KEY).verify(tok).isEmpty());
    }

    @Test void tokenSignedWithAnotherKeyIsRejected() {
        byte[] other = "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        assertTrue(at(now, KEY).verify(at(now, other).issue("alice")).isEmpty());
    }

    @Test void tamperedPayloadIsRejected() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TokenService t = at(now, KEY);
        String good = t.issue("alice");
        String evil = t.issue("bobby");
        // alice's signature on bobby's payload
        String forged = evil.substring(0, evil.indexOf('.')) + good.substring(good.indexOf('.'));
        assertTrue(t.verify(forged).isEmpty());
    }

    @Test void garbageNeverThrows() {
        TokenService t = at(Instant.parse("2026-01-01T00:00:00Z"), KEY);
        for (String s : new String[]{null, "", ".", "a.b", "...", "x".repeat(600), "v1|alice|9", "%%%.%%%"})
            assertTrue(t.verify(s).isEmpty(), String.valueOf(s));
    }

    @Test void shortSecretIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new TokenService(new byte[8], Duration.ofHours(1), Clock.systemUTC()));
    }
}
