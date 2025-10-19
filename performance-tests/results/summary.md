# Performance Test Summary

Generated on: Sun Oct 19 13:36:17 -03 2025

## Test Results

### Wallet Creation Load Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 44854
- **95th percentile response time**: 34.83ms
- **Error rate**: 0.00%
- **Throughput**: 897 requests/second

### Deposit Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 16443
- **95th percentile response time**: 49.23ms
- **Error rate**: 0.00%
- **Throughput**: 329 requests/second

### Withdrawal Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 16478
- **95th percentile response time**: 47.53ms
- **Error rate**: 0.00%
- **Throughput**: 330 requests/second

### Transfer Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 15163
- **95th percentile response time**: 48.17ms
- **Error rate**: 0.00%
- **Throughput**: 303 requests/second

### History Query Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 13384
- **95th percentile response time**: 75.68ms
- **Error rate**: 0.00%
- **Throughput**: 268 requests/second

### Spike Resilience Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 13274
- **95th percentile response time**: 183.25ms
- **Error rate**: 0.00%
- **Throughput**: 265 requests/second

### Mixed Workload Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 20976
- **95th percentile response time**: 121.10ms
- **Error rate**: 0.00%
- **Throughput**: 420 requests/second

### Insufficient Balance Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 18184
- **95th percentile response time**: 39.94ms
- **Error rate**: 99.40%
- **Throughput**: 364 requests/second

### Concurrency Conflict Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 9270
- **95th percentile response time**: 449.95ms
- **Error rate**: 0.00%
- **Throughput**: 185 requests/second


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

