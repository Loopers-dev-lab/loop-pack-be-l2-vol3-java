# Order Queue Polling Load Guide (2026-04-03)

## 목적
- Cycle 1의 single instance Polling 부하 검증을 바로 시작할 수 있도록 k6 실행 초안을 정의한다.
- 정책 기반 polling과 고정 1초 polling을 같은 스크립트로 비교할 수 있게 한다.

## 대상 API
- `GET /api/v1/order-queue/me/realtime`

## 준비 조건
- 대상 서버는 single instance로 실행한다.
- Step 1~3 구현이 반영된 `week8-clean` 기준 애플리케이션을 실행한다.
- 테스트 유저는 이미 queue에 진입해 있거나, 별도 fixture로 준비한다.
- follow-up Cycle 2부터는 `docs/performance/k6/multi-instance-coordinator-model-20260403.md` 를 기준으로 coordinator 1대 / worker N대 모델을 사용한다.

## k6 스크립트
- `k6/scripts/order-queue-polling-load.js`

## 실행 예시

### Scenario A. 대기자 100명 / 정책 interval 가정
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e LOGIN_ID=orderuser1 \
  -e LOGIN_PW=Test1234!@ \
  -e SCENARIO_NAME=queue-polling-100 \
  -e VUS=100 \
  -e DURATION=30s \
  -e POLLING_SECONDS=1 \
  k6/scripts/order-queue-polling-load.js
```

### Scenario B. 대기자 1000명 / 정책 interval 가정
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e LOGIN_ID=orderuser1 \
  -e LOGIN_PW=Test1234!@ \
  -e SCENARIO_NAME=queue-polling-1000 \
  -e VUS=1000 \
  -e DURATION=30s \
  -e POLLING_SECONDS=3 \
  k6/scripts/order-queue-polling-load.js
```

### Scenario C. 대기자 10000명 / 정책 interval 가정
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e LOGIN_ID=orderuser1 \
  -e LOGIN_PW=Test1234!@ \
  -e SCENARIO_NAME=queue-polling-10000 \
  -e VUS=10000 \
  -e DURATION=30s \
  -e POLLING_SECONDS=5 \
  k6/scripts/order-queue-polling-load.js
```

### Scenario D. 고정 1초 polling 비교
```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e LOGIN_ID=orderuser1 \
  -e LOGIN_PW=Test1234!@ \
  -e SCENARIO_NAME=queue-polling-fixed-1s \
  -e VUS=1000 \
  -e DURATION=30s \
  -e POLLING_SECONDS=1 \
  k6/scripts/order-queue-polling-load.js
```

## 측정 항목
- HTTP req/s
- error rate
- p95 / p99 응답 시간
- Redis command 수(`ZRANK`, claim/token key 조회)
- recommended polling interval 분포

## 결과 정리 포맷
| scenario | vus | polling_seconds | req/s | error% | p95 | p99 | redis_ops | note |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |

## 현재 결과
| scenario | vus | polling_seconds | req/s | error% | p95 | p99 | redis_ops | note |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| queue-polling-20-fixture | 20 | 1 | 17.53 | 0.00% | 264.86ms | 275.07ms | 미측정 | 다중 사용자 fixture baseline |
| queue-polling-100 | 100 | 1 | 73.09 | 0.00% | 896.77ms | 1.19s | 미측정 | scheduler 비활성화 후 유효 baseline |
| queue-polling-1000 | 1000 | 3 | 82.39 | 20.32% | 10.6s | 12.03s | 미측정 | 단일 인스턴스 한계 도달, 정책 보정 필요 |
| queue-polling-1000-multi-api1 | 1000 | 3 | 82.06 | 15.44% | 11.22s | 12.73s | 미측정 | worker 1대 대상 재실행, 단일 worker 병목으로 개선 제한적 |
| queue-polling-1000-multi-api1-5s | 1000 | 5 | 47.39 | 17.21% | 18.66s | 21.42s | 미측정 | interval 상향만으로는 개선 실패 |
| queue-polling-1000-multi-api1-8s | 1000 | 8 | 33.97 | 17.48% | 19.87s | 21.63s | 미측정 | 더 느린 polling도 근본 병목을 해소하지 못함 |

## 현재 해석
- 100명 baseline은 현재 단일 인스턴스에서 수용 가능하다.
- 1000명 / 3초 polling에서는 에러율과 tail latency가 급격히 증가해 현재 단일 인스턴스 기준 허용 범위를 넘는다.
- 1000-user를 worker 1대에 다시 실행해도 p95와 에러율이 크게 개선되지 않아, 단순 인스턴스 분리만으로는 부족하고 실제 분산 라우팅 또는 polling 정책 추가 보정이 필요하다.
- `5초`, `8초`로 polling interval을 늘려도 p95와 에러율이 개선되지 않아, 현재 병목은 polling interval만의 문제가 아니라 worker 단일 타깃/분산 라우팅 부재/하류 처리량 한계와 더 관련이 깊다.
- 10000명 시나리오는 fixture 준비 비용과 단일 인스턴스 한계를 고려해 별도 cycle 또는 fixture 최적화 후 진행하는 것이 적절하다.

## polling 정책 보정안

### 1차 보정안
- `1 ~ 100`: `1초`
- `101 ~ 1000`: `3초`
- `1000+`: `5초` 이상으로 상향

### 권장 보정 방향
- `1000+` 구간은 현재 `3초` 가정보다 느리게 가져가는 것이 안전하다.
- `recommendedPollingIntervalSeconds`의 상한은 현재 `5초`보다 더 높게 열 수 있다.
  - 후보: `8초 ~ 10초`
- polling interval은 순번만 보지 말고 최근 부하 상태도 반영하는 방향이 필요하다.
  - 입력 후보:
    - 최근 `p95`
    - 최근 에러율
    - 현재 worker 수
    - `effectiveOrderThroughputPerSecond`

### 다음 실험 제안
- 실제 로드밸런싱 환경에서 worker 분산 호출 비교
- `server-count`, `order-tps-per-server` 보정값 재조정
- polling 정책보다 하류 처리량/라우팅 구조를 우선 확인

## 현재 결론
- 단일 인스턴스에서는 `1000-user / 3초 polling`이 과도하다.
- multi-instance라도 worker 1대 단독 타깃으로는 큰 개선이 없다.
- 따라서 다음 단계는 polling interval 자체보다 처리량 입력값 보정과 스케줄러 배치 크기 산정에 초점을 맞춘 재실험이다.

## 다음 사이클 입력 메모
- 실제 분산 라우팅 테스트는 이번 범위에서 제외한다.
- 다음 사이클에서는 스케줄러 배치 크기 산정을 우선 다룬다.
- 현재 가정값:
  - DB 커넥션 풀: `50`
  - 주문 1건 평균 처리 시간: `250ms`
  - 이론 최대 TPS: `50 / 0.25 = 200 TPS`
  - 안전 마진 70% 적용: `140 TPS`
  - 100ms마다 발급 수: 약 `14명`
- 즉 다음 사이클 기본 실험값은 `100ms마다 14명 발급`으로 시작한다.

## 다음 입력으로 넘길 것
- Cycle 2에 넘길 값
  - single instance 기준 acceptable req/s
  - 병목 구간
  - interval 정책 보정안
