# Admission Strategy A/B/C Plan (2026-04-03)

## 목적
- admission 저장 전략 A/B/C를 공정한 조건에서 비교한다.
- 결과는 Step 2 딥초안 보강과 후속 기본안/검증안 선정에 사용한다.

## 비교 범위 고정
- queue 저장/조회는 Step 1에서 검증된 기존 ZSET 구현을 공통으로 유지한다.
- A/B/C 비교 대상은 queue가 아니라 claim/token admission 저장 전략이다.

## 비교 대상

### A. key TTL 기반 admission
- claim: `order:admission:claim:{memberId}`
- token: `order:admission:token:{memberId}`
- active count: `order:admission:active:v1` ZSET
- 현재 구현이 이미 존재하므로 baseline으로 사용한다.

### B. Hash field TTL 기반 admission
- key 예시: `order:admission:bucket:{instanceId}`
- field: `memberId` 또는 `tokenId`
- value: `userId|token|expireAt` 직렬화 값
- claim/token TTL은 `HEXPIRE` 계열을 전제로 한다.
- active count는 `HLEN`과 별도 limit key로 관리한다.
- 현재 결정: 기본 전략 채택

### C. hybrid 구조
- token key TTL + active ZSET 또는 token key TTL + bucket hash
- 목적은 A의 단순성과 B의 관측성 사이 절충안 확인

## 최소 구현 범위

### 공통 인터페이스
- `tryClaim(memberId, nowMillis, claimTtlMillis)`
- `hasValidToken(memberId)`
- `hasActiveClaim(memberId)`
- `issueToken(memberId, nowMillis, tokenTtlMillis)`
- `countActiveTokens(nowMillis)`
- `removeToken(memberId)`
- `clearClaim(memberId)`

### A 최소 구현
- 현재 구현 사용

### B 최소 구현
- 기존 `AdmissionStorageStrategy` 구현체 추가
- `RedisAdmissionStorageRepository`를 건드리기보다 B 전용 repository/strategy 추가
- 테스트는 Redis 7.4+ 기능 사용 가능 여부를 먼저 확인한다.

### C 최소 구현
- token key TTL은 유지
- active count용 보조 자료구조만 별도 사용
- A를 최대한 재사용하고 관측성 보완만 추가한다.

## 공정한 비교 조건
- 동일 queue fixture
- 동일 user count
- 동일 claim/token TTL
- 동일 batch size / maxActiveAdmissions
- 동일 endpoint / 동일 주문 흐름
- 동일 측정 기간
- fixture 준비는 `k6/scripts/setup-admission-strategy-fixture.sh`로 고정한다.

## 비교표 형식
| 전략 | claim 저장 | token 저장 | active count | TTL 관리 | 구현 복잡도 | 정합성 리스크 | 운영 집계 편의 | 메모 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 테스트 포인트
- 동일 사용자 중복 발급 방지
- claim/token TTL 만료 후 재진입 가능 여부
- active token cap 제어
- queue/claim/token 정합성
- 운영 중 active/expiry 집계 가능성

## 완료 기준
- 전략 비교표 작성
- 유지 기본안 1개 선정
- 후속 검증안 1개 선정

## 현재 결론
- 기본 전략은 B(Hash field TTL)로 전환한다.
- A(key TTL)는 이전 구현 및 회귀 비교 기준으로 남긴다.
- C(hybrid)는 필요 시 후속 검증안으로 유지한다.
