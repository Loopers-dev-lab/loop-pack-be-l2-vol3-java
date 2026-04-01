#!/bin/bash
# Session 8 k6 부하 테스트 시드 데이터
# 사전 조건: commerce-api가 localhost:8080에서 실행 중
BASE="http://localhost:8080"

echo "=== Session 8 시드 데이터 적재 ==="

# 10,000 유저 생성 (k6user1 ~ k6user10000)
for i in $(seq 1 10000); do
  curl -s -X POST "$BASE/api/v1/users" \
    -H "Content-Type: application/json" \
    -d "{\"loginId\":\"k6user$i\",\"password\":\"Test1234!\",\"userName\":\"LoadUser$i\",\"birthday\":\"19900101\",\"email\":\"k6user$i@test.com\",\"address\":\"Seoul\"}" > /dev/null

  if [ $((i % 1000)) -eq 0 ]; then
    echo "  $i / 10000 유저 생성 완료"
  fi
done

echo "=== 완료 ==="
