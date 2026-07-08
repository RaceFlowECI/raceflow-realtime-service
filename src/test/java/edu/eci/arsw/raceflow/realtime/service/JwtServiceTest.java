package edu.eci.arsw.raceflow.realtime.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "raceflow-dev-secret-key-for-local-dev-only-32chars";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
    }

    @Test
    void extractsEmailFromValidToken() {
        String token = signToken("juan@raceflow.dev", new Date(System.currentTimeMillis() + 60_000));

        assertThat(jwtService.extractEmail(token)).isEqualTo("juan@raceflow.dev");
    }

    @Test
    void isTokenValidReturnsTrueForFreshToken() {
        String token = signToken("juan@raceflow.dev", new Date(System.currentTimeMillis() + 60_000));

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValidReturnsFalseForExpiredToken() {
        String token = signToken("juan@raceflow.dev", new Date(System.currentTimeMillis() - 60_000));

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValidReturnsFalseForMalformedToken() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void isTokenValidReturnsFalseForTokenSignedWithDifferentSecret() {
        SecretKey otherKey = Keys.hmacShaKeyFor("a-completely-different-secret-key-32ch".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("juan@raceflow.dev")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    private String signToken(String subject, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
