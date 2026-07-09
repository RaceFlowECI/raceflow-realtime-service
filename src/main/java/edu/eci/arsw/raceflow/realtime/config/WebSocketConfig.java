package edu.eci.arsw.raceflow.realtime.config;

import edu.eci.arsw.raceflow.realtime.websocket.RoomWebSocketHandler;
import edu.eci.arsw.raceflow.realtime.websocket.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler,
                            WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/ws/room/{roomCode}")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins("*");
    }
}
