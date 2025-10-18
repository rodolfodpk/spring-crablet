# Performance Test Summary

Generated on: Sat Oct 18 15:38:40 -03 2025

## Test Results

### Wallet Creation Load Test

❌ Test failed or was not executed

### Deposit Operations Test

❌ Test failed or was not executed

### Withdrawal Operations Test

❌ Test failed or was not executed

### Transfer Operations Test

❌ Test failed or was not executed

### History Query Test

❌ Test failed or was not executed

### Spike Resilience Test

❌ Test failed or was not executed

### Mixed Workload Test

❌ Test failed or was not executed


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

