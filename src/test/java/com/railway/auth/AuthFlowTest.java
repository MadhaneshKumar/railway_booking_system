package com.railway.auth;

import com.railway.document.BookingDocument;
import com.railway.repository.Repos.BookingRepo;
import com.railway.service.BookingException;
import com.railway.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Needs MongoDB and Redis running, like OverbookingTest. */
@SpringBootTest
class AuthFlowTest {
    @Autowired AuthService auth;
    @Autowired AccessGuard guard;
    @Autowired TokenService tokens;
    @Autowired BookingRepo bookings;
    @MockBean PaymentService payments;

    @AfterEach void signOut() { CurrentUser.clear(); }

    private static String uid() { return "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private static String code(Runnable r) {
        try { r.run(); return "NO_ERROR"; } catch (BookingException e) { return e.code; }
    }

    @Test void registerThenLoginWorksAndTokenNamesTheUser() {
        String u = uid();
        var s = auth.register(u.toUpperCase(), "a-good-password", "Test User", "t@example.com", "+911234567890", "ip-" + u);
        assertEquals(u, s.userId());
        assertEquals(java.util.Optional.of(u), tokens.verify(s.token()));
        var l = auth.login(u, "a-good-password", "ip-" + u);
        assertEquals(java.util.Optional.of(u), tokens.verify(l.token()));
    }

    @Test void wrongPasswordAndUnknownUserGiveTheSameError() {
        String u = uid();
        auth.register(u, "a-good-password", null, null, null, "ip-" + u);
        assertEquals("INVALID_CREDENTIALS", code(() -> auth.login(u, "wrong-password", "ip-" + u)));
        assertEquals("INVALID_CREDENTIALS", code(() -> auth.login(uid(), "a-good-password", "ip-" + u)));
    }

    @Test void duplicateAndInvalidRegistrationsAreRefused() {
        String u = uid();
        auth.register(u, "a-good-password", null, null, null, "ip-" + u);
        assertEquals("USERNAME_TAKEN", code(() -> auth.register(u, "another-password", null, null, null, "ip2-" + u)));
        assertEquals("INVALID_INPUT", code(() -> auth.register("ab", "a-good-password", null, null, null, "ip3-" + u)));
        assertEquals("INVALID_INPUT", code(() -> auth.register(uid(), "short", null, null, null, "ip4-" + u)));
    }

    @Test void legacyAccountWithoutPasswordCannotSignIn() {
        // user1 is the old demo account seeded before login existed
        assertEquals("INVALID_CREDENTIALS", code(() -> auth.login("user1", "", "ip-legacy")));
        assertEquals("INVALID_CREDENTIALS", code(() -> auth.login("user1", "password1", "ip-legacy")));
    }

    @Test void onlyTheOwnerCanReachABooking() {
        String owner = uid(), other = uid();
        BookingDocument b = new BookingDocument();
        b.pnr = String.valueOf(2_000_000_000L + (long) (Math.random() * 999_999_999L)); b.userId = owner;
        b.bookingStatus = "CONFIRMED"; b.trainNumber = "12607"; b.travelDate = "2030-01-01"; b.travelClass = "SL";
        bookings.save(b);
        try {
            assertEquals("UNAUTHENTICATED", code(() -> guard.booking(b.pnr)));         // nobody signed in
            CurrentUser.set(other);
            assertEquals("PNR_NOT_FOUND", code(() -> guard.booking(b.pnr)));           // somebody else
            CurrentUser.set(owner);
            assertEquals(b.pnr, guard.booking(b.pnr).pnr);                              // the owner
        } finally { bookings.delete(b); }
    }

    @Test void outsiderSeesTheSameErrorAsForAMissingBooking() {
        CurrentUser.set(uid());
        BookingException missing = assertThrows(BookingException.class, () -> guard.booking("1999999999"));
        assertEquals("PNR_NOT_FOUND", missing.code);
    }
}
