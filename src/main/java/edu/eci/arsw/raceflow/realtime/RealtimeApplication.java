package edu.eci.arsw.raceflow.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Realtime Service. Handles live GPS positions and
 * ranking over WebSocket, room lifecycle, room invitations, and the
 * signaling for the WebRTC voice chat.
 */
@SpringBootApplication
public class RealtimeApplication {

    /**
     * Boots the Spring application context.
     *
     * @param args argumentos de línea de comandos pasados a Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(RealtimeApplication.class, args);
    }
}
