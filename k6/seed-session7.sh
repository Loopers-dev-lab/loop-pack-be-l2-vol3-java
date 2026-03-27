#!/bin/bash
# Session 7 k6 부하 테스트 시드 데이터
# 사전 조건: commerce-api가 localhost:8080에서 실행 중
#
# 생성 데이터:
#   - 유저 1000명 (k6user1~k6user1000)
#   - 브랜드 5개
#   - 상품 100개 (브랜드당 20개, 재고 10000)
#   - 선착순 쿠폰 1개 (max_quantity=100)
#   - Redis remaining 초기화

BASE="http://localhost:8080"
ADMIN_HEADER="X-Loopers-Ldap: loopers.admin"

echo "=== Session 7 시드 데이터 적재 시작 ==="

# 1. 유저 1000명 생성 (병렬)
echo "[1/5] 유저 1000명 생성..."
SUCCESS=0
FAIL=0
for i in $(seq 1 1000); do
  RES=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/users" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\":\"k6user$i\",\"password\":\"Test1234!\",\"userName\":\"K6User$i\",\"birthday\":\"19900101\",\"email\":\"k6user$i@test.com\",\"address\":\"Seoul\"}")
  if [ "$RES" = "200" ]; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
  # 진행률 표시
  if [ $((i % 100)) -eq 0 ]; then
    echo "  $i / 1000 완료 (성공=$SUCCESS, 실패=$FAIL)"
  fi
done
echo "  유저 생성 완료: 성공=$SUCCESS, 실패=$FAIL"

# 2. 브랜드 5개 생성
echo "[2/5] 브랜드 5개 생성..."
BRAND_NAMES=("나이키" "아디다스" "구찌" "프라다" "루이비통")
BRAND_IDS=()

for i in "${!BRAND_NAMES[@]}"; do
  RESULT=$(curl -s -X POST "${BASE}/api-admin/v1/brands" \
    -H "Content-Type: application/json" \
    -H "${ADMIN_HEADER}" \
    -d "{
      \"brandName\": \"${BRAND_NAMES[$i]}\",
      \"description\": \"${BRAND_NAMES[$i]} 공식 브랜드\",
      \"address\": \"서울시 강남구 $((i+1))번길\"
    }")
  BRAND_ID=$(echo "$RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('brandId',''))" 2>/dev/null)
  if [ -n "$BRAND_ID" ]; then
    BRAND_IDS+=("$BRAND_ID")
    echo "  브랜드: ${BRAND_NAMES[$i]} (ID: $BRAND_ID)"
  fi
done

# 3. 상품 100개 생성 (재고 10000)
echo "[3/5] 상품 100개 생성 (브랜드당 20개, 재고 10000)..."
PRODUCT_COUNT=0
for BRAND_ID in "${BRAND_IDS[@]}"; do
  for j in $(seq 1 20); do
    PRICE=$((10000 + RANDOM % 90000))
    curl -s -o /dev/null -X POST "${BASE}/api-admin/v1/products" \
      -H "Content-Type: application/json" \
      -H "${ADMIN_HEADER}" \
      -d "{
        \"productName\": \"상품-B${BRAND_ID}-P${j}\",
        \"brandId\": ${BRAND_ID},
        \"price\": ${PRICE},
        \"description\": \"브랜드${BRAND_ID}의 ${j}번째 상품\",
        \"initialStock\": 10000
      }"
    PRODUCT_COUNT=$((PRODUCT_COUNT + 1))
  done
  echo "  브랜드 ${BRAND_ID}: 20개 상품 (누적: ${PRODUCT_COUNT})"
done

# 4. 선착순 쿠폰 생성 (100장 한정)
echo "[4/5] 선착순 쿠폰 생성 (100장 한정)..."
COUPON_RESULT=$(curl -s -X POST "${BASE}/api-admin/v1/coupons" \
  -H "Content-Type: application/json" \
  -H "${ADMIN_HEADER}" \
  -d "{
    \"name\": \"선착순100장쿠폰\",
    \"type\": \"FIXED\",
    \"value\": 5000,
    \"minOrderAmount\": 10000,
    \"expiredAt\": \"2026-12-31T23:59:59\",
    \"maxQuantity\": 100
  }")
COUPON_ID=$(echo "$COUPON_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('couponId',''))" 2>/dev/null)
echo "  쿠폰 ID: ${COUPON_ID:-생성 실패}"

# 5. Redis remaining 초기화
echo "[5/5] Redis remaining 초기화..."
if [ -n "$COUPON_ID" ]; then
  docker exec -i $(docker ps -qf "ancestor=redis" | head -1) redis-cli SET "coupon:${COUPON_ID}:remaining" "100" 2>/dev/null \
    && echo "  Redis SET coupon:${COUPON_ID}:remaining = 100" \
    || echo "  Redis 설정 실패 — 수동 실행: redis-cli SET coupon:${COUPON_ID}:remaining 100"
fi

echo ""
echo "=== 시드 데이터 적재 완료 ==="
echo "  유저: 1000명 (k6user1~k6user1000 / Test1234!)"
echo "  브랜드: ${#BRAND_IDS[@]}개"
echo "  상품: ${PRODUCT_COUNT}개 (재고 10000)"
echo "  선착순 쿠폰: ID=${COUPON_ID:-?} (100장)"
echo ""
echo "쿠폰 ID를 k6 스크립트에 설정하세요:"
echo "  export RUSH_COUPON_ID=${COUPON_ID}"
echo ""
echo "k6 실행 예시:"
echo "  docker run --rm -i --network host -e RUSH_COUPON_ID=${COUPON_ID} \\"
echo "    -v \$(pwd)/k6:/scripts grafana/k6 run /scripts/scripts/session7/rush-coupon-spike.js"
