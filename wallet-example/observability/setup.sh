#!/bin/bash

# Observability Stack Setup Script
# This script validates configuration and starts the observability stack

set -e

echo "🚀 Setting up Observability Stack for Java Crablet"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

# Check if Docker is running
check_docker() {
    print_info "Checking Docker..."
    if ! docker info > /dev/null 2>&1; then
        print_error "Docker is not running. Please start Docker and try again."
        exit 1
    fi
    print_status "Docker is running"
}

# Check if docker-compose is available
check_docker_compose() {
    print_info "Checking Docker Compose..."
    if ! command -v docker-compose > /dev/null 2>&1; then
        print_error "docker-compose is not installed. Please install Docker Compose and try again."
        exit 1
    fi
    print_status "Docker Compose is available"
}

# Validate configuration files
validate_config() {
    print_info "Validating configuration files..."
    
    # Check Prometheus config
    if [ ! -f "observability/prometheus/prometheus.yml" ]; then
        print_error "Prometheus configuration file not found: observability/prometheus/prometheus.yml"
        exit 1
    fi
    
    # Check Grafana datasources
    if [ ! -f "observability/grafana/datasources/datasources.yaml" ]; then
        print_error "Grafana datasources configuration not found: observability/grafana/datasources/datasources.yaml"
        exit 1
    fi
    
    # Check Loki config
    if [ ! -f "observability/loki/loki-config.yml" ]; then
        print_error "Loki configuration file not found: observability/loki/loki-config.yml"
        exit 1
    fi
    
    # Check Promtail config
    if [ ! -f "observability/promtail/promtail-config.yaml" ]; then
        print_error "Promtail configuration file not found: observability/promtail/promtail-config.yaml"
        exit 1
    fi
    
    print_status "All configuration files found"
}

# Create necessary directories
create_directories() {
    print_info "Creating necessary directories..."
    
    # Create logs directory for Promtail
    mkdir -p logs
    print_status "Created logs directory"
    
    # Create observability data directories
    mkdir -p observability/data/{prometheus,grafana,loki}
    print_status "Created data directories"
}

# Start the observability stack
start_services() {
    print_info "Starting observability services..."
    
    # Start only the observability services
    docker-compose up -d prometheus grafana loki promtail
    
    print_status "Services started"
}

# Wait for services to be healthy
wait_for_services() {
    print_info "Waiting for services to be healthy..."
    
    # Wait for Prometheus
    print_info "Waiting for Prometheus..."
    timeout=60
    while [ $timeout -gt 0 ]; do
        if curl -s http://localhost:9090/-/healthy > /dev/null 2>&1; then
            print_status "Prometheus is healthy"
            break
        fi
        sleep 2
        timeout=$((timeout - 2))
    done
    
    if [ $timeout -le 0 ]; then
        print_warning "Prometheus health check timeout"
    fi
    
    # Wait for Grafana
    print_info "Waiting for Grafana..."
    timeout=60
    while [ $timeout -gt 0 ]; do
        if curl -s http://localhost:3000/api/health > /dev/null 2>&1; then
            print_status "Grafana is healthy"
            break
        fi
        sleep 2
        timeout=$((timeout - 2))
    done
    
    if [ $timeout -le 0 ]; then
        print_warning "Grafana health check timeout"
    fi
    
    # Wait for Loki
    print_info "Waiting for Loki..."
    timeout=60
    while [ $timeout -gt 0 ]; do
        if curl -s http://localhost:3100/ready > /dev/null 2>&1; then
            print_status "Loki is healthy"
            break
        fi
        sleep 2
        timeout=$((timeout - 2))
    done
    
    if [ $timeout -le 0 ]; then
        print_warning "Loki health check timeout"
    fi
}

# Check service status
check_status() {
    print_info "Checking service status..."
    
    echo ""
    echo "Service Status:"
    echo "==============="
    docker-compose ps
    
    echo ""
    echo "Service URLs:"
    echo "============="
    echo "Grafana:    http://localhost:3000 (admin/admin)"
    echo "Prometheus: http://localhost:9090"
    echo "Loki:       http://localhost:3100"
    echo ""
    
    # Check if Spring Boot app is running
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        print_status "Spring Boot application is running"
        echo "Spring Boot: http://localhost:8080"
        echo "Metrics:     http://localhost:8080/actuator/prometheus"
    else
        print_warning "Spring Boot application is not running"
        echo "Please start the Spring Boot application to see metrics in Grafana"
    fi
}

# Main execution
main() {
    echo ""
    check_docker
    check_docker_compose
    validate_config
    create_directories
    start_services
    wait_for_services
    check_status
    
    echo ""
    print_status "Observability stack setup complete!"
    echo ""
    echo "Next steps:"
    echo "1. Start your Spring Boot application: ./mvnw spring-boot:run"
    echo "2. Open Grafana: http://localhost:3000"
    echo "3. Login with admin/admin"
    echo "4. Explore the pre-configured dashboards"
    echo ""
    echo "Available dashboards:"
    echo "- JVM & System Metrics: /d/jvm-system"
    echo "- Database & HikariCP: /d/database"
    echo "- Application & Resilience4j: /d/application"
    echo "- Business Metrics: /d/business"
    echo ""
}

# Run main function
main "$@"
