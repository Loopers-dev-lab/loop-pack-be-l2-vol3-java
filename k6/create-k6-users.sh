#!/bin/bash
# k6 테스트 유저 50명 생성
BASE="http://localhost:8080"

echo "=== k6 테스트 유저 50명 생성 ==="
SUCCESS=0
FAIL=0
for i in $(seq 1 50); do
  RES=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/users" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\":\"k6user$i\",\"password\":\"Test1234!\",\"userName\":\"K6User$i\",\"birthday\":\"19900101\",\"email\":\"k6user$i@test.com\",\"address\":\"Seoul\"}")
  if [ "$RES" = "200" ]; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
done
echo "완료: 성공=$SUCCESS, 실패=$FAIL"
