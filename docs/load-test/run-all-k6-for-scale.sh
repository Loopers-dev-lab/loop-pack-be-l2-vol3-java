#!/usr/bin/env bash
# 규모(상품 건수)별 PLP/PDP k6 부하 테스트 실행 및 결과 요약
# - README(performance)에 정의된 시나리오·SLO에 맞춰 avg/p95/p99/max/에러율을 수집한다.
# 사용: ./docs/load-test/run-all-k6-for-scale.sh [100000|200000|500000|1000000] [duration_sec]
# 예:  ./docs/load-test/run-all-k6-for-scale.sh 100000 30
# 전제:
#   - commerce-api가 localhost:8080에서 기동 중 (또는 BASE_URL로 덮어쓰기)
#   - ProductDataSeeder로 해당 규모만큼 상품 시드 완료

set -e
BASE_URL="${BASE_URL:-http://localhost:8080}"
SCALE="${1:-100000}"
DURATION="${2:-30}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "=== scale=$SCALE duration=${DURATION}s BASE_URL=$BASE_URL ==="

run_plp() {
  local sort=$1
  local page=$2
  local label="${sort}_page${page}"
  local out
  out=$(k6 run --summary-trend-stats="avg,p(95),p(99),max" \
    -e "SORT=$sort" -e "PAGE=$page" -e "BASE_URL=$BASE_URL" \
    --vus 50 --duration "${DURATION}s" \
    docs/load-test/k6-product-plp.js 2>&1) || true
  echo "$out" | grep -E "http_req_duration|http_req_failed" | head -2
  local avg p95 p99 max err
  avg=$(echo "$out" | grep -oE "avg=[0-9.]+(ms|µs)" | head -1 | sed -E 's/avg=([0-9.]+)(ms|µs)/\1/' | head -1)
  p95=$(echo "$out" | grep -oE "p\\(95\\)=[0-9.]+(ms|µs)" | head -1 | sed -E 's/p\\(95\\)=([0-9.]+)(ms|µs)/\1/' | head -1)
  p99=$(echo "$out" | grep -oE "p\\(99\\)=[0-9.]+(ms|µs)" | head -1 | sed -E 's/p\\(99\\)=([0-9.]+)(ms|µs)/\1/' | head -1)
  max=$(echo "$out" | grep -oE "max=[0-9.]+(ms|µs)" | head -1 | sed -E 's/max=([0-9.]+)(ms|µs)/\1/' | head -1)
  err=$(echo "$out" | grep -oE "http_req_failed......: [0-9.]+%" | head -1 | grep -oE "[0-9.]+" | head -1)
  echo "PLP $label avg=${avg} p95=${p95} p99=${p99} max=${max} err=${err}%"
}

run_pdp() {
  local max_id=$1
  local out
  out=$(k6 run --summary-trend-stats="avg,p(95),p(99),max" \
    -e "MAX_PRODUCT_ID=$max_id" -e "BASE_URL=$BASE_URL" \
    --vus 100 --duration "${DURATION}s" \
    docs/load-test/k6-product-pdp.js 2>&1) || true
  echo "$out" | grep -E "http_req_duration|http_req_failed|checks_succeeded" | head -3
  local avg p95 p99 max err
  avg=$(echo "$out" | grep -oE "avg=[0-9.]+(ms|µs)" | head -1 | sed -E 's/avg=([0-9.]+)(ms|µs)/\1/' | head -1)
  p95=$(echo "$out" | grep -oE "p\\(95\\)=[0-9.]+(ms|µs)" | head -1 | sed -E 's/p\\(95\\)=([0-9.]+)(ms|µs)/\1/' | head -1)
  p99=$(echo "$out" | grep -oE "p\\(99\\)=[0-9.]+(ms|µs)" | head -1 | sed -E 's/p\\(99\\)=([0-9.]+)(ms|µs)/\1/' | head -1)
  max=$(echo "$out" | grep -oE "max=[0-9.]+(ms|µs)" | head -1 | sed -E 's/max=([0-9.]+)(ms|µs)/\1/' | head -1)
  err=$(echo "$out" | grep -oE "http_req_failed......: [0-9.]+%" | head -1 | grep -oE "[0-9.]+" | head -1)
  echo "PDP scale=$max_id avg=${avg} p95=${p95} p99=${p99} max=${max} err=${err}%"
}

# PLP 시나리오 4종
run_plp latest 0
run_plp price_asc 0
run_plp likes_desc 0
run_plp latest 50

# PDP 시나리오 (규모별 ID 범위)
run_pdp "$SCALE"

echo "=== scale=$SCALE 완료 ==="
