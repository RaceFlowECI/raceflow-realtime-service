FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Download OpenTelemetry Java Agent (no code changes needed)
FROM eclipse-temurin:21-jre-jammy AS otel-agent
ARG OTEL_VERSION=2.3.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=otel-agent /otel/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
EXPOSE 8083

# OTEL_SERVICE_NAME, OTEL_EXPORTER_OTLP_ENDPOINT etc. are injected at runtime via env vars
ENTRYPOINT ["java", \
  "-javaagent:opentelemetry-javaagent.jar", \
  "-jar", "app.jar"]
