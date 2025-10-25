#!/bin/bash
# Validate that seeded wallets exist in the database

set -e

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="crablet"
DB_USER="crablet"
DB_PASS="crablet"

echo "🔍 Validating seeded wallets..."

# Check each wallet pool
for prefix in "transfer-success" "insufficient" "concurrency" "success"; do
    count=$(PGPASSWORD=$DB_PASS psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c \
        "SELECT COUNT(DISTINCT SUBSTRING(tags[1] FROM 'wallet_id=([^,]+)')) \
         FROM events WHERE tags[1] LIKE 'wallet_id=${prefix}%';")
    
    echo "  ${prefix}-wallet-*: ${count} wallets"
    
    if [ "$count" -eq 0 ]; then
        echo "❌ ERROR: No ${prefix} wallets found!"
        exit 1
    fi
done

echo "✅ All wallet pools validated"

