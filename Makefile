# Wallet API Makefile
# Provides convenient commands for development, testing, and deployment

.PHONY: help build test clean start start-test stop restart logs status health perf-test perf-setup perf-seed perf-run-all perf-cleanup perf-quick perf-logs perf-status perf-results

# Default target
help:
	@echo "Wallet API - Available Commands:"
	@echo ""
	@echo "Development:"
	@echo "  start       - Start PostgreSQL and Spring Boot application"
	@echo "  start-test  - Start application with TEST profile (rate limiting disabled)"
	@echo "  stop        - Stop all services"
	@echo "  restart     - Restart all services"
	@echo "  logs        - Show application logs"
	@echo "  status      - Check service status"
	@echo ""
	@echo "Building & Testing:"
	@echo "  build       - Build the application"
	@echo "  test        - Run unit and integration tests"
	@echo "  clean       - Clean build artifacts"
	@echo ""
	@echo "Performance Testing:"
	@echo "  perf-test     - Run comprehensive k6 performance tests (setup + seed + run + cleanup)"
	@echo "  perf-quick    - Run quick performance test (wallet creation only)"
	@echo "  perf-setup    - Setup performance test environment"
	@echo "  perf-seed     - Seed test data (1000 wallets)"
	@echo "  perf-run-all  - Run all k6 performance tests"
	@echo "  perf-cleanup  - Cleanup performance test environment"
	@echo "  perf-logs     - Show performance test application logs"
	@echo "  perf-status   - Check performance test environment status"
	@echo "  perf-results  - Show performance test results"
	@echo ""
	@echo "Monitoring:"
	@echo "  health      - Check application health"
	@echo "  metrics     - Show application metrics"

# Development commands
start:
	@echo "🚀 Starting Wallet API..."
	docker-compose up -d
	@echo "⏳ Waiting for PostgreSQL to be ready..."
	@for i in $$(seq 1 30); do \
		if docker-compose exec -T postgres pg_isready -U crablet >/dev/null 2>&1; then \
			echo "✅ PostgreSQL is ready"; \
			break; \
		fi; \
		if [ $$i -eq 30 ]; then \
			echo "❌ PostgreSQL failed to start"; \
			exit 1; \
		fi; \
		sleep 1; \
	done
	@echo "🚀 Starting Spring Boot application..."
	mvn spring-boot:run > app.log 2>&1 &
	@echo "⏳ Waiting for application to be ready..."
	@for i in $$(seq 1 30); do \
		if curl -s http://localhost:8080/actuator/health >/dev/null 2>&1; then \
			echo "✅ Application is ready at http://localhost:8080"; \
			echo "📊 Health check: http://localhost:8080/actuator/health"; \
			echo "📈 Metrics: http://localhost:8080/actuator/metrics"; \
			break; \
		fi; \
		if [ $$i -eq 30 ]; then \
			echo "❌ Application failed to start. Check logs with 'make logs'"; \
			exit 1; \
		fi; \
		sleep 2; \
	done

start-test:
	@echo "🚀 Starting Wallet API with TEST profile (rate limiting disabled)..."
	docker-compose up -d
	@echo "⏳ Waiting for PostgreSQL to be ready..."
	@for i in $$(seq 1 30); do \
		if docker-compose exec -T postgres pg_isready -U crablet >/dev/null 2>&1; then \
			echo "✅ PostgreSQL is ready"; \
			break; \
		fi; \
		if [ $$i -eq 30 ]; then \
			echo "❌ PostgreSQL failed to start"; \
			exit 1; \
		fi; \
		sleep 1; \
	done
	@echo "🚀 Starting Spring Boot application with TEST profile (no outbox)..."
	mvn spring-boot:run -Dspring-boot.run.profiles=test-no-outbox > app.log 2>&1 &
	@echo "⏳ Waiting for application to be ready..."
	@for i in $$(seq 1 30); do \
		if curl -s http://localhost:8080/actuator/health >/dev/null 2>&1; then \
			echo "✅ Application is ready at http://localhost:8080"; \
			echo "📊 Health check: http://localhost:8080/actuator/health"; \
			echo "📈 Metrics: http://localhost:8080/actuator/metrics"; \
			echo "⚠️  Rate limiting is DISABLED (test profile active)"; \
			break; \
		fi; \
		if [ $$i -eq 30 ]; then \
			echo "❌ Application failed to start. Check logs with 'make logs'"; \
			exit 1; \
		fi; \
		sleep 2; \
	done

