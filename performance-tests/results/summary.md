# Performance Test Summary

Generated on: Sat Oct 18 15:50:36 -03 2025

## Test Results

### Wallet Creation Load Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 34261
- **95th percentile response time**: 44.88ms
- **Error rate**: 0.00%
- **Throughput**: 685 requests/second

### Deposit Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 19106
- **95th percentile response time**: 42.21ms
- **Error rate**: 0.00%
- **Throughput**: 382 requests/second

### Withdrawal Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 19812
- **95th percentile response time**: 37.43ms
- **Error rate**: 0.00%
- **Throughput**: 396 requests/second

### Transfer Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 14612
- **95th percentile response time**: 49.03ms
- **Error rate**: 0.00%
- **Throughput**: 292 requests/second

### History Query Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 30113
- **95th percentile response time**: 34.17ms
- **Error rate**: 0.00%
- **Throughput**: 602 requests/second

### Spike Resilience Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 14850
- **95th percentile response time**: 170.97ms
- **Error rate**: 0.00%
- **Throughput**: 297 requests/second

### Mixed Workload Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 20199
- **95th percentile response time**: 136.76ms
- **Error rate**: 0.00%
- **Throughput**: 404 requests/second

### Insufficient Balance Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 39054
- **95th percentile response time**: 29.32ms
- **Error rate**: 99.71%
- **Throughput**: 781 requests/second

### Concurrency Conflict Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 10096
- **95th percentile response time**: 417.36ms
- **Error rate**: 0.00%
- **Throughput**: 202 requests/second


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

