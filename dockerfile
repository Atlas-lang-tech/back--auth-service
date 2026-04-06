# =========================
# 1. Build stage
# =========================
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests -Dquarkus.package.jar.type=uber-jar

# =========================
# 2. Runtime stage
# =========================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /work/
COPY --from=build /app/target/*-runner.jar app.jar
EXPOSE 8080 9000
ENTRYPOINT ["java", "-jar", "/work/app.jar"]
