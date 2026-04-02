#!/bin/bash
# 대기열 + 토큰 + 인증 캐시 초기화
REDIS_CONTAINER="redis-master"

echo "=== Redis 대기열 초기화 ==="

docker exec "$REDIS_CONTAINER" redis-cli DEL order:waiting-queue
docker exec "$REDIS_CONTAINER" redis-cli DEL queue:scheduler:lock

# 토큰 키 일괄 삭제 (SCAN 기반)
docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern "order:entry-token:*" \
    | xargs -r -L 100 docker exec -i "$REDIS_CONTAINER" redis-cli DEL 2>/dev/null

# 인증 캐시 초기화 (테스트 간 격리)
docker exec "$REDIS_CONTAINER" redis-cli --scan --pattern "auth:cache:*" \
    | xargs -r -L 100 docker exec -i "$REDIS_CONTAINER" redis-cli DEL 2>/dev/null

echo "=== 완료 ==="
