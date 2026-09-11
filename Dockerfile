# Stage 1: Build JAR using Gradle in JDK 26
FROM eclipse-temurin:26-jdk-alpine AS builder

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY src/ src/

RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime image using JRE 26
FROM eclipse-temurin:26-jre-alpine

RUN apk add --no-cache tzdata

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# Download the Sentry OpenTelemetry agent dynamically from Maven Central
ADD https://repo1.maven.org/maven2/io/sentry/sentry-opentelemetry-agent/8.49.0/sentry-opentelemetry-agent-8.49.0.jar sentry-agent.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Restrict JVM RAM consumption to max 350 MB
ENV JAVA_TOOL_OPTIONS="-Xmx350m"

# Run with Sentry OpenTelemetry agent, explicit 350MB heap limit and Europe/London timezone
ENTRYPOINT ["java", "-Xmx350m", "-Duser.timezone=Europe/London", "-javaagent:sentry-agent.jar", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
