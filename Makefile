# Kurumsal AI Asistan Platformu — Developer Makefile
# All commands run from repo root.

.PHONY: help up down restart logs ps health \
        proto proto-lint proto-breaking \
        test test-gateway test-identity test-documents test-processing test-aiengine test-realtime \
        clean clean-all \
        db-shell redis-shell \
        seed

# Default: print help
help: ## Show this help
	@echo "Kurumsal AI Asistan Platformu — Common commands"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ─── Lifecycle ────────────────────────────────────────────────────────────

up: ## Start the entire stack (detached)
	docker compose up -d --build

down: ## Stop and remove containers
	docker compose down

restart: down up ## Restart the stack

logs: ## Tail logs from all services
	docker compose logs -f --tail=100

logs-%: ## Tail logs from a specific service (e.g. make logs-aiengine)
	docker compose logs -f --tail=200 $*

ps: ## Show running containers
	docker compose ps

# ─── Health ───────────────────────────────────────────────────────────────

health: ## Hit every service /health/ready
	@echo "🔍 Health check..."
	@for svc in gateway:8080 identity:8081 documents:8082 processing:8083 aiengine:8084 realtime:8085; do \
		port=$${svc#*:}; name=$${svc%:*}; \
		printf "  %-12s " "$$name"; \
		curl -fsS -m 3 http://localhost:$$port/health/ready >/dev/null 2>&1 && echo "✅" || echo "❌"; \
	done

# ─── Proto (gRPC contracts) ───────────────────────────────────────────────

proto: ## Generate gRPC stubs for all languages
	cd protos && buf generate

proto-lint: ## Lint .proto files
	cd protos && buf lint

proto-breaking: ## Detect breaking changes against main branch
	cd protos && buf breaking --against ".git#branch=main"

# ─── Test ─────────────────────────────────────────────────────────────────

test: test-gateway test-identity test-documents test-processing test-aiengine test-realtime ## Run all service test suites

test-gateway: ## Test Gateway (C#)
	cd services/gateway && dotnet test

test-identity: ## Test Identity (C#)
	cd services/identity && dotnet test

test-documents: ## Test Documents (Java)
	cd services/documents && ./mvnw test

test-processing: ## Test Processing (Rust)
	cd services/processing && cargo test --workspace

test-aiengine: ## Test AI Engine (Python)
	cd services/aiengine && uv run pytest

test-realtime: ## Test Realtime (TS)
	cd services/realtime && pnpm test

# ─── Database ─────────────────────────────────────────────────────────────

db-shell: ## Open psql in the Postgres container
	docker compose exec postgres psql -U $${POSTGRES_USER:-kai} -d $${POSTGRES_DB:-kai}

redis-shell: ## Open redis-cli
	docker compose exec redis redis-cli

seed: ## Seed minimal demo data (1 tenant + 1 user + 1 doc)
	./scripts/seed.sh

# ─── Cleanup ──────────────────────────────────────────────────────────────

clean: down ## Stop containers (data preserved)

clean-all: ## Stop containers AND remove all volumes (destroys data)
	docker compose down -v
	@echo "⚠️  All persistent data wiped."
