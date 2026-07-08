package edu.eci.arsw.raceflow.realtime.websocket;

import edu.eci.arsw.raceflow.realtime.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler wsHandler;

    private WebSocketAuthInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        interceptor = new WebSocketAuthInterceptor(jwtService);
        attributes = new HashMap<>();
    }

    @Test
    void acceptsHandshakeWithValidTokenAndStoresEmail() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8083/ws/room/ABC123?token=valid.jwt.token"));
        when(jwtService.isTokenValid("valid.jwt.token")).thenReturn(true);
        when(jwtService.extractEmail("valid.jwt.token")).thenReturn("juan@raceflow.dev");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes).containsEntry("email", "juan@raceflow.dev");
    }

    @Test
    void rejectsHandshakeWithMissingToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8083/ws/room/ABC123"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsHandshakeWithInvalidToken() {
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8083/ws/room/ABC123?token=bad"));
        when(jwtService.isTokenValid("bad")).thenReturn(false);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void afterHandshakeIsNoOp() {
        interceptor.afterHandshake(request, response, wsHandler, null);
        // no interaction expected beyond method completing without throwing
    }
}
