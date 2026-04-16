#!/bin/bash
# k6 부하테스트 일괄 실행 — daily / weekly / monthly
# 전제조건:
#   1) commerce-api 서버가 :8080 에서 기동 중
#   2) seed.sh로 시드 데이터 생성 완료

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULT_DIR="${SCRIPT_DIR}/results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
# ORDER: 실행 순서 (콤마 구분). 예: ORDER=monthly,weekly,daily
ORDER="${ORDER:-daily,weekly,monthly}"
# LABEL: 결과 파일 접미사 (실험 구분용). 예: LABEL=reversed
LABEL="${LABEL:-}"
# WARMUP: 메인 측정 전 warmup 여부 (true|false). 기본 false
WARMUP="${WARMUP:-false}"
# WARMUP_SEC: warmup 지속 시간 (초). 기본 10
WARMUP_SEC="${WARMUP_SEC:-10}"

mkdir -p "${RESULT_DIR}"

echo "=== 서버 health 체크 ==="
HEALTH_URL="${BASE_URL}/api/v1/rankings?period=daily&date=20260416&size=1&page=1"
if ! curl -s -f -o /dev/null "${HEALTH_URL}"; then
  echo "ERROR: ${HEALTH_URL} 응답 없음. 서버를 먼저 기동하세요."
  exit 1
fi
echo "OK"

SUFFIX=""
[ -n "${LABEL}" ] && SUFFIX="-${LABEL}"

echo "=== 실행 순서: ${ORDER} ${LABEL:+(label=${LABEL})} ==="

IFS=',' read -ra PERIODS <<< "${ORDER}"
for PERIOD in "${PERIODS[@]}"; do
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "  k6 실행 — period=${PERIOD}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  if [ "${WARMUP}" = "true" ]; then
    WARMUP_URL="${BASE_URL}/api/v1/rankings?period=${PERIOD}&date=20260416&size=20&page=1"
    echo ">> warmup ${WARMUP_SEC}s — period=${PERIOD}"
    WARMUP_END=$(( $(date +%s) + WARMUP_SEC ))
    WARMUP_COUNT=0
    while [ $(date +%s) -lt ${WARMUP_END} ]; do
      curl -s -o /dev/null "${WARMUP_URL}" &
      curl -s -o /dev/null "${WARMUP_URL}" &
      curl -s -o /dev/null "${WARMUP_URL}" &
      curl -s -o /dev/null "${WARMUP_URL}"
      WARMUP_COUNT=$(( WARMUP_COUNT + 4 ))
    done
    wait
    echo ">> warmup 완료 (총 ${WARMUP_COUNT} 요청)"
  fi

  OUTPUT_FILE="${RESULT_DIR}/get-rankings-${PERIOD}${SUFFIX}-${TIMESTAMP}.txt"
  k6 run \
    -e PERIOD="${PERIOD}" \
    -e BASE_URL="${BASE_URL}" \
    --summary-trend-stats="avg,min,med,max,p(90),p(95),p(99)" \
    "${SCRIPT_DIR}/get-rankings.js" \
    2>&1 | tee "${OUTPUT_FILE}"

  echo "-> 결과 저장: ${OUTPUT_FILE}"
done

echo ""
echo "=== 완료 ==="
ls -lh "${RESULT_DIR}" | tail -3
