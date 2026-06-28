# raceflow-realtime-service
Microservicio de tiempo real de RaceFlow. Recibe posiciones GPS por WebSocket, recalcula el ranking y lo difunde a todos los participantes de la sala.

**Stack:** Java 21 · Spring Boot · spring-websocket · Redis · RabbitMQ (productor)

**Componentes clave:**
- RoomWebSocketHandler — gestiona conexiones WebSocket por sala
- PositionIngestor — valida posiciones GPS entrantes
- RankingService — recalcula el ranking
- RankingStrategy — interfaz (patrón Strategy, intercambiable por deporte)
- RoomStateClient — operaciones atómicas en Redis
- EventPublisher — publica eventos en RabbitMQ

**Escala:** ×2–M (cuello de botella del tiempo real)
