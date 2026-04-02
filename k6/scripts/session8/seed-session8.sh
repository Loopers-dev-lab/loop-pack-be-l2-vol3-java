#!/bin/bash
# Session 8 k6 부하 테스트 시드 데이터
# 사전 조건: commerce-api가 localhost:8080에서 실행 중
BASE="http://localhost:8080"
TOTAL=${1:-10000}
PARALLEL=50

echo "=== Session 8 시드 데이터 적재 (${TOTAL}명, ${PARALLEL} 병렬) ==="

register_user() {
    local i=$1
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "$BASE/api/v1/users" \
        -H "Content-Type: application/json" \
        -d "{\"loginId\":\"k6user$i\",\"password\":\"Test1234!\",\"userName\":\"LoadUser$i\",\"birthday\":\"19900101\",\"email\":\"k6user$i@test.com\",\"address\":\"Seoul\"}")

    if [ "$status" != "201" ] && [ "$status" != "409" ]; then
        echo "WARN: k6user$i failed with HTTP $status" >&2
    fi
}
export -f register_user
export BASE

seq 1 "$TOTAL" | xargs -P "$PARALLEL" -I {} bash -c 'register_user {}'

echo "=== 완료: ${TOTAL}명 시드 적재 ==="
