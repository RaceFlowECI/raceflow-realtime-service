package edu.eci.arsw.raceflow.realtime.config;

import edu.eci.arsw.raceflow.realtime.websocket.RoomWebSocketHandler;
import edu.eci.arsw.raceflow.realtime.websocket.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registra el endpoint de WebSocket de la sala y su interceptor de autenticación. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    /**
     * @param roomWebSocketHandler      maneja los mensajes de cada conexión de sala
     * @param webSocketAuthInterceptor  valida el JWT en el handshake
     */
    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler,
                            WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    /**
     * @param registry el registro de manejadores WebSocket de Spring
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/room/{roomCode}")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins("*");
    }
}
