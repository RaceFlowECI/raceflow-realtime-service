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

---

## Protocolo WebSocket

---

## Pruebas y calidad

---

## CI/CD

