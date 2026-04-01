#!/bin/bash
# 대기열 + 토큰 초기화
echo "=== Redis 대기열 초기화 ==="
redis-cli DEL order:waiting-queue
redis-cli KEYS "order:entry-token:*" | xargs -r redis-cli DEL
redis-cli DEL queue:scheduler:lock
echo "=== 완료 ==="
