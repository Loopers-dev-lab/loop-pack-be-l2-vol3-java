#!/bin/bash
# =============================================================================
# 테스트 데이터 시드 스크립트
#
# 용도: 로컬 개발 및 부하 테스트용 데이터 생성
# 실행: ./scripts/seed-test-data.sh
# 전제: commerce-api가 localhost:8080에서 실행 중이어야 함
#
# 생성 데이터:
#   - 회원 10명 (user1~user10, 비밀번호: Password1!)
#   - 브랜드 2개 (나이키, 아디다스)
#   - 상품 5개 (재고 10000개씩)
# =============================================================================

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_HEADER="X-Loopers-Ldap: loopers.admin"

echo "=== 테스트 데이터 시드 시작 ==="
echo "대상: $BASE_URL"
echo ""

# --- 회원 생성 ---
echo "[1/3] 회원 생성 (user1~user10)"
for i in $(seq 1 10); do
  RESULT=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/members" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\":\"user${i}\",\"password\":\"Password1!\",\"name\":\"테스트유저${i}\",\"birthDate\":\"199${i}-01-15\",\"email\":\"user${i}@test.com\"}")
  if [ "$RESULT" = "200" ] || [ "$RESULT" = "201" ]; then
    echo "  user${i} 생성 완료"
  else
    echo "  user${i} 생성 실패 (HTTP $RESULT) — 이미 존재하거나 오류"
  fi
done

echo ""

# --- 브랜드 생성 ---
echo "[2/3] 브랜드 생성"
BRAND1=$(curl -s -X POST "$BASE_URL/api-admin/v1/brands" \
  -H "Content-Type: application/json" \
  -H "$ADMIN_HEADER" \
  -d '{"name":"나이키","description":"스포츠 브랜드"}')
BRAND1_ID=$(echo "$BRAND1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null)
echo "  나이키 생성 완료 (id: ${BRAND1_ID:-실패})"

BRAND2=$(curl -s -X POST "$BASE_URL/api-admin/v1/brands" \
  -H "Content-Type: application/json" \
  -H "$ADMIN_HEADER" \
  -d '{"name":"아디다스","description":"스포츠 브랜드"}')
BRAND2_ID=$(echo "$BRAND2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null)
echo "  아디다스 생성 완료 (id: ${BRAND2_ID:-실패})"

echo ""

# --- 상품 생성 ---
echo "[3/3] 상품 생성 (재고 10000개)"

# 브랜드 ID가 없으면 기본값 사용
BRAND1_ID="${BRAND1_ID:-1}"
BRAND2_ID="${BRAND2_ID:-2}"

PRODUCTS=(
  "{\"brandId\":${BRAND1_ID},\"name\":\"에어맥스 90\",\"price\":129000,\"stockQuantity\":10000}"
  "{\"brandId\":${BRAND1_ID},\"name\":\"에어포스 1\",\"price\":119000,\"stockQuantity\":10000}"
  "{\"brandId\":${BRAND1_ID},\"name\":\"덩크 로우\",\"price\":139000,\"stockQuantity\":10000}"
  "{\"brandId\":${BRAND2_ID},\"name\":\"울트라부스트\",\"price\":199000,\"stockQuantity\":10000}"
  "{\"brandId\":${BRAND2_ID},\"name\":\"스탠스미스\",\"price\":99000,\"stockQuantity\":10000}"
)

PRODUCT_NAMES=("에어맥스 90" "에어포스 1" "덩크 로우" "울트라부스트" "스탠스미스")

for i in "${!PRODUCTS[@]}"; do
  RESULT=$(curl -s -X POST "$BASE_URL/api-admin/v1/products" \
    -H "Content-Type: application/json" \
    -H "$ADMIN_HEADER" \
    -d "${PRODUCTS[$i]}")
  PRODUCT_ID=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null)
  echo "  ${PRODUCT_NAMES[$i]} 생성 완료 (id: ${PRODUCT_ID:-실패})"
done

echo ""

# --- Redis 재고 초기화 확인 ---
echo "[검증] Redis 재고 확인"
for i in $(seq 1 5); do
  STOCK=$(redis-cli -p 6379 GET "stock:${i}" 2>/dev/null)
  echo "  product:${i} Redis 재고: ${STOCK:-미설정}"
done

echo ""

# --- 검증 ---
echo "[검증] API 응답 확인"
echo -n "  회원 인증: "
AUTH_RESULT=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/members/me" \
  -H "X-Loopers-LoginId: user1" \
  -H "X-Loopers-LoginPw: Password1!")
echo "HTTP $AUTH_RESULT"

echo -n "  상품 목록: "
PRODUCT_RESULT=$(curl -s "$BASE_URL/api/v1/products" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'{len(d.get(\"data\",{}).get(\"products\",[]))}개')" 2>/dev/null)
echo "${PRODUCT_RESULT:-실패}"

echo ""
echo "=== 시드 완료 ==="
echo ""
echo "테스트 계정: user1~user10 / Password1!"
echo "상품 ID: 1~5 (재고 10000개)"
