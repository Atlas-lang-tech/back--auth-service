# 🔐 Quarkus Auth Service

Микросервис авторизации на **Quarkus** с JWT, PostgreSQL, Redis, Kafka и gRPC.

## 📋 Требования

Перед началом убедитесь, что установлены:

- **Docker** (v20.10+) — контейнеризация
- **Docker Compose** (v2.0+) — оркестрация контейнеров
- **Java 21+** — для локальной разработки
- **Maven** (входит в проект) — сборка проекта
- **OpenSSL** — генерация RSA ключей для JWT

### Проверка зависимостей

```bash
docker --version
docker-compose --version
java -version
openssl version
```

## 🚀 Быстрый старт (3 команды)

```bash
# 1. Инициализация проекта (генерация JWT ключей)
make init-project

# 2. Запуск всей инфраструктуры и приложения
make up

# 3. Откройте в браузере
open http://localhost:8080/q/swagger-ui
```

## 📁 Структура проекта

```
auth-service/
├── Dockerfile                              # Docker образ приложения
├── docker-compose.yml                      # Оркестрация сервисов
├── Makefile                                # Команды управления проектом
├── generate-keys.sh                        # Генератор JWT ключей
├── pom.xml                                 # Maven конфигурация
│
├── src/
│   └── main/
│       ├── java/org/atlas/
│       │   ├── dto/
│       │   │   └── AuthDto.java           # DTO для запросов/ответов
│       │   ├── entity/
│       │   │   └── User.java              # JPA сущность
│       │   ├── exception/
│       │   │   └── GlobalExceptionMapper.java
│       │   ├── grpc/
│       │   │   └── AuthGrpcServiceImpl.java   # gRPC сервис
│       │   ├── repository/
│       │   │   └── UserRepository.java
│       │   ├── resource/
│       │   │   └── AuthResource.java      # REST эндпоинты
│       │   └── service/
│       │       ├── AuthService.java       # Бизнес-логика
│       │       ├── JwtService.java        # Работа с JWT токенами
│       │       ├── RedisService.java      # Работа с Redis
│       │       └── UserEventProducer.java # Kafka producer
│       │
│       ├── proto/
│       │   └── auth.proto                 # gRPC схема
│       │
│       └── resources/
│           ├── application.properties     # Конфигурация приложения
│           ├── META-INF/resources/
│           │   ├── privateKey.pem         # ❌ НЕ коммитить!
│           │   └── publicKey.pem          # ✅ Публичный ключ
│           └── db/migration/
│               └── V1__create_users_table.sql  # Flyway миграции
│
├── .gitignore                              # Git исключения
├── .dockerignore                           # Docker исключения
└── README.md                               # Документация
```

## ⚙️ Команды Makefile

### 🔑 Инициализация и ключи

```bash
make init-project      # Инициализировать проект (генерировать ключи)
make gen-keys          # Сгенерировать/перегенерировать JWT RSA ключи
```

### 🚀 Запуск и остановка

```bash
make up                # Запустить все сервисы (сборка + docker-compose)
make down              # Остановить все сервисы
make restart           # Перезагрузить все сервисы
make rebuild           # Полная пересборка (clean + build + up)
make clean             # Остановить и удалить контейнеры/volumes ⚠️
```

### 💻 Разработка

```bash
make dev               # Запустить в режиме разработки Quarkus (live reload)
make build             # Собрать Docker образ
make build-native      # Собрать native образ (GraalVM, требует GraalVM)
make test              # Запустить тесты
```

### 📊 Логи и мониторинг

```bash
make logs              # Логи всех сервисов (follow mode)
make logs-service      # Логи Auth Service
make logs-db           # Логи PostgreSQL
make logs-redis        # Логи Redis
make logs-kafka        # Логи Kafka
make ps                # Список запущенных контейнеров
make status            # Статус сервисов и endpoints
make health            # Проверка здоровья всех сервисов
```

### 🗄️ Работа с БД и инструментами

```bash
make db-shell          # Открыть psql shell (PostgreSQL)
make redis-cli         # Открыть Redis CLI
make kafka-cli         # Открыть Kafka CLI
make shell             # Bash в контейнере auth-service
```

### 🌐 API и информация

```bash
make swagger           # Открыть Swagger UI в браузере
make version           # Информация о версиях (Docker, Java, Maven, OpenSSL)
```

### 🚢 Развертывание

```bash
make push TAG=myrepo/auth-service:1.0   # Загрузить образ в реестр
```

## 🔌 Сервисы и порты

