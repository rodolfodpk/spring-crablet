# Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the wallet microservices.

## Architecture

The deployment consists of:
- **PostgreSQL** - Shared database for both services
- **wallet-eventstore-service** - Business API service (port 8080)
- **wallet-outbox-service** - Event publishing service (port 8081)

## Prerequisites

- Kubernetes cluster (minikube, kind, or cloud provider)
- kubectl configured to access your cluster
- Docker images built and available in your cluster

## Deployment Steps

### 1. Build Docker Images

```bash
# Build EventStore service
cd wallet-eventstore-service
docker build -t wallet-eventstore-service:latest .

# Build Outbox service
cd wallet-outbox-service
docker build -t wallet-outbox-service:latest .
```

### 2. Deploy to Kubernetes

```bash
# Deploy PostgreSQL
kubectl apply -f postgres-deployment.yaml

# Wait for PostgreSQL to be ready
kubectl wait --for=condition=available --timeout=300s deployment/postgres

# Deploy EventStore service
kubectl apply -f eventstore-deployment.yaml

# Deploy Outbox service
kubectl apply -f outbox-deployment.yaml

# Apply shared configuration
kubectl apply -f configmap.yaml
```

### 3. Verify Deployment

```bash
# Check all pods are running
kubectl get pods

# Check services
kubectl get services

# Check logs
kubectl logs -l app=wallet-eventstore-service
kubectl logs -l app=wallet-outbox-service
```

## Service Endpoints

### EventStore Service (Port 8080)
- `POST /api/wallets` - Open wallet
- `POST /api/wallets/{id}/deposit` - Deposit money
- `POST /api/wallets/{id}/withdraw` - Withdraw money
- `POST /api/wallets/{id}/transfer` - Transfer money
- `GET /api/wallets/{id}` - Get wallet state
- `GET /api/wallets/{id}/history` - Get wallet history

### Outbox Service (Port 8081)
- `GET /api/outbox/publishers` - List publishers
- `POST /api/outbox/publishers/{name}/pause` - Pause publisher
- `POST /api/outbox/publishers/{name}/resume` - Resume publisher
- `GET /api/outbox/metrics` - Get metrics

## Accessing Services

### Port Forwarding (for local testing)
```bash
# EventStore service
kubectl port-forward service/wallet-eventstore-service 8080:8080

# Outbox service
kubectl port-forward service/wallet-outbox-service 8081:8081
```

### Load Balancer (for production)
```bash
# Create load balancer services
kubectl expose deployment wallet-eventstore-service --type=LoadBalancer --port=8080
kubectl expose deployment wallet-outbox-service --type=LoadBalancer --port=8081
```

## Scaling

### Horizontal Scaling
```bash
# Scale EventStore service
kubectl scale deployment wallet-eventstore-service --replicas=3

# Scale Outbox service
kubectl scale deployment wallet-outbox-service --replicas=3
```

### Vertical Scaling
Edit the deployment files to adjust resource requests and limits:
- `resources.requests.memory` - Minimum memory
- `resources.limits.memory` - Maximum memory
- `resources.requests.cpu` - Minimum CPU
- `resources.limits.cpu` - Maximum CPU

## Monitoring

### Health Checks
Both services expose health endpoints:
- `GET /actuator/health` - Health status
- `GET /actuator/metrics` - Service metrics
- `GET /actuator/prometheus` - Prometheus metrics

### Logs
```bash
# Follow logs
kubectl logs -f -l app=wallet-eventstore-service
kubectl logs -f -l app=wallet-outbox-service

# Get logs from specific pod
kubectl logs <pod-name>
```

## Troubleshooting

### Common Issues

1. **Pods not starting**
   - Check resource limits
   - Verify image availability
   - Check logs for errors

2. **Database connection issues**
   - Ensure PostgreSQL is running
   - Check database credentials
   - Verify network connectivity

3. **Service communication issues**
   - Check service names and ports
   - Verify DNS resolution
   - Check firewall rules

### Debug Commands
```bash
# Describe pod for events
kubectl describe pod <pod-name>

# Check service endpoints
kubectl get endpoints

# Check ConfigMap
kubectl describe configmap wallet-services-config

# Check secrets
kubectl get secrets
```

## Cleanup

```bash
# Delete all resources
kubectl delete -f .
```
