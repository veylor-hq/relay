FROM eclipse-temurin:26-jre-alpine

WORKDIR /app

# Copy the built jar file
COPY build/libs/*.jar app.jar

# Download the Sentry OpenTelemetry agent dynamically from Maven Central
ADD https://repo1.maven.org/maven2/io/sentry/sentry-opentelemetry-agent/8.49.0/sentry-opentelemetry-agent-8.49.0.jar sentry-agent.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run with Sentry OpenTelemetry agent
ENTRYPOINT ["java", "-javaagent:sentry-agent.jar", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
