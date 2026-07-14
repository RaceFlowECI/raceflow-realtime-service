package edu.eci.arsw.raceflow.realtime.simulation;

import edu.eci.arsw.raceflow.realtime.metrics.RealtimeMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Endpoints de simulación de incidente para el laboratorio de observabilidad.
 *
 * POST /api/simulate/slow-ranking?delayMs=1500&count=30
 *   Registra 'count' actualizaciones de ranking con delay artificial de 'delayMs' ms.
 *   Con 30 observaciones a 1500 ms el p99 superará el SLO de 1 s en ~3 minutos.
 *
 * POST /api/simulate/stop
 *   Cancela cualquier simulación en curso.
 *
 * GET  /api/simulate/status
 *   Estado actual de la simulación.
 */
@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);

    private final RealtimeMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "incident-sim");
        t.setDaemon(true);
        return t;
    });

    /** @param metrics usado para registrar los retrasos artificiales de actualización de ranking que inyecta este endpoint */
    public SimulationController(RealtimeMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Starts recording {@code count} ranking-update observations with an artificial
     * {@code delayMs} delay each, to deliberately breach the p99 &lt;= 1s SLO for
     * observability-lab purposes.
     *
     * @param delayMs retraso artificial por observación, en milisegundos
     * @param count   número de observaciones a registrar
     * @return 400 si ya hay una simulación en curso, de lo contrario 202 con los parámetros de la ejecución
     */
    @PostMapping("/slow-ranking")
    public ResponseEntity<Map<String, Object>> startSlowRanking(
            @RequestParam(defaultValue = "1500") int delayMs,
            @RequestParam(defaultValue = "30")  int count) {

        if (running.getAndSet(true)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Simulación ya en curso. POST /api/simulate/stop primero."));
        }

        log.warn("INCIDENT SIMULATION STARTED — delayMs={} count={}", delayMs, count);

        executor.submit(() -> {
            int recorded = 0;
            try {
                while (running.get() && recorded < count) {
                    final int obs = recorded + 1;
                    metrics.getRankingUpdateDuration().record(() -> {
                        try {
                            log.warn("SLOW RANKING UPDATE — simulando obs {} con delay {}ms", obs, delayMs);
                            Thread.sleep(delayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    recorded++;
                    // pequeña pausa entre observaciones para distribuirlas en la ventana de 5 min
                    Thread.sleep(3_000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.set(false);
                log.warn("INCIDENT SIMULATION ENDED — {} observaciones registradas", recorded);
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "status",  "started",
                "delayMs", delayMs,
                "count",   count,
                "estimatedDurationSeconds", (long) count * 3,
                "sloThresholdSeconds", 1.0,
                "message", "Monitorear en Grafana: panel 'Ranking latencia p99'. Alerta disparará en ~3 min."
        ));
    }

    /** Cancela cualquier simulación en curso. Es seguro llamarlo incluso si no hay ninguna en ejecución. */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stop() {
        boolean wasRunning = running.getAndSet(false);
        String msg = wasRunning ? "Simulación detenida." : "No había simulación en curso.";
        log.warn("INCIDENT SIMULATION: {}", msg);
        return ResponseEntity.ok(Map.of("status", "stopped", "message", msg));
    }

    /** @return si hay una simulación en curso, más la consulta PromQL usada para vigilar el SLO en Grafana */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "running", running.get(),
                "sloThresholdSeconds", 1.0,
                "rankingP99Query",
                "histogram_quantile(0.99, rate(raceflow_ranking_update_duration_seconds_bucket[5m]))"
        ));
    }
}
