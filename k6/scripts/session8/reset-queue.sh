#!/bin/bash
# 대기열 + 토큰 + 인증 캐시 초기화
REDIS_CONTAINER="redis-master"

echo "=== Redis 대기열 초기화 ==="

docker exec "$REDIS_CONTAINER" redis-cli DEL order:waiting-queue
docker exec "$REDIS_CONTAINER" redis-cli DEL queue:scheduler:lock

# 토큰 키 일괄 삭제 (SCAN 기반)
docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern "order:entry-token:*" \
    | xargs -r -L 100 docker exec -i "$REDIS_CONTAINER" redis-cli DEL 2>/dev/null

# 인증 캐시는 유지 — warm 상태 보존이 시스템 전제 조건
# cold 테스트가 필요하면 별도 스크립트로 수동 삭제:
#   docker exec redis-master redis-cli --scan --pattern "auth:cache:*" \
#       | xargs -r -L 100 docker exec -i redis-master redis-cli DEL

echo "=== 완료 ==="
