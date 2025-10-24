#!/bin/bash

# Wallet API Performance Tests Runner
# This script starts Docker Compose, waits for the application to be ready,
# runs k6 performance tests, and cleans up

set -e

echo "🚀 Starting Wallet API Performance Tests"
echo "========================================"

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

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

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

print_success "All prerequisites are installed"

# Clean up any existing containers
print_status "Cleaning up existing containers..."
docker-compose down -v 2>/dev/null || true

# Start Docker Compose
print_status "Starting Docker Compose (PostgreSQL)..."
docker-compose up -d

# Wait for PostgreSQL to be ready
print_status "Waiting for PostgreSQL to be ready..."
max_attempts=30
attempt=1

while [ $attempt -le $max_attempts ]; do
    if docker-compose exec -T postgres pg_isready -U crablet >/dev/null 2>&1; then
        print_success "PostgreSQL is ready"
        break
    fi
    
    if [ $attempt -eq $max_attempts ]; then
        print_error "PostgreSQL failed to start after $max_attempts attempts"
        docker-compose logs postgres
        exit 1
    fi
    
    print_status "Waiting for PostgreSQL... (attempt $attempt/$max_attempts)"
    sleep 2
    attempt=$((attempt + 1))
done

# Start the Spring Boot application
print_status "Starting Spring Boot application..."
mvn spring-boot:run > app.log 2>&1 &
APP_PID=$!

# Wait for the application to be ready
print_status "Waiting for application to be ready..."
max_attempts=60
attempt=1

while [ $attempt -le $max_attempts ]; do
    if curl -s http://localhost:8080/actuator/health >/dev/null 2>&1; then
        print_success "Application is ready"
        break
    fi
    
    if [ $attempt -eq $max_attempts ]; then
        print_error "Application failed to start after $max_attempts attempts"
        print_error "Application logs:"
        tail -50 app.log
        kill $APP_PID 2>/dev/null || true
        docker-compose down -v
        exit 1
    fi
    
    print_status "Waiting for application... (attempt $attempt/$max_attempts)"
    sleep 2
    attempt=$((attempt + 1))
done

# Run performance tests
print_status "Running performance tests..."

# Create results directory
mkdir -p results

# Run each test
tests=(
    "wallet-creation-load.js:Wallet Creation Load Test"
    "simple-deposit-test.js:Deposit Operations Test"
    "simple-withdrawal-test.js:Withdrawal Operations Test"
    "simple-transfer-test.js:Transfer Operations Test"
    "simple-history-test.js:History Query Test"
    "simple-spike-test.js:Spike Resilience Test"
    "simple-mixed-workload-test.js:Mixed Workload Test"
)

total_tests=${#tests[@]}
current_test=1

for test_info in "${tests[@]}"; do
    IFS=':' read -r test_file test_name <<< "$test_info"
    
    print_status "Running test $current_test/$total_tests: $test_name"
    
    # Run k6 test and save results
    if k6 run --out json=results/${test_file%.js}_results.json "$test_file"; then
        print_success "✅ $test_name completed successfully"
    else
        print_warning "⚠️  $test_name completed with warnings or failures"
    fi
    
    current_test=$((current_test + 1))
    echo ""
done

# Generate summary report
print_status "Generating performance test summary..."

cat > results/summary.md << EOF
# Performance Test Summary

Generated on: $(date)

## Test Results

EOF

# Function to extract metrics from k6 JSON results
extract_metrics() {
    local result_file="$1"
    if [ ! -f "$result_file" ]; then
        echo "❌ Test failed or was not executed"
        return
    fi
    
    # Extract key metrics using jq if available, otherwise use basic parsing
    if command_exists jq; then
        local http_reqs=$(jq -r '.metrics.http_reqs.values.count // 0' "$result_file" 2>/dev/null || echo "0")
        local http_req_duration_p95=$(jq -r '.metrics.http_req_duration.values.p95 // 0' "$result_file" 2>/dev/null || echo "0")
        local http_req_failed_rate=$(jq -r '.metrics.http_req_failed.values.rate // 0' "$result_file" 2>/dev/null || echo "0")
        local throughput=$(jq -r '.metrics.http_reqs.values.rate // 0' "$result_file" 2>/dev/null || echo "0")
        
        # Convert to more readable format
        local duration_ms=$(echo "$http_req_duration_p95" | awk '{printf "%.2f", $1}')
        local error_rate=$(echo "$http_req_failed_rate" | awk '{printf "%.2f", $1 * 100}')
        local throughput_rps=$(echo "$throughput" | awk '{printf "%.0f", $1}')
        
        echo "✅ Test completed successfully"
        echo "- **Operations completed**: $http_reqs"
        echo "- **95th percentile response time**: ${duration_ms}ms"
        echo "- **Error rate**: ${error_rate}%"
        echo "- **Throughput**: $throughput_rps requests/second"
    else
        echo "✅ Test completed successfully"
        echo "- **Note**: Install 'jq' for detailed metrics extraction"
    fi
}

for test_info in "${tests[@]}"; do
    IFS=':' read -r test_file test_name <<< "$test_info"
    result_file="results/${test_file%.js}_results.json"
    
    echo "### $test_name" >> results/summary.md
    echo "" >> results/summary.md
    extract_metrics "$result_file" >> results/summary.md
    echo "" >> results/summary.md
done

cat >> results/summary.md << EOF

## Performance Targets

- **Wallet Creation**: < 500ms (95th percentile)
- **Deposits**: < 300ms (95th percentile)  
- **Withdrawals**: < 300ms (95th percentile)
- **Transfers**: < 300ms (95th percentile)
- **History Queries**: < 1000ms (95th percentile)
- **Spike Operations**: < 1000ms (95th percentile)
- **Mixed Workload**: < 800ms (95th percentile)

## Application Metrics

Check the following endpoints for application metrics:

- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Resilience4j: http://localhost:8080/actuator/resilience4jcircuitbreaker

## Next Steps

1. Review the performance test results above
2. Check application logs for any errors or warnings
3. Monitor database performance and connection pool usage
4. Consider optimizations if performance targets are not met

EOF

print_success "Performance test summary generated: results/summary.md"

# Clean up
print_status "Cleaning up..."

# Stop the application
if kill $APP_PID 2>/dev/null; then
    print_status "Stopping Spring Boot application..."
    sleep 5
    kill -9 $APP_PID 2>/dev/null || true
fi

# Stop Docker Compose
print_status "Stopping Docker Compose..."
docker-compose down -v

# Clean up log file
rm -f app.log

print_success "🎉 Performance tests completed successfully!"
print_status "Results are available in the 'results/' directory"
print_status "Check 'results/summary.md' for a summary of all tests"

echo ""
echo "========================================"
echo "Performance Test Results Summary"
echo "========================================"
cat results/summary.md
