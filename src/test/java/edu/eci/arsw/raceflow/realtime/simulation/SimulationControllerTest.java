package edu.eci.arsw.raceflow.realtime.simulation;

import edu.eci.arsw.raceflow.realtime.metrics.RealtimeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationControllerTest {

    private SimulationController controller;

    @BeforeEach
    void setUp() {
        controller = new SimulationController(new RealtimeMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void tearDown() {
        controller.stop();
    }

    @Test
    void statusReportsNotRunningInitially() {
        ResponseEntity<Map<String, Object>> response = controller.status();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("running", false);
        assertThat(response.getBody()).containsEntry("sloThresholdSeconds", 1.0);
    }

    @Test
    void startSlowRankingAcceptsRequestAndMarksAsRunning() {
        ResponseEntity<Map<String, Object>> response = controller.startSlowRanking(10, 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("status", "started");
        assertThat(response.getBody()).containsEntry("delayMs", 10);
        assertThat(response.getBody()).containsEntry("count", 1);

        ResponseEntity<Map<String, Object>> status = controller.status();
        assertThat(status.getBody()).containsEntry("running", true);
    }

    @Test
    void startSlowRankingRejectsWhenAlreadyRunning() {
        controller.startSlowRanking(10, 1);

        ResponseEntity<Map<String, Object>> second = controller.startSlowRanking(10, 1);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody()).containsKey("error");
    }

    @Test
    void stopWhenRunningReturnsStoppedMessage() {
        controller.startSlowRanking(10, 1);

        ResponseEntity<Map<String, String>> response = controller.stop();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "stopped");
        assertThat(response.getBody().get("message")).isEqualTo("Simulación detenida.");
    }

    @Test
    void stopWhenNotRunningReturnsNoSimulationMessage() {
        ResponseEntity<Map<String, String>> response = controller.stop();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "stopped");
        assertThat(response.getBody().get("message")).isEqualTo("No había simulación en curso.");
    }
}
