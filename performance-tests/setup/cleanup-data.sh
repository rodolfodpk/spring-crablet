#!/bin/bash

# Cleanup script for performance test data
# Removes all events and commands related to performance test wallets

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-crablet}
DB_USER=${DB_USER:-crablet}
DB_PASSWORD=${DB_PASSWORD:-crablet}
WALLET_PREFIX=${WALLET_PREFIX:-perf-wallet-}
KEEP_DATA=${KEEP_DATA:-false}

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if cleanup should be skipped
if [ "$KEEP_DATA" = "true" ]; then
    print_warning "KEEP_DATA=true, skipping cleanup"
    exit 0
fi

print_status "Starting cleanup of performance test data..."

# Function to execute SQL command
execute_sql() {
    local sql="$1"
    local description="$2"
    
    print_status "$description"
    
    PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$sql"
    
    if [ $? -eq 0 ]; then
        print_success "$description completed"
    else
        print_error "$description failed"
        return 1
    fi
}

# Truncate all tables like Java tests do
# This is simpler and faster than DELETE with WHERE clauses
EVENTS_SQL="TRUNCATE TABLE events CASCADE"
COMMANDS_SQL="TRUNCATE TABLE commands CASCADE"
OUTBOX_SQL="TRUNCATE TABLE outbox_topic_progress CASCADE"

# Reset sequences to start from 1
SEQUENCE_SQL="ALTER SEQUENCE events_position_seq RESTART WITH 1"

# Execute cleanup
execute_sql "$EVENTS_SQL" "Truncating events table"
execute_sql "$COMMANDS_SQL" "Truncating commands table"
execute_sql "$OUTBOX_SQL" "Truncating outbox_topic_progress table"
execute_sql "$SEQUENCE_SQL" "Resetting events_position_seq"

# Show cleanup summary - just show table counts
SUMMARY_SQL="
SELECT 
    'Events' as table_name,
    COUNT(*) as remaining_records
FROM events
UNION ALL
SELECT 
    'Commands' as table_name,
    COUNT(*) as remaining_records
FROM commands
UNION ALL
SELECT 
    'Outbox Progress' as table_name,
    COUNT(*) as remaining_records
FROM outbox_topic_progress;
"

print_status "Cleanup summary:"
PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$SUMMARY_SQL"

print_success "🎉 Performance test data cleanup completed!"

# Usage examples:
echo ""
echo "Usage examples:"
echo "  # Basic cleanup"
echo "  ./cleanup-data.sh"
echo ""
echo "  # Skip cleanup (keep data for debugging)"
echo "  KEEP_DATA=true ./cleanup-data.sh"
echo ""
echo "  # Custom database connection"
echo "  DB_HOST=localhost DB_PORT=5432 DB_NAME=crablet DB_USER=crablet DB_PASSWORD=secret ./cleanup-data.sh"
echo ""
echo "  # Custom wallet prefix"
echo "  WALLET_PREFIX=test-wallet- ./cleanup-data.sh"
