.PHONY: help build up down logs logs-service logs-db logs-redis logs-kafka restart clean rebuild status shell ps \
        db-shell redis-cli kafka-cli test coverage dev build-native push

# Colors for output
GREEN := \033[0;32m
BLUE := \033[0;34m
YELLOW := \033[0;33m
RED := \033[0;31m
NC := \033[0m # No Color

# Default target
.DEFAULT_GOAL := help

# Project name
PROJECT_NAME := auth-service
DOCKER_COMPOSE := docker-compose
DOCKER := docker

help: ## Display this help message
	@echo "$(BLUE)╔════════════════════════════════════════════════════════════╗$(NC)"
	@echo "$(BLUE)║    Quarkus Auth Service - Docker & Project Commands        ║$(NC)"
	@echo "$(BLUE)╚════════════════════════════════════════════════════════════╝$(NC)"
	@echo ""
	@echo "$(GREEN)SETUP & INITIALIZATION$(NC)"
	@echo "  $(YELLOW)make init-project$(NC)      Initialize project (gen keys + setup)"
	@echo "  $(YELLOW)make gen-keys$(NC)          Generate JWT RSA keys"
	@echo ""
	@echo "$(GREEN)STARTUP$(NC)"
	@echo "  $(YELLOW)make up$(NC)                 Start all services (build + compose)"
	@echo "  $(YELLOW)make build$(NC)              Build Docker image only"
	@echo "  $(YELLOW)make dev$(NC)                Start in dev mode (Quarkus dev)"
	@echo ""
	@echo "$(GREEN)STOP & CLEAN$(NC)"
	@echo "  $(YELLOW)make down$(NC)               Stop all services"
	@echo "  $(YELLOW)make restart$(NC)            Restart all services"
	@echo "  $(YELLOW)make clean$(NC)              Stop and remove all containers/volumes"
	@echo "  $(YELLOW)make rebuild$(NC)            Clean build and restart"
	@echo ""
	@echo "$(GREEN)LOGS$(NC)"
	@echo "  $(YELLOW)make logs$(NC)               Show logs from all services"
	@echo "  $(YELLOW)make logs-service$(NC)       Show logs from auth-service"
	@echo "  $(YELLOW)make logs-db$(NC)            Show logs from PostgreSQL"
	@echo "  $(YELLOW)make logs-redis$(NC)         Show logs from Redis"
	@echo "  $(YELLOW)make logs-kafka$(NC)         Show logs from Kafka"
	@echo ""
	@echo "$(GREEN)DATABASE & TOOLS$(NC)"
	@echo "  $(YELLOW)make db-shell$(NC)           Open PostgreSQL shell"
	@echo "  $(YELLOW)make redis-cli$(NC)          Open Redis CLI"
	@echo "  $(YELLOW)make kafka-cli$(NC)          Open Kafka CLI"
	@echo ""
	@echo "$(GREEN)SHELL & INSPECT$(NC)"
	@echo "  $(YELLOW)make shell$(NC)              Open shell in auth-service container"
	@echo "  $(YELLOW)make ps$(NC)                 Show running containers"
	@echo "  $(YELLOW)make status$(NC)             Show services status"
	@echo ""
	@echo "$(GREEN)BUILD & DEPLOY$(NC)"
	@echo "  $(YELLOW)make build-native$(NC)       Build GraalVM native image"
	@echo "  $(YELLOW)make push$(NC)               Push image to registry (requires TAG)"
	@echo "  $(YELLOW)make test$(NC)               Run all tests + coverage gate"
	@echo "  $(YELLOW)make coverage$(NC)           Run tests and open coverage report"
	@echo ""
	@echo "$(GREEN)API & HEALTH$(NC)"
	@echo "  $(YELLOW)make swagger$(NC)            Open Swagger UI in browser"
	@echo "  $(YELLOW)make health$(NC)             Check service health"
	@echo "  $(YELLOW)make version$(NC)            Show version info"
	@echo ""

# ============================================================================
# SETUP & INITIALIZATION
# ============================================================================

init-project: gen-keys ## Initialize project (generate keys + create directories)
	@echo "$(GREEN)✓ Project initialized!$(NC)"
	@echo ""
	@echo "$(BLUE)Next step: Start services with $(YELLOW)make up$(NC)"

gen-keys: ## Generate JWT RSA keys for authentication
	@echo "$(BLUE)▶ Generating JWT RSA keys...$(NC)"
	@chmod +x generate-keys.sh
	@./generate-keys.sh
	@echo "$(GREEN)✓ Keys generated successfully!$(NC)"

# ============================================================================
# STARTUP & BUILD
# ============================================================================

up: ## Start all services (build + docker-compose up)
	@echo "$(BLUE)▶ Starting all services...$(NC)"
	$(DOCKER_COMPOSE) up -d --build
	@echo "$(GREEN)✓ Services started successfully!$(NC)"
	@echo ""
	@echo "$(BLUE)Access your services:$(NC)"
	@echo "  API:           $(YELLOW)http://localhost:8080$(NC)"
	@echo "  Swagger UI:    $(YELLOW)http://localhost:8080/q/swagger-ui$(NC)"
	@echo "  OpenAPI:       $(YELLOW)http://localhost:8080/q/openapi$(NC)"
	@echo "  Health:        $(YELLOW)http://localhost:8080/q/health$(NC)"
	@echo "  gRPC:          $(YELLOW)localhost:9000$(NC)"
	@make status

build: ## Build Docker image only
	@echo "$(BLUE)▶ Building Docker image...$(NC)"
	$(DOCKER_COMPOSE) build --no-cache
	@echo "$(GREEN)✓ Docker image built successfully!$(NC)"

dev: ## Start in Quarkus dev mode (with live reload)
	@echo "$(BLUE)▶ Starting in DEV mode...$(NC)"
	./mvnw quarkus:dev

# ============================================================================
# STOP & RESTART
# ============================================================================

down: ## Stop all services
	@echo "$(BLUE)▶ Stopping all services...$(NC)"
	$(DOCKER_COMPOSE) down
	@echo "$(GREEN)✓ Services stopped!$(NC)"

restart: down up ## Restart all services
	@echo "$(GREEN)✓ Services restarted!$(NC)"

rebuild: clean up ## Full rebuild (clean + build + up)
	@echo "$(GREEN)✓ Rebuild complete!$(NC)"

clean: ## Stop and remove all containers/volumes (⚠️ WARNING: Deletes data)
	@echo "$(RED)⚠ WARNING: This will delete all containers and data volumes!$(NC)"
	@read -p "Are you sure? (y/N) " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		echo "$(RED)▶ Cleaning up...$(NC)"; \
		$(DOCKER_COMPOSE) down -v; \
		echo "$(GREEN)✓ Cleanup complete!$(NC)"; \
	else \
		echo "$(YELLOW)Cancelled$(NC)"; \
	fi

# ============================================================================
# LOGS
# ============================================================================

logs: ## Show logs from all services (follow mode)
	@echo "$(BLUE)▶ Showing logs from all services (Ctrl+C to exit)...$(NC)"
	$(DOCKER_COMPOSE) logs -f

logs-service: ## Show logs from auth-service
	@echo "$(BLUE)▶ Showing logs from auth-service (Ctrl+C to exit)...$(NC)"
	$(DOCKER_COMPOSE) logs -f auth-service

logs-db: ## Show logs from PostgreSQL
	@echo "$(BLUE)▶ Showing logs from PostgreSQL (Ctrl+C to exit)...$(NC)"
	$(DOCKER_COMPOSE) logs -f postgres

logs-redis: ## Show logs from Redis
	@echo "$(BLUE)▶ Showing logs from Redis (Ctrl+C to exit)...$(NC)"
	$(DOCKER_COMPOSE) logs -f redis

logs-kafka: ## Show logs from Kafka
	@echo "$(BLUE)▶ Showing logs from Kafka (Ctrl+C to exit)...$(NC)"
	$(DOCKER_COMPOSE) logs -f kafka

# ============================================================================
# DATABASE & TOOLS
# ============================================================================

db-shell: ## Open PostgreSQL shell (psql)
	@echo "$(BLUE)▶ Opening PostgreSQL shell...$(NC)"
	@echo "$(YELLOW)Commands:$(NC)"
	@echo "  \\dt          - list tables"
	@echo "  \\du          - list users"
	@echo "  \\q           - quit"
	@echo ""
	$(DOCKER_COMPOSE) exec postgres psql -U auth_user -d auth_db

redis-cli: ## Open Redis CLI
	@echo "$(BLUE)▶ Opening Redis CLI...$(NC)"
	@echo "$(YELLOW)Commands:$(NC)"
	@echo "  KEYS *       - list all keys"
	@echo "  GET <key>    - get value"
	@echo "  FLUSHDB      - clear database"
	@echo "  QUIT         - exit"
	@echo ""
	$(DOCKER_COMPOSE) exec redis redis-cli

kafka-cli: ## Open Kafka CLI
	@echo "$(BLUE)▶ Opening Kafka CLI (type 'exit' to quit)...$(NC)"
	$(DOCKER_COMPOSE) exec kafka bash

# ============================================================================
# SHELL & INSPECT
# ============================================================================

shell: ## Open bash shell in auth-service container
	@echo "$(BLUE)▶ Opening shell in auth-service container...$(NC)"
	$(DOCKER_COMPOSE) exec auth-service /bin/sh

ps: ## Show running containers
	@echo "$(BLUE)▶ Running containers:$(NC)"
	@$(DOCKER_COMPOSE) ps

status: ## Show services status and endpoints
	@echo ""
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"
	@echo "$(BLUE)                    SERVICE STATUS$(NC)"
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"
	@$(DOCKER_COMPOSE) ps
	@echo ""
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"
	@echo "$(BLUE)                    ENDPOINTS$(NC)"
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"
	@echo "$(GREEN)HTTP API$(NC)             http://localhost:8080"
	@echo "$(GREEN)Swagger UI$(NC)           http://localhost:8080/q/swagger-ui"
	@echo "$(GREEN)OpenAPI$(NC)              http://localhost:8080/q/openapi"
	@echo "$(GREEN)Health Check$(NC)         http://localhost:8080/q/health"
	@echo "$(GREEN)gRPC Server$(NC)          localhost:9000"
	@echo ""
	@echo "$(GREEN)PostgreSQL$(NC)           postgres:5432"
	@echo "  User: auth_user | Pass: auth_pass | DB: auth_db"
	@echo ""
	@echo "$(GREEN)Redis$(NC)                redis://localhost:6379"
	@echo ""
	@echo "$(GREEN)Kafka$(NC)                localhost:9092"
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"

# ============================================================================
# HEALTH CHECK
# ============================================================================

health: ## Check service health
	@echo "$(BLUE)▶ Checking service health...$(NC)"
	@echo ""
	@echo "$(YELLOW)Auth Service:$(NC)"
	@curl -s http://localhost:8080/q/health/ready | jq '.' || echo "$(RED)✗ Service not responding$(NC)"
	@echo ""
	@echo "$(YELLOW)Database:$(NC)"
	@$(DOCKER_COMPOSE) exec -T postgres pg_isready -U auth_user -d auth_db && echo "$(GREEN)✓ PostgreSQL healthy$(NC)" || echo "$(RED)✗ PostgreSQL down$(NC)"
	@echo ""
	@echo "$(YELLOW)Redis:$(NC)"
	@$(DOCKER_COMPOSE) exec -T redis redis-cli ping && echo "$(GREEN)✓ Redis healthy$(NC)" || echo "$(RED)✗ Redis down$(NC)"
	@echo ""
	@echo "$(YELLOW)Kafka:$(NC)"
	@$(DOCKER_COMPOSE) exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1 && echo "$(GREEN)✓ Kafka healthy$(NC)" || echo "$(RED)✗ Kafka down$(NC)"
	@echo ""

# ============================================================================
# BUILD & DEPLOY
# ============================================================================

test: ## Run all tests + coverage gate (needs Docker for Testcontainers)
	@echo "$(BLUE)▶ Running tests with coverage gate...$(NC)"
	./mvnw -B clean verify

coverage: ## Run tests and open the JaCoCo HTML coverage report
	@echo "$(BLUE)▶ Generating coverage report...$(NC)"
	./mvnw -B clean verify
	@echo "$(GREEN)✓ Report: target/site/jacoco/index.html$(NC)"
	@command -v open >/dev/null 2>&1 && open target/site/jacoco/index.html || \
	command -v xdg-open >/dev/null 2>&1 && xdg-open target/site/jacoco/index.html || \
	echo "$(YELLOW)Open manually: target/site/jacoco/index.html$(NC)"

build-native: ## Build GraalVM native image (requires GraalVM)
	@echo "$(BLUE)▶ Building native image...$(NC)"
	./mvnw package -DskipTests \
		-Dquarkus.package.jar.enabled=false \
		-Dquarkus.native.enabled=true
	@echo "$(GREEN)✓ Native image built successfully!$(NC)"

push: ## Push image to Docker registry
	@echo "$(BLUE)▶ Pushing image to registry...$(NC)"
	@if [ -z "$(TAG)" ]; then \
		echo "$(RED)Error: TAG not specified!$(NC)"; \
		echo "Usage: make push TAG=myregistry/auth-service:1.0"; \
		exit 1; \
	fi
	$(DOCKER) tag $(PROJECT_NAME):latest $(TAG)
	$(DOCKER) push $(TAG)
	@echo "$(GREEN)✓ Image pushed to $(TAG)$(NC)"

# ============================================================================
# CLEANUP HELPERS
# ============================================================================

prune: ## Remove all unused Docker images/containers/volumes
	@echo "$(RED)▶ Pruning Docker...$(NC)"
	$(DOCKER) system prune -f
	@echo "$(GREEN)✓ Pruning complete!$(NC)"

logs-tail: ## Show last 50 lines of logs and follow
	$(DOCKER_COMPOSE) logs --tail=50 -f

# ============================================================================
# API & ENDPOINTS
# ============================================================================

swagger: ## Open Swagger UI in default browser
	@echo "$(BLUE)▶ Opening Swagger UI...$(NC)"
	@command -v open >/dev/null 2>&1 && open http://localhost:8080/q/swagger-ui || \
	command -v xdg-open >/dev/null 2>&1 && xdg-open http://localhost:8080/q/swagger-ui || \
	echo "$(YELLOW)Open manually: http://localhost:8080/q/swagger-ui$(NC)"

# ============================================================================
# VERSION INFO
# ============================================================================

version: ## Show version info
	@echo "$(BLUE)╔════════════════════════════════════════════════════════════╗$(NC)"
	@echo "$(BLUE)║                    VERSION INFORMATION                     ║$(NC)"
	@echo "$(BLUE)╚════════════════════════════════════════════════════════════╝$(NC)"
	@echo ""
	@echo "$(YELLOW)Docker:$(NC)"
	@$(DOCKER) --version
	@echo ""
	@echo "$(YELLOW)Docker Compose:$(NC)"
	@$(DOCKER_COMPOSE) --version
	@echo ""
	@echo "$(YELLOW)Java:$(NC)"
	@java -version 2>&1 || echo "Java not installed"
	@echo ""
	@echo "$(YELLOW)Maven:$(NC)"
	@./mvnw --version 2>&1 || echo "Maven not installed"
	@echo ""
	@echo "$(YELLOW)OpenSSL (for JWT key generation):$(NC)"
	@openssl version || echo "OpenSSL not installed"
	@echo ""
