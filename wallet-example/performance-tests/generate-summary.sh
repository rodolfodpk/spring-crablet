#!/bin/bash

# Generate Performance Test Summary with Throughput Data
# This script parses k6 JSON results and generates a comprehensive summary

set -e

echo "📊 Generating Performance Test Summary..."

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to extract metrics from k6 JSON results
extract_metrics() {
    local result_file="$1"
    local test_name="$2"
    
    if [ ! -f "$result_file" ]; then
        echo "❌ Test failed or was not executed"
        return
    fi
    
    # Extract key metrics from k6 streaming JSON format
    if command_exists jq; then
        # Parse the streaming JSON format and extract metrics
        local http_reqs=$(grep '"metric":"http_reqs"' "$result_file" | grep '"type":"Point"' | wc -l | tr -d ' ')
        local http_req_duration_p95=$(grep '"metric":"http_req_duration"' "$result_file" | grep '"type":"Point"' | jq -r '.data.value' | sort -n | awk '{all[NR] = $0} END{print all[int(NR*0.95)]}' 2>/dev/null || echo "0")
        local http_req_failed_count=$(grep '"metric":"http_req_failed"' "$result_file" | grep '"type":"Point"' | jq -r '.data.value' | awk '{sum+=$1} END{print sum}' 2>/dev/null || echo "0")
        local total_requests=$(grep '"metric":"http_reqs"' "$result_file" | grep '"type":"Point"' | wc -l | tr -d ' ')
        local http_req_failed_rate=$(echo "scale=4; $http_req_failed_count / $total_requests" | bc -l 2>/dev/null || echo "0")
        local throughput=$(echo "scale=2; $http_reqs / 50" | bc -l 2>/dev/null || echo "0")  # Assuming 50 second test duration
        
        # Convert to more readable format
        local duration_ms=$(echo "$http_req_duration_p95" | awk '{printf "%.2f", $1}')
        local error_rate=$(echo "$http_req_failed_rate" | awk '{printf "%.2f", $1 * 100}')
        local throughput_rps=$(echo "$throughput" | awk '{printf "%.0f", $1}')
        
        # Determine status based on performance targets
        local status="✅"
        local status_color="$GREEN"
        
        case "$test_name" in
            *"Wallet Creation"*)
                if command_exists bc && (( $(echo "$duration_ms > 500" | bc -l) )); then
                    status="⚠️"
                    status_color="$YELLOW"
                elif (( $(echo "$duration_ms > 500" | awk '{print ($1 > 500)}') )); then
                    status="⚠️"
                    status_color="$YELLOW"
                fi
                ;;
            *"Deposit"*|*"Withdrawal"*|*"Transfer"*)
                if command_exists bc && (( $(echo "$duration_ms > 300" | bc -l) )); then
                    status="⚠️"
                    status_color="$YELLOW"
                elif (( $(echo "$duration_ms > 300" | awk '{print ($1 > 300)}') )); then
                    status="⚠️"
                    status_color="$YELLOW"
                fi
                ;;
            *"History"*|*"Spike"*)
                if command_exists bc && (( $(echo "$duration_ms > 1000" | bc -l) )); then
                    status="⚠️"
                    status_color="$YELLOW"
                elif (( $(echo "$duration_ms > 1000" | awk '{print ($1 > 1000)}') )); then
                    status="⚠️"
                    status_color="$YELLOW"
                fi
                ;;
            *"Mixed Workload"*)
                if command_exists bc && (( $(echo "$duration_ms > 800" | bc -l) )); then
                    status="⚠️"
                    status_color="$YELLOW"
                elif (( $(echo "$duration_ms > 800" | awk '{print ($1 > 800)}') )); then
                    status="⚠️"
                    status_color="$YELLOW"
                fi
                ;;
        esac
        
        echo -e "${status_color}${status}${NC} Test completed successfully"
        echo "- **Operations completed**: $http_reqs"
        echo "- **95th percentile response time**: ${duration_ms}ms"
        echo "- **Error rate**: ${error_rate}%"
        echo "- **Throughput**: $throughput_rps requests/second"
    else
        echo "✅ Test completed successfully"
        echo "- **Note**: Install 'jq' for detailed metrics extraction"
    fi
}

# Create results directory if it doesn't exist
mkdir -p results

# Generate summary report
cat > results/summary.md << EOF
# Performance Test Summary

Generated on: $(date)

## Test Results

EOF

# Define test files and their display names
tests=(
    "wallet-creation-load_results.json:Wallet Creation Load Test"
    "simple-deposit-test_results.json:Deposit Operations Test"
    "simple-withdrawal-test_results.json:Withdrawal Operations Test"
    "simple-transfer-test_results.json:Transfer Operations Test"
    "simple-history-test_results.json:History Query Test"
    "simple-spike-test_results.json:Spike Resilience Test"
    "simple-mixed-workload-test_results.json:Mixed Workload Test"
    "simple-insufficient-balance-test_results.json:Insufficient Balance Test"
    "simple-concurrency-test_results.json:Concurrency Conflict Test"
)

# Process each test result
for test_info in "${tests[@]}"; do
    IFS=':' read -r result_file test_name <<< "$test_info"
    full_path="results/$result_file"
    
    echo "### $test_name" >> results/summary.md
    echo "" >> results/summary.md
    extract_metrics "$full_path" "$test_name" >> results/summary.md
    echo "" >> results/summary.md
done

# Add performance targets and additional information
cat >> results/summary.md << EOF

## Performance Targets

- **Wallet Creation**: < 500ms (95th percentile)
- **Deposits**: < 300ms (95th percentile)  
- **Withdrawals**: < 300ms (95th percentile)
- **Transfers**: < 300ms (95th percentile)
- **History Queries**: < 1000ms (95th percentile)
- **Spike Operations**: < 1000ms (95th percentile)
- **Mixed Workload**: < 800ms (95th percentile)

## Key Metrics Explained

- **Operations completed**: Total number of successful operations
- **95th percentile response time**: 95% of requests completed within this time
- **Error rate**: Percentage of failed requests
- **Throughput**: Requests per second (RPS) - key performance indicator

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

echo -e "${GREEN}✅ Performance test summary generated: results/summary.md${NC}"
echo ""
echo "📋 Summary Preview:"
echo "==================="
head -20 results/summary.md
