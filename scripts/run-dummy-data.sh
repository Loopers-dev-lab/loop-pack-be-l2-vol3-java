#!/bin/bash
# =============================================================================
# Dummy Data Insert Script Runner
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/insert-dummy-data.sql"

# MySQL connection info (from docker/infra-compose.yml)
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-loopers}"

echo "============================================="
echo "Dummy Data Insert Script"
echo "============================================="
echo "Target: $MYSQL_HOST:$MYSQL_PORT/$MYSQL_DATABASE"
echo "User: $MYSQL_USER"
echo ""

# Check if MySQL is accessible
echo "Checking MySQL connection..."
if ! mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1" "$MYSQL_DATABASE" > /dev/null 2>&1; then
    echo "ERROR: Cannot connect to MySQL. Make sure Docker containers are running."
    echo "Run: docker compose -f docker/infra-compose.yml up -d"
    exit 1
fi
echo "MySQL connection OK"
echo ""

# Check current data
echo "Current data in database:"
mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'brands', COUNT(*) FROM brands
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'likes', COUNT(*) FROM likes
UNION ALL SELECT 'coupon_templates', COUNT(*) FROM coupon_templates
UNION ALL SELECT 'orders', COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items;
" 2>/dev/null || echo "(Tables may not exist yet)"

echo ""
read -p "This will INSERT data (existing data will remain). Continue? [y/N]: " confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo "Running SQL script... (this may take 10-30 minutes)"
echo "============================================="

# Run the SQL script
mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < "$SQL_FILE"

echo ""
echo "============================================="
echo "Done!"
echo "============================================="
