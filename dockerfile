# =========================
# 1. Stage: Build
# =========================
FROM quay.io/quarkus/ubi-quarkus-maven:3.41.1-java17 AS build
WORKDIR /app

# Копируем pom.xml и зависимости
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B

# Копируем весь код
COPY src src

# Сборка в jar
RUN ./mvnw package -DskipTests

# =========================
# 2. Stage: Runtime
# =========================
FROM quay.io/quarkus/ubi-quarkus-jvm:3.3.2-java17

WORKDIR /work/

# Копируем jar из build stage
COPY --from=build /app/target/*-runner.jar app.jar

# Для gRPC нужен открытый порт
EXPOSE 8080 9000

# Опционально: добавляем папку для ключей
VOLUME ["/etc/keys"]

# Запуск приложения
CMD ["java", "-jar", "app.jar"]
