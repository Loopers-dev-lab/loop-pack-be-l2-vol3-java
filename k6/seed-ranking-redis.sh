#!/bin/bash
# ============================================================
# Redis ZSET 시딩 스크립트
# MySQL product_metrics 데이터를 Redis ZSET에 동기화
# 사용법: ./seed-ranking-redis.sh [date]
#   date: yyyyMMdd (기본: 오늘)
# ============================================================

DATE=${1:-$(date +%Y%m%d)}
KEY="ranking:all:${DATE}"
TTL=$((2 * 24 * 60 * 60))  # 2일

MYSQL_HOST=${MYSQL_HOST:-localhost}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASS=${MYSQL_PASS:-root}
MYSQL_DB=${MYSQL_DB:-commerce}
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}

echo "=== Redis ZSET 시딩 시작 ==="
echo "Key: ${KEY}, TTL: ${TTL}s"

# MySQL에서 가중치 합산 점수 조회 → Redis ZADD 파이프라인
mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" -p"${MYSQL_PASS}" "${MYSQL_DB}" -N -e "
    SELECT product_id,
           (view_count * 0.1 + like_count * 0.2 + sales_count * 0.7) AS score
    FROM product_metrics
" | while IFS=$'\t' read -r product_id score; do
    echo "ZADD ${KEY} ${score} ${product_id}"
done | redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" --pipe

# TTL 설정
redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${KEY}" "${TTL}"

TOTAL=$(redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ZCARD "${KEY}")
echo "=== 시딩 완료: ${TOTAL}건, Key: ${KEY} ==="