| Сервис | Порт | Описание |
|--------|------|---------|
| **Auth Service (HTTP)** | 8080 | REST API |
| **Auth Service (gRPC)** | 9000 | gRPC Server |
| **PostgreSQL** | 5432 | Database |
| **Redis** | 6379 | Cache |
| **Kafka** | 9092 | Message Broker |
| **Zookeeper** | 2181 | Kafka Coordination |

### Endpoints

| Метод | URL | Описание | Auth |
|-------|-----|---------|------|
| POST | `/api/v1/auth/register` | Регистрация | ❌ |
| POST | `/api/v1/auth/login` | Логин | ❌ |
| POST | `/api/v1/auth/refresh` | Обновить токен | ❌ |
| POST | `/api/v1/auth/logout` | Выход | ✅ |
| GET | `/api/v1/auth/me` | Профиль | ✅ |

## 🌐 Веб-интерфейсы

- **Swagger UI**: http://localhost:8080/q/swagger-ui
- **OpenAPI Schema**: http://localhost:8080/q/openapi
- **Health Check**: http://localhost:8080/q/health
- **Ready Check**: http://localhost:8080/q/health/ready

## 🔐 JWT архитектура

```
┌─────────────────────────────────────────────────────────┐
│                    JWT ARCHITECTURE                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Access Token                                           │
│  ├─ Type: JWT                                           │
│  ├─ TTL: 15 минут (900 сек)                            │
│  ├─ Подпись: RSA (приватный ключ)                      │
│  └─ Хранение: В памяти клиента (не сохранять!)         │
│                                                          │
│  Refresh Token                                          │
│  ├─ Type: JWT                                           │
│  ├─ TTL: 7 дней (604800 сек)                           │
│  ├─ Подпись: RSA                                        │
│  └─ Хранение: Redis (по user_id)                       │
│                                                          │
│  Blacklist (отозванные токены)                         │
│  ├─ Тип: Redis SET                                      │
│  ├─ Ключ: token_jti                                     │
│  ├─ TTL: Access Token TTL                               │
│  └─ Причина: Logout или revoke                          │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Поток аутентификации

```
1. REGISTER
   POST /api/v1/auth/register
   ├─ email, password, name
   └─ → User создан в PostgreSQL

2. LOGIN
   POST /api/v1/auth/login
   ├─ email, password
   ├─ → Access Token (15 мин)
   ├─ → Refresh Token (7 дней) в Redis
   └─ → Publish user-events в Kafka

3. PROTECTED ENDPOINTS
   GET /api/v1/auth/me
   ├─ Header: Authorization: Bearer {access_token}
   ├─ gRPC ValidateToken (для микросервисов)
   └─ → User данные

4. REFRESH TOKEN
   POST /api/v1/auth/refresh
   ├─ refresh_token (из Redis)
   └─ → Новый Access Token

5. LOGOUT
   POST /api/v1/auth/logout
   ├─ Header: Authorization: Bearer {access_token}
   ├─ → Удалить Refresh Token из Redis
   ├─ → Добавить JTI в Blacklist (Redis)
   └─ → Publish user-events в Kafka
```

## 📨 Kafka события

### Топик: `user-events`

```json
{
  "event_type": "USER_REGISTERED|USER_UPDATED|USER_DELETED|USER_LOGGED_OUT",
  "user_id": "uuid",
  "email": "user@example.com",
  "timestamp": "2024-02-25T10:30:00Z",
  "metadata": {}
}
```

Событие публикуется при:
- ✅ Регистрация пользователя
- ✅ Обновление профиля
- ✅ Удаление аккаунта
- ✅ Выход (logout)

## 🔧 gRPC сервисы

Доступны по адресу: `localhost:9000`

### ValidateToken

```protobuf
service AuthService {
  rpc ValidateToken(ValidateTokenRequest) returns (ValidateTokenResponse);
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
}
```

**Использование для других микросервисов:**

```java
// Клиент gRPC в другом микросервисе
AuthServiceGrpc.AuthServiceBlockingStub stub = 
  AuthServiceGrpc.newBlockingStub(channel);

ValidateTokenResponse response = stub.validateToken(request);
```

## 🐳 Docker инструкции

### Структура образа

**Dockerfile** использует **multi-stage build** для оптимизации:

```dockerfile
# Stage 1: Builder (Maven + JDK 21)
FROM maven:3.9-eclipse-temurin-21 AS builder
# → Компилирует приложение

