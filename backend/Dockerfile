# ==============================================================================
# Stage 1: Build Spring Boot Application (Java 21 + Maven)
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Runtime Image (Eclipse Temurin 21 JRE, TZ=Asia/Ho_Chi_Minh)
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime && \
    echo "Asia/Ho_Chi_Minh" > /etc/timezone

ENV TZ=Asia/Ho_Chi_Minh
ENV JAVA_OPTS="-Duser.timezone=Asia/Ho_Chi_Minh -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
