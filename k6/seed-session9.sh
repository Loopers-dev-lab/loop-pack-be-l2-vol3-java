#!/bin/bash
# Session 9 시드 데이터 생성
# 사용법: bash k6/seed-session9.sh
#
# 생성 데이터:
#   - 100 테스트 유저 (k6rank1~k6rank100, password1!)
#   - 5 브랜드 (seed.sh에서 이미 생성된 것 사용)
#   - 100 상품 (seed.sh에서 이미 생성된 것 사용)
#
# 전제: commerce-api가 localhost:8080에서 실행 중

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_HEADER="X-Loopers-Ldap: loopers.admin"
USER_COUNT=100

echo "=== Session 9 시드 데이터 생성 ==="
echo "BASE_URL: ${BASE_URL}"
echo ""

# 1. 테스트 유저 생성
echo "--- 유저 ${USER_COUNT}명 생성 ---"
for i in $(seq 1 $USER_COUNT); do
    LOGIN_ID="k6rank${i}"
    PASSWORD="password1!"
    USER_NAME="랭킹테스터${i}"
    BIRTHDAY="19900101"

    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/users" \
        -H "Content-Type: application/json" \
        -d "{
            \"loginId\": \"${LOGIN_ID}\",
            \"password\": \"${PASSWORD}\",
            \"userName\": \"${USER_NAME}\",
            \"birthday\": \"${BIRTHDAY}\"
        }")

    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
        if [ $((i % 20)) -eq 0 ]; then
            echo "  유저 생성 진행: ${i}/${USER_COUNT}"
        fi
    elif [ "$HTTP_CODE" = "409" ]; then
        : # 이미 존재 — 무시
    else
        echo "  WARNING: 유저 ${LOGIN_ID} 생성 실패 (HTTP ${HTTP_CODE})"
    fi
done
echo "  유저 생성 완료: ${USER_COUNT}명"

echo ""
echo "=== 시드 데이터 생성 완료 ==="
echo ""
echo "다음 스크립트 실행 가능:"
echo "  k6 run k6/scripts/session9/ranking-e2e-accuracy.js     # L1: E2E 정확성"
echo "  k6 run k6/scripts/session9/ranking-api-load.js          # L2: API 부하"
echo "  k6 run k6/scripts/session9/ranking-event-throughput.js   # L3: 이벤트 처리량"
echo "  k6 run k6/scripts/session9/ranking-weight-accuracy.js    # L4: 가중치 정확성"
echo "  k6 run k6/scripts/session9/ranking-mixed-load.js         # L5: 운영 시뮬레이션"
