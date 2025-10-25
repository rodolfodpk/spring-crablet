#!/bin/bash

# Wallet API Performance Tests Runner
# This script orchestrates the complete performance test suite with proper data management
# Updated with robust validation and error handling

set -e

echo "🚀 Starting Wallet API Performance Tests with Data Management"
echo "============================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Configuration
BASE_URL=${BASE_URL:-http://localhost:8080}
CLEANUP_AFTER_TEST=${CLEANUP_AFTER_TEST:-true}
KEEP_DATA=${KEEP_DATA:-false}
RESULTS_DIR="results"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PERF_TESTS_DIR="$PROJECT_ROOT/performance-tests"

# Global variables for cleanup
APP_PID=""
FAILED_TESTS=()

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to check if a port is free
port_is_free() {
    local port=$1
    ! lsof -i :$port >/dev/null 2>&1
}

# Function to wait for port to be free
wait_for_port_free() {
    local port=$1
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if port_is_free $port; then
            print_success "Port $port is free"
            return 0
        fi
        
        if [ $attempt -eq $max_attempts ]; then
            print_error "Port $port is still in use after $max_attempts attempts"
            return 1
        fi
        
        print_status "Waiting for port $port to be free... (attempt $attempt/$max_attempts)"
        sleep 2
        attempt=$((attempt + 1))
    done
}

# Function to parse JSON and extract status
parse_health_status() {
    local json_response="$1"
    echo "$json_response" | grep -o '"status":"[^"]*"' | cut -d'"' -f4
}

# Function to cleanup on exit
cleanup() {
    print_status "Performing cleanup..."
    
    # Kill Spring Boot application if running
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        print_status "Stopping Spring Boot application (PID: $APP_PID)..."
        kill -TERM "$APP_PID" 2>/dev/null || true
        sleep 5
        if kill -0 "$APP_PID" 2>/dev/null; then
            print_warning "Application didn't stop gracefully, forcing kill..."
            kill -9 "$APP_PID" 2>/dev/null || true
        fi
        print_success "Spring Boot application stopped"
    fi
    
    # Stop Docker Compose
    print_status "Stopping Docker Compose..."
    cd "$PROJECT_ROOT"
    docker-compose down -v 2>/dev/null || true
    
    # Clean up log file
    rm -f "$PROJECT_ROOT/app.log"
    
    print_success "Cleanup completed"
}

# Set trap to ensure cleanup runs on exit
trap cleanup EXIT

# Check prerequisites
print_status "Checking prerequisites..."

if ! command_exists docker; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

if ! command_exists docker-compose; then
    print_error "Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

if ! command_exists k6; then
    print_error "k6 is not installed. Please install k6 first."
    exit 1
fi

if ! command_exists mvn; then
    print_error "Maven is not installed. Please install Maven first."
    exit 1
fi

if ! command_exists lsof; then
    print_error "lsof is not installed. Please install lsof first."
    exit 1
fi

print_success "All prerequisites are installed"

# Phase 1: Complete Cleanup
print_status "Phase 1: Complete cleanup..."
cd "$PROJECT_ROOT"

# Stop Docker Compose and remove volumes
print_status "Stopping Docker Compose and removing volumes..."
docker-compose down -v 2>/dev/null || true

# Kill any running Spring Boot processes
print_status "Killing any running Spring Boot processes..."
pkill -f "spring-boot:run" 2>/dev/null || true

# Wait for ports to be free
print_status "Waiting for ports to be free..."
wait_for_port_free 5432
wait_for_port_free 8080

# Remove stale log files
rm -f "$PROJECT_ROOT/app.log"

print_success "Cleanup phase completed"

# Phase 2: PostgreSQL Setup and Validation
print_status "Phase 2: PostgreSQL setup and validation..."

# Start Docker Compose
print_status "Starting Docker Compose (PostgreSQL)..."
docker-compose up -d

# Wait for container to be healthy
print_status "Waiting for PostgreSQL container to be healthy..."
max_attempts=30
attempt=1

while [ $attempt -le $max_attempts ]; do
    if docker-compose ps postgres | grep -q "healthy"; then
        print_success "PostgreSQL container is healthy"
        break
    fi
    
    if [ $attempt -eq $max_attempts ]; then
        print_error "PostgreSQL container failed to become healthy after $max_attempts attempts"
        docker-compose logs postgres
        exit 1
    fi
    
    print_status "Waiting for PostgreSQL container health... (attempt $attempt/$max_attempts)"
    sleep 2
    attempt=$((attempt + 1))
done

# Validate PostgreSQL connection with actual query
print_status "Validating PostgreSQL connection..."
max_attempts=30
attempt=1

while [ $attempt -le $max_attempts ]; do
    if docker-compose exec -T postgres psql -U crablet -d crablet -c "SELECT 1;" >/dev/null 2>&1; then
        print_success "PostgreSQL connection validated successfully"
        break
    fi
    
    if [ $attempt -eq $max_attempts ]; then
        print_error "PostgreSQL connection validation failed after $max_attempts attempts"
        docker-compose logs postgres
        exit 1
    fi
    
    print_status "Validating PostgreSQL connection... (attempt $attempt/$max_attempts)"
    sleep 2
    attempt=$((attempt + 1))
done

print_success "PostgreSQL phase completed"

# Phase 3: Application Startup and Validation
print_status "Phase 3: Application startup and validation..."

# Start the Spring Boot application
print_status "Starting Spring Boot application..."
mvn spring-boot:run > app.log 2>&1 &
APP_PID=$!
print_status "Spring Boot application started with PID: $APP_PID"

# Wait for the application to be ready with proper health check
print_status "Waiting for application to be ready..."
max_attempts=60
attempt=1

while [ $attempt -le $max_attempts ]; do
    # Get health response
    health_response=$(curl -s "$BASE_URL/actuator/health" 2>/dev/null || echo "")
    
    if [ -n "$health_response" ]; then
        # Parse the status from JSON response
        status=$(parse_health_status "$health_response")
        
        if [ "$status" = "UP" ]; then
            print_success "Application is ready and healthy (status: $status)"
            break
        else
            print_status "Application responding but not healthy (status: $status)..."
        fi
    fi
    
    if [ $attempt -eq $max_attempts ]; then
        print_error "Application failed to start after $max_attempts attempts"
        print_error "Last health response: $health_response"
        print_error "Application logs (last 100 lines):"
        tail -100 app.log
        exit 1
    fi
    
    print_status "Waiting for application to be ready... (attempt $attempt/$max_attempts)"
    sleep 2
    attempt=$((attempt + 1))
done

print_success "Application phase completed"

# Phase 4: Test Execution
print_status "Phase 4: Test execution..."

# Create results directory
cd "$PERF_TESTS_DIR"
mkdir -p "$RESULTS_DIR"

# Seed test data
print_status "Seeding test data (creating wallet pool)..."
if k6 run --out json="$RESULTS_DIR/seed-data_${TIMESTAMP}.json" setup/seed-success-data.js; then
    print_success "✅ Test data seeded successfully"
else
    print_error "❌ Failed to seed test data"
    exit 1
fi

# Define tests with their descriptions
tests=(
    "wallet-creation-load.js:Wallet Creation Load Test"
    "simple-deposit-test.js:Deposit Performance Test"
    "simple-withdrawal-test.js:Withdrawal Performance Test"
    "simple-transfer-test.js:Transfer Performance Test"
    "simple-mixed-workload-test.js:Mixed Workload Performance Test"
    "simple-history-test.js:History Query Performance Test"
    "simple-spike-test.js:Spike Performance Test"
    "simple-insufficient-balance-test.js:Insufficient Balance Test"
    "simple-concurrency-test.js:Concurrency Performance Test"
)

total_tests=${#tests[@]}
current_test=1

for test_info in "${tests[@]}"; do
    IFS=':' read -r test_file test_name <<< "$test_info"
    
    print_status "Running test $current_test/$total_tests: $test_name"
    
    # Run k6 test and save results
    if k6 run --out json="$RESULTS_DIR/${test_file%.js}_${TIMESTAMP}.json" "$test_file"; then
        print_success "✅ $test_name completed successfully"
    else
        print_warning "⚠️  $test_name completed with warnings or failures"
        FAILED_TESTS+=("$test_name")
    fi
    
    current_test=$((current_test + 1))
    echo ""
done

print_success "Test execution phase completed"

# Phase 5: Report Generation
print_status "Phase 5: Report generation..."

cat > "$RESULTS_DIR/summary_${TIMESTAMP}.md" << EOF
# Performance Test Summary

Generated on: $(date)
Test Run ID: $TIMESTAMP

## Test Results

EOF

for test_info in "${tests[@]}"; do
    IFS=':' read -r test_file test_name <<< "$test_info"
    result_file="$RESULTS_DIR/${test_file%.js}_${TIMESTAMP}.json"
    
    if [ -f "$result_file" ]; then
        echo "### $test_name" >> "$RESULTS_DIR/summary_${TIMESTAMP}.md"
        echo "" >> "$RESULTS_DIR/summary_${TIMESTAMP}.md"
        echo "✅ Test completed successfully" >> "$RESULTS_DIR/summary_${TIMESTAMP}.md"
        echo "" >> "$RESULTS_DIR/summary_${TIMESTAMP}.md"
    else
        echo "### $test_name" >> "$RESULTS_DIR/summary_${TIMESTAMP}.md"
        echo "" >> "$RESULTS_DIR/summary_${TIMESTAMP}.md"
        echo "❌ Test failed or was not executed" >> "$RESULTS_DIR/summary_${TIMESTAMP}.md"
        echo "" >> "$RESULTS_DIR/summary_${TIMESTAMP}.md"
    fi
done

cat >> "$RESULTS_DIR/summary_${TIMESTAMP}.md" << EOF

## Test Coverage

- **Wallet Creation**: Load testing with concurrent users
- **Deposits**: Performance testing of deposit operations
- **Withdrawals**: Performance testing of withdrawal operations
- **Transfers**: Success, insufficient balance, and concurrency conflict scenarios
- **Mixed Workload**: Realistic user behavior simulation
- **History Queries**: Pagination and query performance
- **Spike Testing**: System resilience under load spikes

## Test Data Management

- **Wallet Pool**: 100 pre-seeded wallets (perf-wallet-001 to perf-wallet-100)
- **Initial Balance Range**: 500-10000
- **Partitioning**: VUs operate on different wallet subsets to avoid conflicts
- **Cleanup**: $(if [ "$CLEANUP_AFTER_TEST" = "true" ]; then echo "Enabled"; else echo "Disabled"; fi)

## Application Metrics

Check the following endpoints for application metrics:

- Health: $BASE_URL/actuator/health
- Metrics: $BASE_URL/actuator/metrics
- Resilience4j: $BASE_URL/actuator/resilience4jcircuitbreaker

## Files Generated

- Seed data: $RESULTS_DIR/seed-data_${TIMESTAMP}.json
- Test results: $RESULTS_DIR/*_${TIMESTAMP}.json
- Summary: $RESULTS_DIR/summary_${TIMESTAMP}.md

## Next Steps

1. Review the performance test results
2. Check application logs for any errors or warnings
3. Monitor database performance and connection pool usage
4. Consider optimizations if performance targets are not met

EOF

print_success "Performance test summary generated: $RESULTS_DIR/summary_${TIMESTAMP}.md"

# Clean up test data if requested
if [ "$CLEANUP_AFTER_TEST" = "true" ] && [ "$KEEP_DATA" != "true" ]; then
    print_status "Cleaning up test data..."
    if ./setup/cleanup-data.sh; then
        print_success "✅ Test data cleaned up successfully"
    else
        print_warning "⚠️  Test data cleanup failed (data may remain)"
    fi
else
    print_status "Test data cleanup skipped (KEEP_DATA=$KEEP_DATA, CLEANUP_AFTER_TEST=$CLEANUP_AFTER_TEST)"
fi

# Final summary
if [ ${#FAILED_TESTS[@]} -eq 0 ]; then
    print_success "🎉 All performance tests completed successfully!"
else
    print_warning "⚠️  Performance tests completed with ${#FAILED_TESTS[@]} failures:"
    for test in "${FAILED_TESTS[@]}"; do
        echo "  - $test"
    done
fi

print_status "Results are available in the '$RESULTS_DIR/' directory"
print_status "Check '$RESULTS_DIR/summary_${TIMESTAMP}.md' for a summary of all tests"

echo ""
echo "============================================================="
echo "Performance Test Results Summary"
echo "============================================================="
cat "$RESULTS_DIR/summary_${TIMESTAMP}.md"