stop:
	@echo "🛑 Stopping services..."
	@pkill -f "spring-boot:run" || true
	docker-compose down -v
	@rm -f app.log
	@echo "✅ All services stopped"

restart: stop start

logs:
	@echo "📋 Application logs:"
	@tail -f app.log 2>/dev/null || echo "No application logs found. Run 'make start' first."

status:
	@echo "🔍 Service Status:"
	@echo "PostgreSQL: $$(docker-compose ps postgres | grep -q 'Up' && echo '✅ Running' || echo '❌ Stopped')"
	@echo "Spring Boot: $$(curl -s http://localhost:8080/actuator/health >/dev/null 2>&1 && echo '✅ Running' || echo '❌ Stopped')"

# Building & Testing
build:
	@echo "🔨 Building application..."
	mvn clean compile
	@echo "✅ Build complete"

test:
	@echo "🧪 Running tests..."
	mvn test
	@echo "✅ Tests complete"

clean:
	@echo "🧹 Cleaning build artifacts..."
	mvn clean
	@rm -f app.log
	@echo "✅ Clean complete"

# Performance Testing
perf-test:
	@echo "🚀 Running comprehensive performance tests..."
	@make perf-setup
	@make perf-seed
	@make perf-run-all
	@make perf-cleanup

perf-setup:
	@echo "🔧 Setting up performance test environment..."
	@echo "📦 Starting PostgreSQL..."
	docker-compose up -d
	@echo "⏳ Waiting for PostgreSQL to be ready..."
	@for i in $$(seq 1 30); do \
		if docker-compose exec -T postgres pg_isready -U crablet >/dev/null 2>&1; then \
			echo "✅ PostgreSQL is ready"; \
			break; \
		fi; \
		if [ $$i -eq 30 ]; then \
			echo "❌ PostgreSQL failed to start"; \
			exit 1; \
		fi; \
		sleep 1; \
	done
	@echo "🗄️ Running database migrations..."
	mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/crablet -Dflyway.user=crablet -Dflyway.password=crablet >/dev/null 2>&1
	@echo "🚀 Starting Spring Boot application with TEST profile (no outbox, rate limiting disabled)..."
	mvn spring-boot:run -Dspring-boot.run.profiles=test-no-outbox > perf-app.log 2>&1 &
	@echo "⏳ Waiting for application to be ready..."
	@for i in $$(seq 1 30); do \
		if curl -s http://localhost:8080/actuator/health >/dev/null 2>&1; then \
			echo "✅ Application is ready at http://localhost:8080"; \
			echo "⚠️  Rate limiting is DISABLED for performance testing"; \
			break; \
		fi; \
		if [ $$i -eq 30 ]; then \
			echo "❌ Application failed to start. Check logs with 'make perf-logs'"; \
			exit 1; \
		fi; \
		sleep 2; \
	done
	@echo "✅ Performance test environment ready!"

perf-seed:
	@echo "🌱 Seeding test data (1000 success wallets + 10 insufficient wallets)..."
	cd performance-tests && k6 run setup/seed-success-data.js --console-output /tmp/seed-console.log
	@grep "WALLET_DATA:" /tmp/seed-console.log | head -1 | sed 's/.*WALLET_DATA://' | sed 's/\\"/"/g' > performance-tests/setup/verified-wallets.json || echo '{"wallets":[],"count":0,"timestamp":0}' > performance-tests/setup/verified-wallets.json
	@echo "🌱 Seeding insufficient balance wallets..."
	cd performance-tests && k6 run setup/seed-insufficient-data.js --console-output /tmp/seed-insufficient-console.log
	@echo "✅ Test data seeded successfully!"

