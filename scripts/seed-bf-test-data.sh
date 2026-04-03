#!/bin/bash
# =============================================================================
# 블랙 프라이데이 부하 테스트용 데이터 시드
#
# 용도: BF 시나리오 테스트를 위한 유저 생성
# 실행: ./scripts/seed-bf-test-data.sh [유저수]
# 전제: commerce-api가 localhost:8080에서 실행 중, seed-test-data.sh 먼저 실행
#
# 생성 데이터:
#   - 회원 N명 (bf0001~bfNNNN, 비밀번호: Password1!)  기본값 5000명
#   - 멱등: 이미 존재하면 skip
#
# 데이터 위치:
#   - DB: member 테이블 (loginId: bf0001~bfNNNN)
#   - 상품/브랜드: seed-test-data.sh에서 생성한 것 재사용
# =============================================================================

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOTAL_USERS="${1:-5000}"

echo "=== 블랙 프라이데이 테스트 데이터 시드 ==="
echo "대상: $BASE_URL"
echo "유저 수: $TOTAL_USERS"
echo ""

echo "[1/1] 회원 생성 (bf0001~bf$(printf '%04d' $TOTAL_USERS))"

SUCCESS=0
FAIL=0
SKIP=0
for i in $(seq 1 $TOTAL_USERS); do
  PADDED=$(printf "%04d" $i)
  YEAR=$((1970 + (i % 30)))
  MONTH=$(printf "%02d" $(( (i % 12) + 1 )))
  DAY=$(printf "%02d" $(( (i % 28) + 1 )))

  RESULT=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/members" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\":\"bf${PADDED}\",\"password\":\"Password1!\",\"name\":\"블프유저${PADDED}\",\"birthDate\":\"${YEAR}-${MONTH}-${DAY}\",\"email\":\"bf${PADDED}@test.com\"}")

  if [ "$RESULT" = "200" ] || [ "$RESULT" = "201" ]; then
    SUCCESS=$((SUCCESS + 1))
  elif [ "$RESULT" = "409" ]; then
    SKIP=$((SKIP + 1))
  else
    FAIL=$((FAIL + 1))
  fi

  # 진행률 표시 (500명 단위)
  if [ $((i % 500)) -eq 0 ]; then
    echo "  ${i}/${TOTAL_USERS} 완료 (성공: ${SUCCESS}, 스킵: ${SKIP}, 실패: ${FAIL})"
  fi
done

echo ""
echo "  최종 결과 — 성공: ${SUCCESS}, 스킵(이미 존재): ${SKIP}, 실패: ${FAIL}"
echo ""

# --- 검증 ---
LAST_ID=$(printf "bf%04d" $TOTAL_USERS)
echo "[검증] 인증 확인"
for id in bf0001 bf0500 $LAST_ID; do
  AUTH_RESULT=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/members/me" \
    -H "X-Loopers-LoginId: ${id}" \
    -H "X-Loopers-LoginPw: Password1!")
  echo "  ${id} 인증: HTTP $AUTH_RESULT"
done

echo ""
echo "=== 시드 완료 ==="
echo "테스트 계정: bf0001~bf$(printf '%04d' $TOTAL_USERS) / Password1!"
echo "상품: seed-test-data.sh에서 생성한 상품 1~5 사용 (재고 10,000)"
