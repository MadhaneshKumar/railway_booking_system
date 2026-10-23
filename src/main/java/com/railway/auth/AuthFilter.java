package com.railway.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/**
 * Reads "Authorization: Bearer <token>" on every request (SOAP and REST). A valid token makes the user available
 * through CurrentUser. The filter never rejects a request itself: each operation decides whether it needs a user.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {
    private final TokenService tokens;

    public AuthFilter(TokenService tokens) { this.tokens = tokens; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            tokens.verify(header.substring(7).trim()).ifPresent(CurrentUser::set);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUser.clear();
        }
    }
}
