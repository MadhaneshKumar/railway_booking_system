package com.railway.controller;

import com.railway.auth.AuthService;
import com.railway.auth.CurrentUser;
import com.railway.document.BookingDocument;
import com.railway.document.UserDocument;
import com.railway.repository.Repos.BookingRepo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AuthController {
    public record RegisterBody(String userId, String password, String name, String email, String phone) {}
    public record LoginBody(String userId, String password) {}
    public record SessionBody(String token, String userId, String name) {}
    public record MeBody(String userId, String name, String email, String phone) {}
    public record BookingRow(String pnr, String status, String trainNumber, String travelDate, String travelClass,
                             int passengers, double amount, String holdExpiresAt) {}

    private final AuthService auth; private final BookingRepo bookings;

    public AuthController(AuthService auth, BookingRepo bookings) { this.auth = auth; this.bookings = bookings; }

    /** The caller's address is only used to throttle attempts. */
    private static String client(HttpServletRequest r) { return r.getRemoteAddr(); }

    @PostMapping("/auth/register")
    public SessionBody register(@RequestBody RegisterBody b, HttpServletRequest r) {
        var s = auth.register(b.userId(), b.password(), b.name(), b.email(), b.phone(), client(r));
        return new SessionBody(s.token(), s.userId(), s.name());
    }

    @PostMapping("/auth/login")
    public SessionBody login(@RequestBody LoginBody b, HttpServletRequest r) {
        var s = auth.login(b.userId(), b.password(), client(r));
        return new SessionBody(s.token(), s.userId(), s.name());
    }

    @GetMapping("/auth/me")
    public MeBody me() {
        UserDocument u = auth.me();
        return new MeBody(u.userId, u.name, u.email, u.phone);
    }

    /** The signed-in user's own bookings, newest first. */
    @GetMapping("/me/bookings")
    public List<BookingRow> myBookings() {
        String me = CurrentUser.require();
        return bookings.findTop50ByUserIdOrderByCreatedAtDesc(me).stream().map(AuthController::row).toList();
    }

    private static BookingRow row(BookingDocument b) {
        return new BookingRow(b.pnr, b.bookingStatus, b.trainNumber, b.travelDate, b.travelClass, b.passengers.size(), b.amount,
                "HELD".equals(b.bookingStatus) && b.expiresAt != null ? b.expiresAt.toString() : null);
    }
}