perf-run-all:
	@echo "🧪 Running all k6 performance tests..."
	@echo "📊 Results will be saved to performance-tests/results/"
	@mkdir -p performance-tests/results
	@echo ""
	@echo "1️⃣ Wallet Creation Load Test..."
	cd performance-tests && k6 run wallet-creation-load.js --out json=results/wallet-creation-load_results.json
	@echo ""
	@echo "2️⃣ Simple Deposit Test..."
	cd performance-tests && k6 run simple-deposit-test.js --out json=results/simple-deposit-test_results.json
	@echo ""
	@echo "3️⃣ Simple Withdrawal Test..."
	cd performance-tests && k6 run simple-withdrawal-test.js --out json=results/simple-withdrawal-test_results.json
	@echo ""
	@echo "4️⃣ Simple Transfer Test..."
	cd performance-tests && k6 run simple-transfer-test.js --out json=results/simple-transfer-test_results.json
	@echo ""
	@echo "5️⃣ Simple Mixed Workload Test..."
	cd performance-tests && k6 run simple-mixed-workload-test.js --out json=results/simple-mixed-workload-test_results.json
	@echo ""
	@echo "6️⃣ Simple History Test..."
	cd performance-tests && k6 run simple-history-test.js --out json=results/simple-history-test_results.json
	@echo ""
	@echo "7️⃣ Simple Spike Test..."
	cd performance-tests && k6 run simple-spike-test.js --out json=results/simple-spike-test_results.json
	@echo ""
	@echo "8️⃣ Simple Insufficient Balance Test..."
	cd performance-tests && k6 run simple-insufficient-balance-test.js --out json=results/simple-insufficient-balance-test_results.json
	@echo ""
	@echo "9️⃣ Simple Concurrency Test..."
	cd performance-tests && k6 run simple-concurrency-test.js --out json=results/simple-concurrency-test_results.json
	@echo ""
	@echo "✅ All performance tests completed!"
	@echo "📁 Results saved to performance-tests/results/"
	@echo "📊 Generating performance summary..."
	@cd performance-tests && ./generate-summary.sh

perf-cleanup:
	@echo "🧹 Cleaning up performance test environment..."
	@pkill -f "spring-boot:run" || true
	@pkill -f "com.wallets.Application" || true
	@sleep 2  # Give processes time to terminate
	docker-compose down
	@rm -f perf-app.log
	@echo "✅ Cleanup complete"

perf-quick:
	@echo "⚡ Running quick performance test (wallet creation only)..."
	@make perf-setup
	@echo "🧪 Running wallet creation load test..."
	cd performance-tests && k6 run wallet-creation-load.js
	@make perf-cleanup

perf-logs:
	@echo "📋 Performance test application logs:"
	@tail -f perf-app.log 2>/dev/null || echo "No performance test logs found. Run 'make perf-test' first."

perf-status:
	@echo "🔍 Performance Test Environment Status:"
	@echo "PostgreSQL: $$(docker-compose ps postgres | grep -q 'Up' && echo '✅ Running' || echo '❌ Stopped')"
	@echo "Spring Boot: $$(curl -s http://localhost:8080/actuator/health >/dev/null 2>&1 && echo '✅ Running' || echo '❌ Stopped')"
	@echo "Results Directory: $$(test -d performance-tests/results && echo '✅ Exists' || echo '❌ Not found')"

perf-results:
	@echo "📊 Performance Test Results:"
	@if [ -d "performance-tests/results" ]; then \
		echo "📁 Results directory contents:"; \
		ls -la performance-tests/results/; \
		echo ""; \
		if [ -f "performance-tests/results/summary.md" ]; then \
			echo "📋 Performance Summary:"; \
			cat performance-tests/results/summary.md; \
		else \
			echo "📊 Generating summary from existing results..."; \
			cd performance-tests && ./generate-summary.sh; \
		fi; \
	else \
		echo "❌ No results found. Run 'make perf-test' first."; \
	fi

# Monitoring
health:
	@echo "🏥 Application Health:"
	@curl -s http://localhost:8080/actuator/health | jq . || curl -s http://localhost:8080/actuator/health

metrics:
	@echo "📊 Application Metrics:"
	@curl -s http://localhost:8080/actuator/metrics | jq . || curl -s http://localhost:8080/actuator/metrics

# Development workflow
dev: start
	@echo "🎯 Development environment ready!"
	@echo "API Base URL: http://localhost:8080/api/wallets"
	@echo "Swagger UI: http://localhost:8080/swagger-ui/index.html"
	@echo "Health Check: http://localhost:8080/actuator/health"
	@echo ""
	@echo "Quick test:"
	@echo "curl -X PUT http://localhost:8080/api/wallets/test-wallet \\"
	@echo "  -H 'Content-Type: application/json' \\"
	@echo "  -d '{\"owner\": \"Test User\", \"initialBalance\": 1000}'"

# Production-like testing
prod-test: build start
	@echo "⏳ Waiting for application to stabilize..."
	@sleep 10
	@make perf-test
	@make stop
