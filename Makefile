.PHONY: up down ps bootstrap build test lint help

COMPOSE_FILE ?= docker-compose.yml
COMPOSE      := docker compose -f $(COMPOSE_FILE)

## ─── Infrastructure ────────────────────────────────────────────

up: ## Start all infrastructure containers in detached mode
	$(COMPOSE) up -d
	@echo "✓ Infrastructure started. Run 'make ps' to verify."

down: ## Stop and remove all containers (keeps volumes)
	$(COMPOSE) down

down-v: ## Stop containers and DELETE volumes (destructive)
	$(COMPOSE) down -v

ps: ## Show status of running containers
	$(COMPOSE) ps

logs: ## Tail logs from all containers (Ctrl-C to stop)
	$(COMPOSE) logs -f

## ─── Bootstrap ─────────────────────────────────────────────────

bootstrap: ## Create Kafka topics and MinIO buckets
	@bash scripts/bootstrap.sh

## ─── Build ─────────────────────────────────────────────────────

GO_SERVICES := adapter-datadog adapter-newrelic adapter-dynatrace adapter-otel \
               ingest-gateway normalizer correlator snapshotter topology-builder query-api

build: ## Build all Go services
	@for svc in $(GO_SERVICES); do \
		echo "→ Building services/$$svc ..."; \
		(cd services/$$svc && go build ./...); \
	done
	@echo "✓ All Go services built."

build-svc: ## Build a single Go service: make build-svc SVC=ingest-gateway
	@test -n "$(SVC)" || (echo "Usage: make build-svc SVC=<service-name>" && exit 1)
	cd services/$(SVC) && go build ./...

## ─── Test ──────────────────────────────────────────────────────

test: ## Run tests across all Go services
	@for svc in $(GO_SERVICES); do \
		echo "→ Testing services/$$svc ..."; \
		(cd services/$$svc && go test ./... -count=1); \
	done

test-python: ## Run rca-engine tests
	cd services/rca-engine && python -m pytest -q

## ─── Lint ──────────────────────────────────────────────────────

lint: lint-go lint-python lint-ts ## Run all linters

lint-go: ## Lint Go services with golangci-lint
	@command -v golangci-lint >/dev/null 2>&1 || (echo "golangci-lint not found; install: https://golangci-lint.run/usage/install/" && exit 1)
	@for svc in $(GO_SERVICES); do \
		echo "→ Linting services/$$svc ..."; \
		(cd services/$$svc && golangci-lint run ./...); \
	done

lint-python: ## Lint Python with ruff
	@command -v ruff >/dev/null 2>&1 || (echo "ruff not found; pip install ruff" && exit 1)
	ruff check services/rca-engine

lint-ts: ## Lint TypeScript console with eslint
	cd apps/console && npm run lint 2>/dev/null || echo "(no lint script yet)"

## ─── Help ──────────────────────────────────────────────────────

help: ## Show this help message
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

.DEFAULT_GOAL := help
