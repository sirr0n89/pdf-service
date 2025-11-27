# === 1) Build Stage ===
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Wrapper + Maven Dateien
COPY mvnw .
COPY .mvn .mvn

# Projektdateien
COPY pom.xml .
COPY src ./src

# Ausführbar machen
RUN chmod +x mvnw

# Maven Build
RUN ./mvnw -DskipTests package

# === 2) Runtime Stage ===
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
