package edu.eci.arsw.raceflow.realtime.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifies JWTs issued by auth-service. Used both by the WebSocket handshake
 * interceptor and the REST controllers to authenticate requests, since
 * realtime-service trusts auth-service's tokens rather than issuing its own.
 */
@Service
public class JwtService {

    private final SecretKey key;

    /**
     * @param secret the shared HMAC signing secret, same value configured in auth-service
     */
    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param token a valid JWT
     * @return the email stored in the token's subject claim
     */
    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * @param token the JWT to validate
     * @return true if the token's signature and expiration are valid
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
