package com.railway.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher h = new PasswordHasher(2_000);   // fast for tests; production uses 600,000

    @Test void correctPasswordVerifies() { assertTrue(h.verify("correct horse", h.hash("correct horse"))); }
    @Test void wrongPasswordFails() { assertFalse(h.verify("correct horsf", h.hash("correct horse"))); }
    @Test void sameCharactersGetDifferentSalts() { assertNotEquals(h.hash("pw-pw-pw-pw"), h.hash("pw-pw-pw-pw")); }
    @Test void hashDoesNotContainThePassword() { assertFalse(h.hash("secretpass1").contains("secretpass1")); }

    @Test void malformedStoredValuesFailClosedWithoutThrowing() {
        for (String s : new String[]{null, "", "plain", "pbkdf2-sha256$x$y$z", "pbkdf2-sha256$1$aa$bb",
                "pbkdf2-sha256$999999999$aa$bb", "md5$2000$aa$bb"})
            assertFalse(h.verify("anything", s), String.valueOf(s));
    }

    @Test void unicodeAndLongPasswordsWork() {
        String p = "pässwörd-密码-" + "x".repeat(100);
        assertTrue(h.verify(p, h.hash(p)));
    }
}