# Stage 2: Runtime (только JRE)
FROM eclipse-temurin:21-jre-alpine
# → Легкий образ для запуска
```

### Размер образа

- Builder: ~800 MB (не входит в финальный образ)
- Runtime: ~200-300 MB

## 📝 Пример использования API

### 1. Регистрация

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123!",
    "name": "John Doe"
  }'
```

**Ответ:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "John Doe",
  "created_at": "2024-02-25T10:30:00Z"
}
```

### 2. Логин

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePassword123!"
  }'
```

**Ответ:**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_expires_in": 604800
}
```

### 3. Получить профиль

```bash
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Ответ:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "John Doe",
  "created_at": "2024-02-25T10:30:00Z"
}
```

### 4. Обновить токен

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
  }'
```

### 5. Выход

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

## 🧪 Разработка

### Режим разработки (live reload)

```bash
make dev
```

Quarkus будет отслеживать изменения файлов и перезагружать приложение автоматически.

### Запуск тестов

```bash
make test
```

Или локально с Maven:

```bash
./mvnw test
```

## 🔍 Отладка

### 1. Просмотр логов

```bash
# Все сервисы
make logs

# Только приложение
make logs-service

# Следить за логами в реальном времени
docker-compose logs -f auth-service
```

### 2. Проверка здоровья

```bash
make health

# Или вручную
curl http://localhost:8080/q/health
curl http://localhost:8080/q/health/ready
curl http://localhost:8080/q/health/live
```

### 3. Подключение к БД

```bash
# PostgreSQL
make db-shell

# Полезные команды:
\dt              # Список таблиц
\d users         # Схема таблицы users
SELECT * FROM users;

# Выход
\q
```

### 4. Работа с Redis

```bash
make redis-cli

# Полезные команды:
KEYS *                          # Все ключи
GET user:refresh:user_id        # Получить refresh token
DEL user:refresh:user_id        # Удалить token
FLUSHDB                         # Очистить БД
QUIT                            # Выход
```

## ⚠️ Важные заметки

### 1. JWT ключи

```bash
# НИКОГДА не коммитить privateKey.pem!
# Он уже в .gitignore, но убедитесь:
cat .gitignore | grep privateKey
```

### 2. Переменные окружения

Все переменные окружения для Docker уже настроены в `docker-compose.yml`.

Для локальной разработки используйте `application.properties`.

### 3. Аварийная остановка

Если нужна полная очистка:

```bash
# Остановить и удалить всё
make clean

# Или вручную
docker-compose down -v
docker system prune -f
```

### 4. Работа с базой данных

Flyway автоматически выполняет миграции при запуске:

```sql
-- src/main/resources/db/migration/V1__create_users_table.sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  name VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
```

Для добавления новых миграций создайте файл `V2__your_migration.sql`.

## 🚀 Развертывание в production

### 1. Собрать образ

```bash
docker build -t myrepo/auth-service:1.0 .
```

### 2. Загрузить в реестр

```bash
docker push myrepo/auth-service:1.0
```

### 3. Kubernetes deployment (пример)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
      - name: auth-service
        image: myrepo/auth-service:1.0
        ports:
        - containerPort: 8080
        - containerPort: 9000
        env:
        - name: QUARKUS_DATASOURCE_JDBC_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
```

## 🐛 Troubleshooting

### Проблема: `Port 8080 already in use`

```bash
# Найти процесс
lsof -i :8080

# Убить процесс
kill -9 <PID>

# Или использовать другой порт в docker-compose.yml
```

### Проблема: `Connection refused to postgresql`

```bash
# Проверить статус сервиса
make ps

# Просмотреть логи PostgreSQL
make logs-db

# Убедитесь, что контейнер здоров
make health
```

### Проблема: `JWT keys not found`

```bash
# Перегенерировать ключи
make gen-keys

# Проверить наличие файлов
ls -la src/main/resources/META-INF/resources/
```

### Проблема: `Gradle/Maven build fails`

```bash
# Очистить кеш Maven
./mvnw clean

# Пересобрать с пропуском тестов
./mvnw clean package -DskipTests
```

## 📚 Полезные ссылки

- [Quarkus Documentation](https://quarkus.io/guides/)
- [JWT.io](https://jwt.io/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [gRPC Java](https://grpc.io/docs/languages/java/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Redis Documentation](https://redis.io/documentation)

## 📄 License

MIT License - используйте свободно

---

**Создано с ❤️ для безопасной аутентификации в микросервисах**

Вопросы? Проверьте логи: `make logs`
