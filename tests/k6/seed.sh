#!/bin/bash
# k6 부하테스트 시드 데이터 생성 스크립트
# 전제조건: commerce-api 서버가 기동되어 MV 테이블이 Hibernate DDL로 생성된 상태

set -e

MYSQL="docker exec docker-mysql-1 mysql -uroot -proot -N -e"
REDIS="docker exec redis-master redis-cli"

TODAY="${TODAY:-2026-04-16}"          # daily ZSET 대상
WEEK_START="${WEEK_START:-2026-04-13}" # 월요일 (ISO-8601)
MONTH_START="${MONTH_START:-2026-04-01}"
TOP_N="${TOP_N:-100}"

echo "=== 선행 체크 ==="
${MYSQL} "USE loopers; SHOW TABLES LIKE 'mv_product_rank_weekly';" loopers 2>/dev/null | grep -q mv_product_rank_weekly \
  || { echo "ERROR: mv_product_rank_weekly 테이블이 없습니다. commerce-api를 먼저 기동하세요."; exit 1; }

echo "=== 1) Brand 시드 (UPSERT) ==="
${MYSQL} "
USE loopers;
INSERT INTO brand (id, name, created_at, updated_at)
VALUES
  (1, 'Nike', NOW(), NOW()),
  (2, 'Adidas', NOW(), NOW()),
  (3, 'New Balance', NOW(), NOW()),
  (4, 'Puma', NOW(), NOW()),
  (5, 'Asics', NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), updated_at=NOW();
" 2>/dev/null

echo "=== 2) Product 시드 (TOP_N 개) ==="
# 기존 product 지우지 않고 UPSERT — id 1..TOP_N
# brand_id는 1..5 순환, price 10000~300000 랜덤 느낌, stock 100
SQL="USE loopers; "
for i in $(seq 1 ${TOP_N}); do
  BRAND_ID=$(( (i - 1) % 5 + 1 ))
  PRICE=$(( 10000 + (i * 2971) % 290000 ))
  SQL+="INSERT INTO product (id, name, price, stock_quantity, brand_id, likes_count, created_at, updated_at) "
  SQL+="VALUES (${i}, 'Product-${i}', ${PRICE}, 100, ${BRAND_ID}, 0, NOW(), NOW()) "
  SQL+="ON DUPLICATE KEY UPDATE name=VALUES(name), price=VALUES(price), updated_at=NOW();"
done
${MYSQL} "${SQL}" 2>/dev/null

echo "=== 3) mv_product_rank_weekly 시드 (TOP_N) ==="
${MYSQL} "USE loopers; DELETE FROM mv_product_rank_weekly WHERE week_start_date = '${WEEK_START}';" 2>/dev/null
SQL="USE loopers; INSERT INTO mv_product_rank_weekly (week_start_date, product_id, rank_position, total_score, aggregated_at) VALUES "
for i in $(seq 1 ${TOP_N}); do
  SCORE=$(( (TOP_N - i + 1) * 10 ))
  SQL+="('${WEEK_START}', ${i}, ${i}, ${SCORE}.0, NOW())"
  [ ${i} -lt ${TOP_N} ] && SQL+=", "
done
SQL+=";"
${MYSQL} "${SQL}" 2>/dev/null

echo "=== 4) mv_product_rank_monthly 시드 (TOP_N) ==="
${MYSQL} "USE loopers; DELETE FROM mv_product_rank_monthly WHERE month_start_date = '${MONTH_START}';" 2>/dev/null
SQL="USE loopers; INSERT INTO mv_product_rank_monthly (month_start_date, product_id, rank_position, total_score, aggregated_at) VALUES "
for i in $(seq 1 ${TOP_N}); do
  SCORE=$(( (TOP_N - i + 1) * 10 ))
  SQL+="('${MONTH_START}', ${i}, ${i}, ${SCORE}.0, NOW())"
  [ ${i} -lt ${TOP_N} ] && SQL+=", "
done
SQL+=";"
${MYSQL} "${SQL}" 2>/dev/null

echo "=== 5) Redis ZSET 시드 (daily ranking:all:${TODAY//-/}) ==="
ZSET_KEY="ranking:all:${TODAY//-/}"
${REDIS} DEL "${ZSET_KEY}" > /dev/null
ZADD_CMD="ZADD ${ZSET_KEY}"
for i in $(seq 1 ${TOP_N}); do
  SCORE=$(( (TOP_N - i + 1) * 10 ))
  ZADD_CMD+=" ${SCORE} ${i}"
done
${REDIS} ${ZADD_CMD} > /dev/null
${REDIS} EXPIRE "${ZSET_KEY}" 172800 > /dev/null

echo ""
echo "=== 결과 요약 ==="
WEEKLY_CNT=$(${MYSQL} "USE loopers; SELECT COUNT(*) FROM mv_product_rank_weekly WHERE week_start_date='${WEEK_START}';" 2>/dev/null)
MONTHLY_CNT=$(${MYSQL} "USE loopers; SELECT COUNT(*) FROM mv_product_rank_monthly WHERE month_start_date='${MONTH_START}';" 2>/dev/null)
ZSET_CNT=$(${REDIS} ZCARD "${ZSET_KEY}")

echo "  brand            : $(${MYSQL} "USE loopers; SELECT COUNT(*) FROM brand;" 2>/dev/null)"
echo "  product          : $(${MYSQL} "USE loopers; SELECT COUNT(*) FROM product;" 2>/dev/null)"
echo "  mv_weekly[${WEEK_START}]  : ${WEEKLY_CNT}"
echo "  mv_monthly[${MONTH_START}] : ${MONTHLY_CNT}"
echo "  ZSET[${ZSET_KEY}]: ${ZSET_CNT}"
echo ""
echo "OK"
