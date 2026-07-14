package edu.eci.arsw.raceflow.realtime.service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifica los JWT emitidos por auth-service. Lo usan tanto el interceptor de
 * handshake de WebSocket como los controladores REST para autenticar peticiones, ya que
 * realtime-service confía en los tokens de auth-service en vez de emitir los propios.
 */
@Service
public class JwtService {

    private final SecretKey key;

    /**
     * @param secret el secreto HMAC compartido para firmar, el mismo valor configurado en auth-service
     */
    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param token un JWT válido
     * @return el email almacenado en el claim subject del token
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
     * @param token el JWT a validar
     * @return true si la firma y la expiración del token son válidas
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
