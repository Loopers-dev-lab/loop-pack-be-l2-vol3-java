#!/bin/bash
# =============================================================================
# 블랙 프라이데이 부하 테스트 초기화
#
# 용도: 매 테스트 실행 전 상태 초기화 (반복 실행 보장)
# 실행: ./scripts/reset-bf-test.sh
# 전제: Redis(6379), commerce-api(8080) 실행 중
#
# 초기화 항목:
#   1. Redis 대기열 초기화 (queue:waiting:order 삭제)
#   2. Redis 토큰 전체 삭제 (queue:token:* 삭제)
#   3. 상품 재고 리셋 (API 호출 또는 DB 직접)
# =============================================================================

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_HEADER="X-Loopers-Ldap: loopers.admin"

echo "=== BF 테스트 초기화 ==="
echo ""

# --- 1. Redis 대기열 초기화 ---
echo "[1/3] Redis 대기열 초기화"
QUEUE_SIZE=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT ZCARD queue:waiting:order 2>/dev/null)
echo "  현재 대기열 크기: ${QUEUE_SIZE:-0}"
redis-cli -h $REDIS_HOST -p $REDIS_PORT DEL queue:waiting:order > /dev/null 2>&1
echo "  queue:waiting:order 삭제 완료"

# --- 2. Redis 토큰 삭제 ---
echo ""
echo "[2/3] Redis 입장 토큰 초기화"
TOKEN_COUNT=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT --scan --pattern "queue:token:*" 2>/dev/null | wc -l | tr -d ' ')
echo "  현재 토큰 수: ${TOKEN_COUNT:-0}"

# SCAN 기반 삭제 (KEYS * 사용 안 함 — 운영 안전)
redis-cli -h $REDIS_HOST -p $REDIS_PORT --scan --pattern "queue:token:*" 2>/dev/null | while read key; do
  redis-cli -h $REDIS_HOST -p $REDIS_PORT DEL "$key" > /dev/null 2>&1
done
echo "  queue:token:* 삭제 완료"

# --- 3. 상품 재고 확인 ---
echo ""
echo "[3/3] 상품 재고 확인"
for i in $(seq 1 5); do
  STOCK_RES=$(curl -s "$BASE_URL/api/v1/products/${i}" \
    -H "X-Loopers-LoginId: bf0001" \
    -H "X-Loopers-LoginPw: Password1!")
  STOCK=$(echo "$STOCK_RES" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('stockQuantity','?'))" 2>/dev/null)
  echo "  product:${i} 재고: ${STOCK:-확인 실패}"
done

echo ""

# --- 검증 ---
echo "[검증] Redis 상태 확인"
FINAL_QUEUE=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT ZCARD queue:waiting:order 2>/dev/null)
FINAL_TOKENS=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT --scan --pattern "queue:token:*" 2>/dev/null | wc -l | tr -d ' ')
echo "  대기열: ${FINAL_QUEUE:-0}명"
echo "  토큰: ${FINAL_TOKENS:-0}개"

echo ""
echo "=== 초기화 완료 — 테스트 실행 가능 ==="
