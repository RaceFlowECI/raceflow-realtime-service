# RACEFLOW — Realtime Service

> [!IMPORTANT]
> Este repositorio contiene el **Realtime Service** de RaceFlow: motor de tiempo real con WebSocket y Strategy pattern.

> Para informacion general consulta el [perfil de la organizacion](https://github.com/RaceFlowECI).

---

## Tabla de contenido
- [Descripcion general](#descripcion-general)
- [Stack tecnologico](#stack-tecnologico)
- [Arquitectura interna](#arquitectura-interna)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Configuracion local](#configuracion-local)
- [Protocolo WebSocket](#protocolo-websocket)
- [Pruebas y calidad](#pruebas-y-calidad)
- [CI/CD](#cicd)
- [Observabilidad](#observabilidad)

---

## Descripcion general

> [!NOTE]
> Motor de tiempo real de RaceFlow. Mantiene conexiones WebSocket con los participantes, consume los eventos de ubicacion de RabbitMQ, calcula la posicion relativa usando el patron Strategy segun el deporte y sincroniza el estado de la carrera en Redis.

### Responsabilidades principales

| Responsabilidad | Descripcion |
|---|---|
| **WebSocket** | Mantiene conexiones persistentes con cada participante. |
| **Posicionamiento** | Calcula posiciones relativas via Strategy (Running, Cycling, Swimming). |
| **Estado en Redis** | Guarda el estado de cada sala para reconexiones rapidas. |
| **Eventos** | Consume eventos de RabbitMQ publicados por Room Service. |

---

## Stack tecnologico

### Backend
![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-010101?style=for-the-badge&logo=socketdotio&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

### Testing y calidad
![JUnit](https://img.shields.io/badge/JUnit_5-25A162?style=for-the-badge&logo=java&logoColor=white)
![JaCoCo](https://img.shields.io/badge/JaCoCo-BB0A30?style=for-the-badge)
![SonarQube](https://img.shields.io/badge/SonarQube-4E9BCD?style=for-the-badge&logo=sonarqube&logoColor=white)

### DevOps
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)

---

## Arquitectura interna

El servicio aplica el patron **Strategy** para desacoplar el algoritmo de calculo de posicion segun el deporte:

```
SportStrategy (interface)
├── RunningStrategy   → distancia GPS en metros
├── CyclingStrategy   → distancia GPS + factor de altimetria
└── SwimmingStrategy  → distancia en metros lineales en piscina
```

El `PositionCalculator` recibe la estrategia en tiempo de ejecucion segun el campo `sport` de la sala, sin necesidad de condiciones en el dominio principal.

---

## Estructura del proyecto

```text
raceflow-realtime-service/
├── .github/workflows/
├── .env.example
├── .gitignore
├── Dockerfile
├── pom.xml
└── src/main/java/edu/eci/arsw/raceflow/realtime/
    ├── RealtimeApplication.java
    ├── config/
    │   ├── WebSocketConfig.java
    │   └── RedisConfig.java
    ├── handler/
    │   └── RaceWebSocketHandler.java
    ├── strategy/
    │   ├── SportStrategy.java
    │   ├── RunningStrategy.java
    │   ├── CyclingStrategy.java
    │   └── SwimmingStrategy.java
    ├── calculator/
    │   └── PositionCalculator.java
    └── listener/
        └── RoomEventListener.java
```

---

## Configuracion local

### 1. Clonar el repositorio
```bash
git clone https://github.com/RaceFlowECI/raceflow-realtime-service.git
cd raceflow-realtime-service
```

### 2. Compilar
```bash
mvn clean install
```

### 3. Configurar variables de entorno
```bash
cp .env.example .env
```
```env
REDIS_HOST=localhost
RABBITMQ_HOST=localhost
```

### 4. Ejecutar
```bash
mvn spring-boot:run
```
> [!TIP]
> El servicio arranca en `ws://localhost:8083`. Requiere Redis y RabbitMQ corriendo localmente.

---

## Protocolo WebSocket

### Conexion
```
ws://localhost:8083/ws/rooms/{roomCode}?token={jwt}
```

### Mensajes del cliente → servidor

| Tipo | Descripcion |
|---|---|
| `LOCATION` | Envia coordenadas GPS del participante. |
| `PING` | Keepalive de la conexion. |

### Mensajes del servidor → cliente

| Tipo | Descripcion |
|---|---|
| `RANKING` | Lista ordenada de participantes con su posicion actual. |
| `ROOM_STATE` | Cambio de estado de la sala (WAITING, ACTIVE, FINISHED). |

### Ejemplo de mensaje LOCATION
```json
{
  "type": "LOCATION",
  "payload": {
    "lat": 4.7110,
    "lng": -74.0721,
    "timestamp": 1720224000000
  }
}
```

---

## Pruebas y calidad
```bash
mvn test
mvn clean test jacoco:report
```

---

## CI/CD

| Campo | Valor |
|---|---|
| Puerto | 8083 |
| Protocolo | WebSocket |
| Plataforma | _por definir_ |
| Ultima version | ![CI](https://github.com/RaceFlowECI/raceflow-realtime-service/actions/workflows/ci.yml/badge.svg) |

---

## Observabilidad

### Endpoint de métricas
```
GET http://localhost:8083/actuator/prometheus
```
También disponibles: `/actuator/health`, `/actuator/info`, `/actuator/metrics`.

### Métricas de negocio

| Métrica | Tipo | Descripcion |
|---|---|---|
| `raceflow_websocket_connections_active` | Gauge | Conexiones WebSocket activas |
| `raceflow_positions_received_total` | Counter | Posiciones GPS recibidas |
| `raceflow_positions_rejected_total{reason}` | Counter | Posiciones rechazadas (reason: invalid_jump / out_of_bounds / malformed) |
| `raceflow_ranking_updates_total` | Counter | Actualizaciones de ranking computadas |
| `raceflow_ranking_update_duration_seconds` | Timer | **SLO p99 ≤ 1s** — latencia de actualización de ranking |
| `raceflow_reactions_sent_total` | Counter | Reacciones enviadas a clientes |
| `raceflow_redis_write_duration_seconds` | Timer | Latencia de escritura en Redis |

> [!IMPORTANT]
> `raceflow_ranking_update_duration_seconds` registra percentiles p50, p95 y p99. Con múltiples réplicas, `raceflow_websocket_connections_active` debe consultarse con `sum()` para no contar doble.

### Verificación local
```bash
# Con el servicio corriendo:
curl -s http://localhost:8083/actuator/prometheus | grep raceflow_
```

> [!NOTE]
> Micrometer convierte puntos a guiones bajos: `raceflow.rooms.created` → `raceflow_rooms_created_total` en Prometheus.
