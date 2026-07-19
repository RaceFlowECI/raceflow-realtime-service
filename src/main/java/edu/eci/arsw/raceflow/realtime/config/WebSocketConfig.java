package edu.eci.arsw.raceflow.realtime.config;

import edu.eci.arsw.raceflow.realtime.websocket.RoomWebSocketHandler;
import edu.eci.arsw.raceflow.realtime.websocket.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the room WebSocket endpoint and its authentication interceptor. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    /**
     * @param roomWebSocketHandler      handles messages for each room connection
     * @param webSocketAuthInterceptor  validates the JWT at the handshake
     */
    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler,
                            WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    /**
     * @param registry Spring's WebSocket handler registry
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/room/{roomCode}")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins("*");
    }
}
