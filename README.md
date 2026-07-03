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

---

## Arquitectura interna

---

## Estructura del proyecto

---

## Configuracion local

---

## Protocolo WebSocket

---

## Pruebas y calidad

---

## CI/CD

