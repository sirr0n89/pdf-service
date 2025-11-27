# 1. Build-Stage: Maven Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN ./mvnw -DskipTests package

# 2. Runtime Stage: nur JAR ausliefern
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
