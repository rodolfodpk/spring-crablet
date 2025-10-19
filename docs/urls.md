# Available URLs

Quick reference for all service URLs when running the application.

## Application URLs

### API Endpoints
- **Base URL**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

See [API Reference](api/README.md) for complete endpoint documentation.

### Management Endpoints (Spring Actuator)
- **Health Check**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics
- **Prometheus**: http://localhost:8080/actuator/prometheus
- **Info**: http://localhost:8080/actuator/info

### API Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/api/wallets/{walletId}` | Create wallet |
| POST | `/api/wallets/{walletId}/deposit` | Deposit money |
| POST | `/api/wallets/{walletId}/withdraw` | Withdraw money |
| POST | `/api/wallets/transfer` | Transfer between wallets |
| GET | `/api/wallets/{walletId}` | Get wallet state |
| GET | `/api/wallets/{walletId}/events` | Get event history |
| GET | `/api/wallets/{walletId}/commands` | Get command history |

## Observability Stack URLs

### Grafana (Dashboards & Visualization)
- **URL**: http://localhost:3000
- **Credentials**: admin/admin (default)
- **Dashboards**:
  - JVM & System: http://localhost:3000/d/jvm-system
  - Database: http://localhost:3000/d/database
  - Application: http://localhost:3000/d/application
  - Business: http://localhost:3000/d/business

### Prometheus (Metrics)
- **URL**: http://localhost:9090
- **Targets**: http://localhost:9090/targets
- **Graph**: http://localhost:9090/graph

### Loki (Logs)
- **URL**: http://localhost:3100
- **Health**: http://localhost:3100/ready

## Database
- **PostgreSQL**: localhost:5432
- **Database**: crablet
- **Username**: crablet
- **Password**: crablet

## Quick Commands

```bash
# Check if application is running
curl http://localhost:8080/actuator/health

# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Check Grafana health
curl http://localhost:3000/api/health
```

## Related Documentation
- [API Reference](api/README.md) - Complete API documentation
- [Observability](observability/README.md) - Monitoring setup
- [Setup](setup/README.md) - Installation guide

