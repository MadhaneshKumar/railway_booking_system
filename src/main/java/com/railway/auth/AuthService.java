package com.railway.auth;

import com.railway.document.UserDocument;
import com.railway.repository.Repos.UserRepo;
import com.railway.service.BookingException;
import com.railway.service.RateLimiterService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Account creation and password sign-in. Throws BookingException with a stable code; controllers map codes to HTTP status. */
@Service
public class AuthService {
    public record Session(String token, String userId, String name) {}

    private final UserRepo users; private final PasswordHasher hasher; private final TokenService tokens;
    private final RateLimiterService limiter;
    /** Verified against when the user does not exist, so "unknown user" and "wrong password" take the same time. */
    private final String dummyHash;

    public AuthService(UserRepo users, PasswordHasher hasher, TokenService tokens, RateLimiterService limiter) {
        this.users = users; this.hasher = hasher; this.tokens = tokens; this.limiter = limiter;
        this.dummyHash = hasher.hash("not-a-real-password");
    }

    public Session register(String rawUserId, String password, String name, String email, String phone, String clientKey) {
        if (!limiter.allow("register:" + clientKey)) throw new BookingException("RATE_LIMITED", "Too many attempts. Try again in a minute.");
        String userId = Usernames.normalize(rawUserId);
        if (!Usernames.isValid(userId))
            throw new BookingException("INVALID_INPUT", "User ID must be 3-30 characters: letters, digits or underscore");
        if (password == null || password.length() < 8 || password.length() > 128)
            throw new BookingException("INVALID_INPUT", "Password must be 8 to 128 characters");
        String cleanEmail = email == null ? "" : email.trim();
        if (!cleanEmail.isEmpty() && (cleanEmail.length() > 254 || !cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")))
            throw new BookingException("INVALID_INPUT", "Email address looks wrong");
        String cleanPhone = phone == null ? "" : phone.trim();
        if (!cleanPhone.isEmpty() && !cleanPhone.matches("^\\+?[0-9 ]{7,20}$"))
            throw new BookingException("INVALID_INPUT", "Phone number looks wrong");
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() > 80) throw new BookingException("INVALID_INPUT", "Name is too long");

        if (users.findByUserId(userId).isPresent()) throw taken();
        UserDocument u = new UserDocument();
        u.userId = userId; u.name = cleanName.isEmpty() ? userId : cleanName;
        u.email = cleanEmail.isEmpty() ? null : cleanEmail; u.phone = cleanPhone.isEmpty() ? null : cleanPhone;
        u.passwordHash = hasher.hash(password);
        try { users.save(u); }
        catch (DuplicateKeyException e) { throw taken(); }      // two people registering the same id at once
        return new Session(tokens.issue(userId), userId, u.name);
    }

    public Session login(String rawUserId, String password, String clientKey) {
        String userId = Usernames.normalize(rawUserId);
        // Throttle per account and per client, so one attacker cannot guess at many accounts or one account from many places.
        if (!limiter.allow("login:" + userId) || !limiter.allow("loginip:" + clientKey))
            throw new BookingException("RATE_LIMITED", "Too many attempts. Try again in a minute.");
        UserDocument u = Usernames.isValid(userId) ? users.findByUserId(userId).orElse(null) : null;
        boolean ok = hasher.verify(password == null ? "" : password, u != null && u.passwordHash != null ? u.passwordHash : dummyHash);
        if (u == null || u.passwordHash == null || !ok)
            throw new BookingException("INVALID_CREDENTIALS", "Wrong user ID or password");
        return new Session(tokens.issue(userId), userId, u.name);
    }

    public UserDocument me() {
        String id = CurrentUser.require();
        return users.findByUserId(id).orElseThrow(() -> new BookingException("UNAUTHENTICATED", "Please sign in to continue"));
    }

    private static BookingException taken() { return new BookingException("USERNAME_TAKEN", "That user ID is already taken"); }
}
