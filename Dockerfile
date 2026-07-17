FROM eclipse-temurin:26-jre-alpine

WORKDIR /app

# Copy the built jar file
COPY build/libs/*.jar app.jar

# Copy the Sentry OpenTelemetry agent (if it exists)
COPY sentry-opentelemetry-agent-8.49.0.jar sentry-agent.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run with Sentry OpenTelemetry agent
ENTRYPOINT ["java", "-javaagent:sentry-agent.jar", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
