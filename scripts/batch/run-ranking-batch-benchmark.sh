#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_DATE="${TARGET_DATE:-20260415}"
ROW_COUNT="${ROW_COUNT:-${PRODUCT_COUNT:-10000}}"
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-loopers}"
MYSQL_USER="${MYSQL_USER:-application}"
MYSQL_PWD="${MYSQL_PWD:-application}"
RESULT_FILE="${RESULT_FILE:-$ROOT_DIR/build/ranking-batch-benchmark-$(date +%Y%m%d-%H%M%S).md}"

start_of_week() {
  python3 - <<'PY'
from datetime import datetime, timedelta
import os
d = datetime.strptime(os.environ["TARGET_DATE"], "%Y%m%d").date()
print((d - timedelta(days=d.isoweekday()-1)).isoformat())
PY
}

start_of_month() {
  python3 - <<'PY'
from datetime import datetime
import os
d = datetime.strptime(os.environ["TARGET_DATE"], "%Y%m%d").date()
print(d.replace(day=1).isoformat())
PY
}

run_mysql() {
  mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PWD" "$MYSQL_DB" "$@"
}

seed_metrics() {
  local start_date="$1"
  local end_date="$2"
  run_mysql <<SQL
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE product_metrics_daily;
TRUNCATE TABLE mv_product_rank_weekly;
TRUNCATE TABLE mv_product_rank_monthly;
SET FOREIGN_KEY_CHECKS = 1;
SET @max_products := ${ROW_COUNT};
SET @start_date := '${start_date}';
SET @end_date := '${end_date}';
$(sed \
  -e "s/:max_products/@max_products/g" \
  -e "s/:start_date/@start_date/g" \
  -e "s/:end_date/@end_date/g" \
  "$ROOT_DIR/scripts/batch/seed-ranking-metrics.sql")
SQL
}

seed_products() {
  run_mysql <<'SQL'
INSERT INTO product
    (id, name, price, stock, brand_id, like_count, display_status, created_at, updated_at, deleted_at)
SELECT
    n,
    CONCAT('benchmark-product-', n),
    10000 + n,
    1000,
    NULL,
    0,
    'DISPLAYING',
    NOW(6),
    NOW(6),
    NULL
FROM (
    WITH RECURSIVE seq AS (
        SELECT 1 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 100
    )
    SELECT n FROM seq
) s
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    stock = VALUES(stock),
    display_status = VALUES(display_status),
    updated_at = NOW(6),
    deleted_at = NULL;
SQL
}

measure_job() {
  local job_name="$1"
  local start_ts end_ts duration_ms
  start_ts=$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)
  (cd "$ROOT_DIR" && ./gradlew :apps:commerce-batch:bootRun --args="--spring.batch.job.name=${job_name} --spring.jpa.hibernate.ddl-auto=none targetDate=${TARGET_DATE}" >/tmp/${job_name}.log 2>&1)
  end_ts=$(python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
)
  duration_ms=$((end_ts - start_ts))
  echo "$duration_ms"
}

write_report() {
  local weekly_ms="$1"
  local monthly_ms="$2"
  mkdir -p "$(dirname "$RESULT_FILE")"
  cat > "$RESULT_FILE" <<EOF
# Ranking Batch Benchmark

- targetDate: \`${TARGET_DATE}\`
- rowCount: \`${ROW_COUNT}\`
- weeklyDurationMs: \`${weekly_ms}\`
- monthlyDurationMs: \`${monthly_ms}\`

| Job | Seed Rows | Duration(ms) | Duration(sec) |
| --- | ---: | ---: | ---: |
| weeklyRankingJob | ${ROW_COUNT} | ${weekly_ms} | $(python3 - <<PY
print(round(${weekly_ms} / 1000, 3))
PY
) |
| monthlyRankingJob | ${ROW_COUNT} | ${monthly_ms} | $(python3 - <<PY
print(round(${monthly_ms} / 1000, 3))
PY
) |
EOF
  echo "Benchmark report written to $RESULT_FILE"
}

main() {
  local weekly_start month_start weekly_ms monthly_ms
  weekly_start="$(start_of_week)"
  month_start="$(start_of_month)"

  echo "[1/6] Seeding weekly range data..."
  seed_metrics "$weekly_start" "$TARGET_DATE"
  echo "[2/6] Running weeklyRankingJob..."
  weekly_ms="$(measure_job "weeklyRankingJob")"

  echo "[3/6] Seeding monthly range data..."
  seed_metrics "$month_start" "$TARGET_DATE"
  echo "[4/6] Running monthlyRankingJob..."
  monthly_ms="$(measure_job "monthlyRankingJob")"

  echo "[5/6] Rehydrating weekly MV for API load test..."
  measure_job "weeklyRankingJob" >/dev/null
  echo "[6/6] Seeding TOP 100 products for API load test..."
  seed_products

  write_report "$weekly_ms" "$monthly_ms"
}

main "$@"
