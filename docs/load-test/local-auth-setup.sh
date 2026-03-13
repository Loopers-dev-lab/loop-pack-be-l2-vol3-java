#!/usr/bin/env bash
# 로컬에서 인증 필요 API 부하 테스트 전 준비 + 주문 목록/상세 k6 실행
# 사용: ./docs/load-test/local-auth-setup.sh [PORT] [LOGIN_ID]
# 예:   ./docs/load-test/local-auth-setup.sh 8081 perfuser
# 전제: commerce-api가 이미 해당 포트에서 기동 중 (bootRun 로그에서 Tomcat 포트 확인)

set -e
PORT="${1:-8080}"
LOGIN_ID="${2:-perfuser}"
BASE_URL="http://localhost:${PORT}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "=== API 포트 확인: $BASE_URL ==="
if ! curl -sf "${BASE_URL}/api/v1/products?page=0&size=1" | grep -q '"result"'; then
  echo "경고: ${BASE_URL} 에서 API JSON 응답이 없습니다. 404(nginx)면 다른 포트(예: 8081)를 넣어 보세요."
  echo "예: $0 8081 $LOGIN_ID"
  exit 1
fi

echo "=== 테스트 유저 생성: $LOGIN_ID ==="
EMAIL="${LOGIN_ID}@perf.local"
RES=$(curl -s -X POST "${BASE_URL}/api/v1/users" \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"${LOGIN_ID}\",\"password\":\"SecurePass1!\",\"email\":\"${EMAIL}\",\"birthDate\":\"1990-01-15\",\"gender\":\"MALE\"}")
if echo "$RES" | grep -q '"result":"SUCCESS"'; then
  echo "회원 가입 성공: $LOGIN_ID"
elif echo "$RES" | grep -q 'CONFLICT\|이미 존재'; then
  echo "이미 존재하는 유저: $LOGIN_ID (계속 진행)"
else
  echo "회원 가입 실패. 응답: $RES"
  exit 1
fi

echo "=== 주문 목록 API 확인 (GET /api/v1/orders) ==="
ORDER_LIST_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "X-Loopers-LoginId: ${LOGIN_ID}" \
  "${BASE_URL}/api/v1/orders?start=2020-01-01T00:00:00Z&end=2030-12-31T23:59:59Z&page=0&size=20")
if [ "$ORDER_LIST_STATUS" != "200" ]; then
  echo "GET /api/v1/orders 응답: HTTP $ORDER_LIST_STATUS (200이어야 함)"
  echo "  - 404: nginx가 해당 경로를 프록시하지 않을 수 있음. commerce-api를 직접 기동한 포트(예: 8081)로 실행해 보세요."
  echo "  - 401: X-Loopers-LoginId 헤더 또는 유저 확인."
  exit 1
fi
echo "주문 목록 API 확인: HTTP 200"

echo "=== k6 주문 목록 부하 테스트 (30s) ==="
export BASE_URL LOGIN_ID
k6 run -e "BASE_URL=${BASE_URL}" -e "LOGIN_ID=${LOGIN_ID}" --vus 50 --duration 30s docs/load-test/k6-orders-list.js

echo "=== k6 주문 상세 부하 테스트 (30s) ==="
# 주문 상세는 MIN_ORDER_ID/MAX_ORDER_ID 범위 내에서 무작위로 조회합니다.
# perf용 환경에서 주문이 충분히 생성되어 있다면, 아래 기본값(1~100)을 필요에 맞게 조정하세요.
k6 run \
  -e "BASE_URL=${BASE_URL}" \
  -e "LOGIN_ID=${LOGIN_ID}" \
  -e "MIN_ORDER_ID=${MIN_ORDER_ID:-1}" \
  -e "MAX_ORDER_ID=${MAX_ORDER_ID:-100}" \
  --vus 50 --duration 30s \
  docs/load-test/k6-order-detail.js

echo "=== 완료 ==="
