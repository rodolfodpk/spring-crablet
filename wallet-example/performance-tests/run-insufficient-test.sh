#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

echo "🚀 Starting Insufficient Balance Test..."

# --- Configuration ---
APP_DIR="/Users/rodolfo/Documents/github/wallets-challenge"
PERF_TESTS_DIR="${APP_DIR}/performance-tests"
K6_BIN="k6" # Assumes k6 is in PATH

# --- Functions ---
start_docker_compose() {
  echo "🐳 Starting Docker Compose services..."
  docker-compose -f "${APP_DIR}/docker-compose.yaml" up -d --build
  echo "Waiting for PostgreSQL to be ready..."
  # Wait for PostgreSQL to be ready
  until docker exec wallets-challenge-postgres-1 pg_isready -U postgres; do
    echo "PostgreSQL is unavailable - sleeping"
    sleep 2
  done
  echo "PostgreSQL is up and running!"
}

start_application() {
  echo "Starting Spring Boot application..."
  # Use mvn spring-boot:run in the background
  (cd "${APP_DIR}" && mvn spring-boot:run > /dev/null 2>&1 &)
  APP_PID=$!
  echo "Spring Boot application started with PID: ${APP_PID}"
  
  echo "Waiting for application to be healthy..."
  # Wait for the application to be healthy
  until curl -s http://localhost:8080/actuator/health | grep UP > /dev/null; do
    echo "Application is unavailable - sleeping"
    sleep 5
  done
  echo "Application is up and running!"
}

seed_data() {
  echo "🌱 Seeding insufficient balance test data..."
  "${K6_BIN}" run "${PERF_TESTS_DIR}/setup/seed-insufficient-data.js"
  echo "✅ Insufficient balance test data seeding complete."
}

run_k6_test() {
  local test_script=$1
  local test_name=$2
  echo "--- Running ${test_name} (${test_script}) ---"
  "${K6_BIN}" run "${PERF_TESTS_DIR}/${test_script}"
  echo "--- Finished ${test_name} ---"
}

cleanup() {
  echo "Performing cleanup..."
  # Kill the Spring Boot application process
  if [ -n "${APP_PID}" ]; then
    echo "Stopping Spring Boot application (PID: ${APP_PID})..."
    kill "${APP_PID}" || true # Use || true to prevent script from exiting if process already gone
    wait "${APP_PID}" 2>/dev/null || true # Wait for process to terminate
    echo "Spring Boot application stopped."
  fi
  
  # Run the cleanup script
  "${PERF_TESTS_DIR}/setup/cleanup-data.sh"
  echo "Cleanup complete."
}

# --- Main Execution ---

# Ensure cleanup runs even if script exits prematurely
trap cleanup EXIT

start_docker_compose
start_application
seed_data

# Run insufficient balance test
run_k6_test "transfer-insufficient-balance.js" "Transfer Insufficient Balance Test"

echo "🎉 Insufficient balance test completed successfully!"
