package edu.eci.arsw.raceflow.realtime.websocket;

import edu.eci.arsw.raceflow.realtime.service.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Authenticates WebSocket connections at handshake time by validating a JWT
 * passed as a {@code ?token=} query parameter -- this is why WS auth does not
 * go through the main Spring Security filter chain (see {@code SecurityConfig}).
 * The resolved email is stashed in the session attributes for the handler to use.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public WebSocketAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Rejects the handshake with 401 if the {@code token} query parameter is
     * missing or invalid; otherwise stores the token's email under the
     * {@code "email"} session attribute.
     *
     * @return true para permitir que el handshake continúe, false para rechazarlo
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        List<String> tokenParams = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");

        String token = (tokenParams == null || tokenParams.isEmpty()) ? null : tokenParams.get(0);

        if (token == null || !jwtService.isTokenValid(token)) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("email", jwtService.extractEmail(token));
        return true;
    }

    /** No-op: no hay nada que hacer después de un handshake exitoso o fallido. */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
