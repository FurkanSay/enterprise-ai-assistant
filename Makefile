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
# buf runs in Docker so contributors do not need a local install.
# Pinned tag avoids surprise plugin/API changes.
BUF_VERSION := 1.45.0
BUF_DOCKER := docker run --rm -v "$(CURDIR):/workspace" -w /workspace/protos bufbuild/buf:$(BUF_VERSION)

proto: ## Generate gRPC stubs for all languages (Docker-backed buf)
	$(BUF_DOCKER) generate

proto-lint: ## Lint .proto files
	$(BUF_DOCKER) lint

proto-breaking: ## Detect breaking changes against main branch
	$(BUF_DOCKER) breaking --against "/workspace/.git#branch=main,subdir=protos"

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

# ─── Image cache (offline backup) ─────────────────────────────────────────
# Save built service images as tarballs so that a Docker Desktop reset or
# pruned builder cache does not force a multi-GB re-download (especially
# AI Engine's PyTorch wheel). Tarballs live in infra/cache/ — gitignored.

IMAGE_SERVICES := gateway identity documents processing aiengine realtime frontend
IMAGE_CACHE_DIR := infra/cache

save-images: ## Dump all service images to infra/cache/*.tar
	@mkdir -p $(IMAGE_CACHE_DIR)
	@for svc in $(IMAGE_SERVICES); do \
	  echo "📦 saving $$svc..."; \
	  docker save -o $(IMAGE_CACHE_DIR)/$$svc.tar kurumsal-ai-asistan-$$svc:latest; \
	done
	@echo "✅ Images saved to $(IMAGE_CACHE_DIR)/"

load-images: ## Reload service images from infra/cache/*.tar
	@for tar in $(IMAGE_CACHE_DIR)/*.tar; do \
	  echo "📥 loading $$tar..."; \
	  docker load -i $$tar; \
	done
	@echo "✅ Images restored."

# ─── Cleanup ──────────────────────────────────────────────────────────────

clean: down ## Stop containers (data preserved)

clean-all: ## Stop containers AND remove all volumes (destroys data)
	docker compose down -v
	@echo "⚠️  All persistent data wiped."
