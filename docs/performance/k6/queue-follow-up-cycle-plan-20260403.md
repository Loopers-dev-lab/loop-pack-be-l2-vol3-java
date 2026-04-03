# Queue Follow-up Cycle Plan (2026-04-03)

## 목적
- 이미 구현이 끝난 Step 1~3은 다시 건드리지 않는다.
- 후속 검증/비교가 필요한 항목만 작은 cycle로 나눠 순서 있게 진행한다.
- 특히 Step 2 딥초안 보강용으로 admission 전략 A/B/C 비교를 남겨둔다.

## 이미 끝난 범위
- Step 1: Redis Sorted Set 기반 대기열 진입/상태 조회
- Step 2: 입장 토큰, claim, scheduler, 주문 게이트
- Step 3: 실시간 순번 조회 API, polling 정책 함수, polling 부하 baseline

## 남은 후속 범위

### 1. Polling 부하 검증
- 단일 인스턴스 기준 baseline과 정책 보정 결과 기록
- 결과는 `docs/performance/k6/order-queue-polling-load-guide-20260403.md`에 누적

### 2. 다중 인스턴스 검증
- coordinator 1대 + worker N대 운영 모델 기준 정합성 확인
- 결과는 `docs/performance/k6/multi-instance-load-guide-20260403.md`에 누적

### 3. A/B/C admission 전략 비교
- 비교 대상
  - A: key TTL 기반 admission
  - B: Hash field TTL 기반 admission
  - C: hybrid 구조
- 목표
  - 구현 복잡도, 정합성, 운영 편의, 메모리/관측성을 비교
  - Step 2 딥초안(`/Users/anseonghun/Documents/loopers/문서/2026-04-03_토큰하나로질서를만든다_주문대기열_Step2_딥초안.md`) 보강용 근거 확보
- 테스트 포인트
  - 동일 사용자 중복 발급 방지
  - claim/token TTL 동작
  - active token cap 제어
  - queue/claim/token 정합성
  - 운영 중 집계 가능성(active count, expiry count 등)
- 산출물
  - 전략 비교표
  - 유지 기본안 1개 / 후속 검증안 1개 선정 메모

### 4. Redis 장애 fallback
- 전면 차단 / bypass / fallback queue 중 운영 방침 결정

## 순서
1. Polling 부하 baseline/보정
2. 다중 인스턴스 baseline
3. A/B/C admission 전략 비교
4. Redis 장애 fallback

## 메모
- `SSE 전환 판단`은 범위에서 제외한다.
- Herd 완화는 이번 라운드 기본안으로 `100ms마다 14명 발급 + jitter`를 채택했다.
