# Order Queue Polling Fixture Guide (2026-04-03)

## 목적
- Cycle 1 Polling 부하 검증에서 단일 사용자 반복 조회가 아니라, 여러 사용자가 각각 자신의 순번을 조회하도록 fixture를 준비한다.

## 준비 스크립트
- `k6/scripts/setup-order-queue-polling-fixture.sh`

## 실행 예시
```bash
BASE_URL=http://localhost:8080 \
LOGIN_ID_PREFIX=orderqueue \
LOGIN_PW=Password1! \
USER_COUNT=100 \
bash k6/scripts/setup-order-queue-polling-fixture.sh
```

## k6 실행 예시
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e LOGIN_ID_PREFIX=orderqueue \
  -e LOGIN_PW=Password1! \
  -e SCENARIO_NAME=queue-polling-100 \
  -e VUS=100 \
  -e DURATION=30s \
  -e POLLING_SECONDS=1 \
  k6/scripts/order-queue-polling-load.js
```

## 규칙
- 각 VU는 `orderqueue00001`, `orderqueue00002` 같은 고유 loginId를 사용한다.
- fixture 스크립트는 회원가입과 queue 진입만 수행한다.
- Step 2/3 정책상 token 발급 여부와 무관하게 queue realtime 조회 baseline을 만들 수 있다.
- Cycle 1 Polling 부하 검증에서는 scheduler를 비활성화해 사용자를 `WAITING` 상태로 유지한다.
