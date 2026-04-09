#!/bin/bash
# =============================================================================
# 부하 테스트용 대량 데이터 시드
#
# 용도: 현실적인 부하 테스트를 위한 유저 100명 생성
# 실행: ./scripts/seed-load-test-data.sh
# 전제: commerce-api가 localhost:8080에서 실행 중, seed-test-data.sh 먼저 실행
# =============================================================================

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "=== 부하 테스트 데이터 시드 ==="
echo "대상: $BASE_URL"
echo ""

# --- 회원 100명 생성 (user01~user99 + 기존 user1~user9) ---
echo "[1/1] 회원 생성 (loaduser001~loaduser100)"

SUCCESS=0
FAIL=0
for i in $(seq 1 100); do
  PADDED=$(printf "%03d" $i)
  # birthDate 범위: 1970~2000
  YEAR=$((1970 + (i % 30)))
  MONTH=$(printf "%02d" $(( (i % 12) + 1 )))
  DAY=$(printf "%02d" $(( (i % 28) + 1 )))

  RESULT=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/v1/members" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\":\"lu${PADDED}\",\"password\":\"Password1!\",\"name\":\"부하유저${PADDED}\",\"birthDate\":\"${YEAR}-${MONTH}-${DAY}\",\"email\":\"load${PADDED}@test.com\"}")

  if [ "$RESULT" = "200" ] || [ "$RESULT" = "201" ]; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
done

echo "  성공: ${SUCCESS}명, 실패: ${FAIL}명"
echo ""

# --- 검증 ---
echo "[검증] 인증 확인"
AUTH_RESULT=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/members/me" \
  -H "X-Loopers-LoginId: lu001" \
  -H "X-Loopers-LoginPw: Password1!")
echo "  lu001 인증: HTTP $AUTH_RESULT"

echo ""
echo "=== 시드 완료 ==="
echo "테스트 계정: lu001~lu100 / Password1!"
