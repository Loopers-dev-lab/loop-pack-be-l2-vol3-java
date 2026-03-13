#!/usr/bin/env bash
# 로드맵 Phase별 K6 부하 테스트 실행 및 결과를 phase-benchmark-results.md에 기록
# 사용: ./docs/load-test/run-all-k6-phase.sh [PHASE_LABEL] [SCALE] [DURATION]
# 예:   ./docs/load-test/run-all-k6-phase.sh baseline 100000 30
# 전제: commerce-api가 localhost:8080 기동 중, 필요 시 BASE_URL/LOGIN_ID 설정
#       좋아요 테스트 전: ./docs/load-test/local-auth-setup.sh 8080 perfuser

set -e
BASE_URL="${BASE_URL:-http://localhost:8080}"
LOGIN_ID="${LOGIN_ID:-perfuser}"
PHASE_LABEL="${1:-baseline}"
SCALE="${2:-100000}"
DURATION="${3:-30}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RESULTS_MD="${ROOT}/docs/performance/phase-benchmark-results.md"
cd "$ROOT"

echo "=== Phase: $PHASE_LABEL | scale=$SCALE | duration=${DURATION}s | BASE_URL=$BASE_URL ==="

run_plp() {
  local sort=$1
  local page=$2
  local label="${sort}_page${page}"
  local out
  out=$(k6 run --summary-trend-stats="avg,p(95),p(99),max" \
    -e "SORT=$sort" -e "PAGE=$page" -e "BASE_URL=$BASE_URL" \
    --vus 50 --duration "${DURATION}s" \
    docs/load-test/k6-product-plp.js 2>&1) || true
  local avg p95 p99 max err
  avg=$(echo "$out" | grep -oE "avg=[0-9.]+" | head -1 | sed -E 's/avg=//')
  p95=$(echo "$out" | grep -oE "p\\(95\\)=[0-9.]+" | head -1 | sed -E 's/p\\(95\\)=//')
  p99=$(echo "$out" | grep -oE "p\\(99\\)=[0-9.]+" | head -1 | sed -E 's/p\\(99\\)=//')
  max=$(echo "$out" | grep -oE "max=[0-9.]+" | head -1 | sed -E 's/max=//')
  err=$(echo "$out" | grep -oE "http_req_failed......: [0-9.]+%" | head -1 | grep -oE "[0-9.]+" | head -1)
  echo "PLP|${label}|${avg}|${p95}|${p99}|${max}|${err}"
}

run_pdp() {
  local max_id=$1
  local out
  out=$(k6 run --summary-trend-stats="avg,p(95),p(99),max" \
    -e "MAX_PRODUCT_ID=$max_id" -e "BASE_URL=$BASE_URL" \
    --vus 100 --duration "${DURATION}s" \
    docs/load-test/k6-product-pdp.js 2>&1) || true
  local avg p95 p99 max err
  avg=$(echo "$out" | grep -oE "avg=[0-9.]+" | head -1 | sed -E 's/avg=//')
  p95=$(echo "$out" | grep -oE "p\\(95\\)=[0-9.]+" | head -1 | sed -E 's/p\\(95\\)=//')
  p99=$(echo "$out" | grep -oE "p\\(99\\)=[0-9.]+" | head -1 | sed -E 's/p\\(99\\)=//')
  max=$(echo "$out" | grep -oE "max=[0-9.]+" | head -1 | sed -E 's/max=//')
  err=$(echo "$out" | grep -oE "http_req_failed......: [0-9.]+%" | head -1 | grep -oE "[0-9.]+" | head -1)
  echo "PDP|scale=$max_id|${avg}|${p95}|${p99}|${max}|${err}"
}

run_likes_write() {
  local max_id=$1
  local out
  out=$(k6 run --summary-trend-stats="avg,p(95),p(99),max" \
    -e "BASE_URL=$BASE_URL" -e "LOGIN_ID=$LOGIN_ID" -e "MAX_PRODUCT_ID=$max_id" \
    --vus 100 --duration "${DURATION}s" \
    docs/load-test/k6-likes-write.js 2>&1) || true
  local avg p95 p99 max err
  avg=$(echo "$out" | grep -oE "avg=[0-9.]+" | head -1 | sed -E 's/avg=//')
  p95=$(echo "$out" | grep -oE "p\\(95\\)=[0-9.]+" | head -1 | sed -E 's/p\\(95\\)=//')
  p99=$(echo "$out" | grep -oE "p\\(99\\)=[0-9.]+" | head -1 | sed -E 's/p\\(99\\)=//')
  max=$(echo "$out" | grep -oE "max=[0-9.]+" | head -1 | sed -E 's/max=//')
  err=$(echo "$out" | grep -oE "http_req_failed......: [0-9.]+%" | head -1 | grep -oE "[0-9.]+" | head -1)
  echo "likes_write|scale=$max_id|${avg}|${p95}|${p99}|${max}|${err}"
}

# 결과 수집
RESULTS_TMP=$(mktemp)
run_plp latest 0 >> "$RESULTS_TMP"
run_plp price_asc 0 >> "$RESULTS_TMP"
run_plp likes_desc 0 >> "$RESULTS_TMP"
run_plp latest 50 >> "$RESULTS_TMP"
run_pdp "$SCALE" >> "$RESULTS_TMP"
run_likes_write "$SCALE" >> "$RESULTS_TMP"

# Markdown 테이블 생성 (형식: PLP|latest_page0|avg|p95|p99|max|err)
TIMESTAMP=$(date -u "+%Y-%m-%d %H:%M UTC")
MD_BLOCK=$(mktemp)
{
  echo ""
  echo "## $PHASE_LABEL ($TIMESTAMP)"
  echo ""
  echo "| 시나리오 | avg(ms) | p95(ms) | p99(ms) | max(ms) | 에러율(%) |"
  echo "| --- | ---: | ---: | ---: | ---: | ---: |"
  while IFS= read -r line; do
    IFS='|' read -r kind val2 val3 val4 val5 val6 val7 <<< "$line"
    echo "| $kind $val2 | ${val3:-—} | ${val4:-—} | ${val5:-—} | ${val6:-—} | ${val7:-—} |"
  done < "$RESULTS_TMP"
} > "$MD_BLOCK"

# 결과 문서에 추가 (파일 끝에 append)
cat "$MD_BLOCK" >> "$RESULTS_MD"
rm -f "$RESULTS_TMP" "$MD_BLOCK"

echo "=== 결과가 $RESULTS_MD 에 추가되었습니다 ==="
tail -20 "$RESULTS_MD"
