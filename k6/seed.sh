#!/bin/bash
# k6 부하 테스트를 위한 시드 데이터 적재 스크립트
# 사전 조건: 앱이 localhost:8080에서 실행 중이어야 합니다.
#
# 생성 데이터:
#   - 유저 1명: testuser / Test1234!
#   - 브랜드 5개
#   - 상품 100개 (브랜드당 20개, 재고 각 1000개)

BASE="http://localhost:8080"
ADMIN_HEADER="X-Loopers-Ldap: loopers.admin"

echo "=== k6 시드 데이터 적재 시작 ==="

# 1. 유저 생성
echo "[1/3] 유저 생성..."
curl -s -X POST "${BASE}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d '{
    "loginId": "testuser",
    "password": "Test1234!",
    "userName": "테스트유저",
    "birthday": "19900101",
    "email": "test@loopers.com",
    "address": "서울시 강남구"
  }' | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'  유저 생성: {d.get(\"meta\",{}).get(\"result\",\"UNKNOWN\")}')" 2>/dev/null || echo "  유저 생성 완료 (또는 이미 존재)"

# 2. 브랜드 5개 생성
echo "[2/3] 브랜드 5개 생성..."
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
    echo "  브랜드 생성: ${BRAND_NAMES[$i]} (ID: $BRAND_ID)"
  else
    echo "  브랜드 생성 실패: ${BRAND_NAMES[$i]}"
  fi
done

# 3. 상품 100개 생성 (브랜드당 20개)
echo "[3/3] 상품 100개 생성 (브랜드당 20개, 재고 1000)..."
PRODUCT_COUNT=0

for BRAND_ID in "${BRAND_IDS[@]}"; do
  for j in $(seq 1 20); do
    PRICE=$((10000 + RANDOM % 90000))
    RESULT=$(curl -s -X POST "${BASE}/api-admin/v1/products" \
      -H "Content-Type: application/json" \
      -H "${ADMIN_HEADER}" \
      -d "{
        \"productName\": \"상품-B${BRAND_ID}-P${j}\",
        \"brandId\": ${BRAND_ID},
        \"price\": ${PRICE},
        \"description\": \"브랜드${BRAND_ID}의 ${j}번째 상품\",
        \"initialStock\": 1000
      }")
    PRODUCT_COUNT=$((PRODUCT_COUNT + 1))
  done
  echo "  브랜드 ${BRAND_ID}: 20개 상품 생성 완료 (누적: ${PRODUCT_COUNT}개)"
done

echo ""
echo "=== 시드 데이터 적재 완료 ==="
echo "  유저: 1명 (testuser / Test1234!)"
echo "  브랜드: ${#BRAND_IDS[@]}개"
echo "  상품: ${PRODUCT_COUNT}개 (재고 각 1000개)"
echo ""
echo "k6 실행:"
echo "  docker run --rm -i --network host -v \$(pwd)/k6:/scripts grafana/k6 run /scripts/scripts/product-steady.js"
