#!/bin/bash
# 선착순 쿠폰 테스트 데이터 리셋
# rush-coupon-spike.js, rush-coupon-redis-gate.js 실행 전에 호출
#
# 사전 조건: Docker MySQL + Redis 실행 중

COUPON_ID=${RUSH_COUPON_ID:-1}
MYSQL_CMD="docker exec -i $(docker ps -qf 'ancestor=mysql' | head -1) mysql -u application -papplication loopers"

echo "=== 선착순 쿠폰 리셋 (couponId=${COUPON_ID}) ==="

# 1. DB 리셋
echo "[1/3] DB 리셋..."
echo "
  -- issued_count 리셋
  UPDATE coupons SET issued_count = 0 WHERE coupon_id = ${COUPON_ID};

  -- 기존 발급 결과 삭제
  DELETE FROM coupon_issue_result WHERE coupon_id = ${COUPON_ID};

  -- 기존 user_coupon 삭제 (선착순 쿠폰 관련)
  DELETE FROM user_coupons WHERE coupon_id = ${COUPON_ID};

  -- event_handled 정리 (쿠폰 관련)
  -- event_handled는 eventId 기반이므로 전체 삭제 불필요. 필요 시:
  -- TRUNCATE event_handled;
" | $MYSQL_CMD 2>/dev/null && echo "  DB 리셋 완료" || echo "  DB 리셋 실패"

# 2. Redis remaining 리셋
echo "[2/3] Redis remaining 리셋..."
docker exec -i $(docker ps -qf "ancestor=redis" | head -1) redis-cli SET "coupon:${COUPON_ID}:remaining" "100" 2>/dev/null \
  && echo "  Redis SET coupon:${COUPON_ID}:remaining = 100" \
  || echo "  Redis 리셋 실패"

# 3. Redis dedup keys 정리
echo "[3/3] Redis dedup keys 정리..."
docker exec -i $(docker ps -qf "ancestor=redis" | head -1) redis-cli EVAL "
  local keys = redis.call('KEYS', 'coupon:issue:dedup:*')
  for _, key in ipairs(keys) do redis.call('DEL', key) end
  return #keys
" 0 2>/dev/null && echo "  dedup keys 정리 완료" || echo "  dedup keys 정리 실패 (수동: redis-cli KEYS 'coupon:issue:dedup:*')"

echo ""
echo "=== 리셋 완료 ==="
echo "  coupons.issued_count = 0"
echo "  Redis coupon:${COUPON_ID}:remaining = 100"
echo "  coupon_issue_result, user_coupons 삭제"
echo ""
echo "테스트 실행:"
echo "  docker run --rm -i --network host -e RUSH_COUPON_ID=${COUPON_ID} \\"
echo "    -v \$(pwd)/k6:/scripts grafana/k6 run /scripts/scripts/session7/rush-coupon-spike.js"
