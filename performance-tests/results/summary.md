# Performance Test Summary

Generated on: Tue Oct 21 14:34:12 -03 2025

## Test Results

### Wallet Creation Load Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 2698
- **95th percentile response time**: 499.35ms
- **Error rate**: 0.00%
- **Throughput**: 54 requests/second

### Deposit Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 4519
- **95th percentile response time**: 145.57ms
- **Error rate**: 0.00%
- **Throughput**: 90 requests/second

### Withdrawal Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 4784
- **95th percentile response time**: 136.81ms
- **Error rate**: 0.02%
- **Throughput**: 96 requests/second

### Transfer Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 3862
- **95th percentile response time**: 164.38ms
- **Error rate**: 0.07%
- **Throughput**: 77 requests/second

### History Query Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 52294
- **95th percentile response time**: 56.72ms
- **Error rate**: 0.00%
- **Throughput**: 1046 requests/second

### Spike Resilience Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 3754
- **95th percentile response time**: 707.62ms
- **Error rate**: 0.00%
- **Throughput**: 75 requests/second

### Mixed Workload Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 5653
- **95th percentile response time**: 435.10ms
- **Error rate**: 0.00%
- **Throughput**: 113 requests/second

### Insufficient Balance Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 27804
- **95th percentile response time**: 29.16ms
- **Error rate**: 100.00%
- **Throughput**: 556 requests/second

### Concurrency Conflict Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 3285
- **95th percentile response time**: 1211.93ms
- **Error rate**: 0.00%
- **Throughput**: 66 requests/second


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

