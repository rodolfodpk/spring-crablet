# Performance Test Summary

Generated on: Sat Oct 18 21:22:36 -03 2025

## Test Results

### Wallet Creation Load Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 41299
- **95th percentile response time**: 36.89ms
- **Error rate**: 0.00%
- **Throughput**: 826 requests/second

### Deposit Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 14562
- **95th percentile response time**: 59.49ms
- **Error rate**: 0.00%
- **Throughput**: 291 requests/second

### Withdrawal Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 10579
- **95th percentile response time**: 64.69ms
- **Error rate**: 0.00%
- **Throughput**: 212 requests/second

### Transfer Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 6587
- **95th percentile response time**: 138.02ms
- **Error rate**: 0.03%
- **Throughput**: 132 requests/second

### History Query Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 13452
- **95th percentile response time**: 85.89ms
- **Error rate**: 0.00%
- **Throughput**: 269 requests/second

### Spike Resilience Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 7763
- **95th percentile response time**: 282.74ms
- **Error rate**: 0.00%
- **Throughput**: 155 requests/second

### Mixed Workload Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 17066
- **95th percentile response time**: 145.38ms
- **Error rate**: 0.00%
- **Throughput**: 341 requests/second

### Insufficient Balance Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 9054
- **95th percentile response time**: 81.83ms
- **Error rate**: 100.00%
- **Throughput**: 181 requests/second

### Concurrency Conflict Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 3459
- **95th percentile response time**: 1067.72ms
- **Error rate**: 0.00%
- **Throughput**: 69 requests/second


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

