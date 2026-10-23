package com.railway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class AuthConfig {
    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    /**
     * AUTH_TOKEN_SECRET (32+ characters) signs the session tokens. Without it a random key is used, which is fine for
     * local development but signs everyone out on each restart. With the "prod" profile it is mandatory.
     */
    @Bean
    public TokenService tokenService(Environment env,
                                     @Value("${auth.token-secret:}") String secret,
                                     @Value("${auth.token-ttl-minutes:480}") long ttlMinutes) {
        byte[] key;
        if (secret.isBlank()) {
            if (env.acceptsProfiles(Profiles.of("prod")))
                throw new IllegalStateException("AUTH_TOKEN_SECRET must be set (32+ characters) when the 'prod' profile is active");
            key = new byte[32];
            new SecureRandom().nextBytes(key);
            log.warn("AUTH_TOKEN_SECRET is not set: using a random key. Everyone is signed out whenever the app restarts.");
        } else {
            key = secret.getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) throw new IllegalStateException("AUTH_TOKEN_SECRET must be at least 32 characters");
        }
        return new TokenService(key, Duration.ofMinutes(ttlMinutes), Clock.systemUTC());
    }

    @Bean
    public PasswordHasher passwordHasher() { return new PasswordHasher(); }
}
