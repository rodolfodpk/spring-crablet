# HTTP/2 Configuration

## Overview

HTTP/2 is enabled to improve performance through multiplexing, header compression, and reduced connection overhead.

## Configuration

```properties
# Enable HTTP/2
server.http2.enabled=true
```

## Benefits

### 1. Multiplexing
- Multiple requests over a single TCP connection
- Reduces connection overhead
- Better for concurrent requests
- Works well with Virtual Threads

### 2. Header Compression (HPACK)
- Compresses HTTP headers
- Reduces bandwidth usage
- Especially beneficial for REST APIs with repeated headers

### 3. Binary Protocol
- More efficient than HTTP/1.1 text-based protocol
- Lower parsing overhead
- Slightly reduced latency

### 4. Reduced Latency
- Eliminates head-of-line blocking
- Better connection utilization
- Improved throughput

## Actual Performance Impact

### Baseline (Before Optimizations)
```
Wallet Creation: 549 RPS, 47.82ms p95
Deposits: 194 RPS, 66.15ms p95
Transfers: 171 RPS, 73.03ms p95
```

### After Performance Optimizations (October 2025)
```
Wallet Creation: 887 RPS (+61%), 35ms p95 (-27%)
Deposits: 453 RPS (+133%), 36ms p95 (-46%)
Transfers: 298 RPS (+74%), 52ms p95 (-29%)
```

**Key Optimizations Applied**:
- Prepared statement caching (HikariCP)
- Composite database indexes
- Code optimizations (tag parsing, StringBuilder, singleton RowMapper)
- Single-pass tag filtering in projections

### HTTP/2 Impact

**Status**: HTTP/2 was enabled **after** the performance optimizations were applied.

**Expected Benefits**:
- 5-10% additional throughput improvement
- Better multiplexing for concurrent requests
- Reduced connection overhead
- Modern protocol support

**Note**: HTTP/2 benefits were not measured separately as it was enabled alongside other optimizations. The protocol is still valuable for modern client support, multiplexing capabilities, and future-proofing the application.

## Testing HTTP/2

### Verify HTTP/2 is Active

```bash
# Start the application
make start

# Check protocol version
curl -I --http2 http://localhost:8080/actuator/health

# Should show:
# HTTP/2 200
```

### k6 Testing

k6 automatically uses HTTP/2 when available:

```javascript
import http from 'k6/http';

export default function() {
    const res = http.post(`${BASE_URL}/api/wallets/${walletId}/deposit`, payload);
    
    check(res, {
        'status is 200': (r) => r.status === 200,
        'using HTTP/2': (r) => r.proto === 'HTTP/2.0',
    });
}
```

### Monitor Metrics

```bash
# Check HTTP/2 connections
curl http://localhost:8080/actuator/metrics/http.server.requests | grep http2
```

## Production Considerations

### TLS Requirement

For production environments, HTTP/2 requires TLS:

```properties
# Enable HTTP/2 with TLS
server.http2.enabled=true
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
```

### Load Balancer Compatibility

Ensure your load balancer supports HTTP/2:
- **AWS ALB**: ✅ Supports HTTP/2
- **AWS NLB**: ✅ Supports HTTP/2
- **Nginx**: ✅ Supports HTTP/2
- **HAProxy**: ✅ Supports HTTP/2 (requires configuration)

### Client Compatibility

HTTP/2 is supported by:
- Modern browsers (Chrome, Firefox, Safari, Edge)
- curl (7.47.0+)
- k6
- Postman
- Most HTTP clients

## Comparison with HTTP/1.1

| Feature | HTTP/1.1 | HTTP/2 |
|---------|----------|--------|
| Multiplexing | ❌ No | ✅ Yes |
| Header Compression | ❌ No | ✅ HPACK |
| Binary Protocol | ❌ No | ✅ Yes |
| Server Push | ❌ No | ✅ Yes (not used in REST) |
| Connection Overhead | High | Low |
| Latency | Higher | Lower |

## Troubleshooting

### HTTP/2 Not Working

1. **Check Undertow version**: Requires Undertow 2.0+
2. **Verify configuration**: `server.http2.enabled=true`
3. **Check logs**: Look for HTTP/2 initialization messages
4. **Test with curl**: `curl -I --http2 http://localhost:8080/actuator/health`

### Performance Not Improved

1. **Single connection**: HTTP/2 benefits increase with multiple concurrent requests
2. **Test with higher concurrency**: Use k6 with multiple VUs
3. **Check network**: HTTP/2 benefits are more noticeable on slower networks
4. **Monitor metrics**: Compare before/after metrics

## References

- [HTTP/2 Specification](https://http2.github.io/)
- [Spring Boot HTTP/2 Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.enable-http2)
- [Undertow HTTP/2 Support](https://undertow.io/)

