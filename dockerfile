# =========================
# 1. Stage: Build
# =========================
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Кэш зависимостей
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Копируем исходники
COPY src src

# Сборка
RUN ./mvnw package -DskipTests

# =========================
# 2. Stage: Runtime
# =========================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /work/

# Копируем jar
COPY --from=build /app/target/*-runner.jar app.jar

# Порты (HTTP + gRPC)
EXPOSE 8080 9000

# Папка под ключи
VOLUME ["/etc/keys"]

# Запуск
ENTRYPOINT ["java","-jar","/work/app.jar"]
