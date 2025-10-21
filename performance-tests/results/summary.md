# Performance Test Summary

Generated on: Tue Oct 21 05:09:07 -03 2025

## Test Results

### Wallet Creation Load Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 48143
- **95th percentile response time**: 31.41ms
- **Error rate**: 0.00%
- **Throughput**: 963 requests/second

### Deposit Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 17682
- **95th percentile response time**: 43.22ms
- **Error rate**: 0.00%
- **Throughput**: 354 requests/second

### Withdrawal Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 16628
- **95th percentile response time**: 44.91ms
- **Error rate**: 0.00%
- **Throughput**: 333 requests/second

### Transfer Operations Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 14173
- **95th percentile response time**: 50.93ms
- **Error rate**: 0.00%
- **Throughput**: 283 requests/second

### History Query Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 28496
- **95th percentile response time**: 196.02ms
- **Error rate**: 0.00%
- **Throughput**: 570 requests/second

### Spike Resilience Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 15998
- **95th percentile response time**: 194.78ms
- **Error rate**: 0.00%
- **Throughput**: 320 requests/second

### Mixed Workload Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 9346
- **95th percentile response time**: 258.43ms
- **Error rate**: 0.00%
- **Throughput**: 187 requests/second

### Insufficient Balance Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 35634
- **95th percentile response time**: 19.95ms
- **Error rate**: 99.70%
- **Throughput**: 713 requests/second

### Concurrency Conflict Test

[0;32m✅[0m Test completed successfully
- **Operations completed**: 10060
- **95th percentile response time**: 399.00ms
- **Error rate**: 0.00%
- **Throughput**: 201 requests/second


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

