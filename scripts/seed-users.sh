#!/bin/bash
# user1~user100 + tokenuser 등록 스크립트
# 실행: bash scripts/seed-users.sh

BASE_URL="http://localhost:8080"

register() {
  local LOGIN_ID=$1
  local RES=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/v1/members" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\":\"${LOGIN_ID}\",\"password\":\"Test1234!\",\"name\":\"Tester\",\"birthday\":\"19900101\",\"email\":\"${LOGIN_ID}@test.com\"}")
  if [ "$RES" == "200" ]; then
    echo "OK: ${LOGIN_ID}"
  else
    echo "SKIP(${RES}): ${LOGIN_ID}"
  fi
}

for i in $(seq 1 100); do
  register "user${i}"
done

register "tokenuser"

echo "완료"
