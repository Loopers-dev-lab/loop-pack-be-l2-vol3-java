# 성능 벤치마크 — 공통 절차 / 시나리오

규모별 최종 보고서: [benchmark-100k.md](benchmark-100k.md) · [benchmark-200k.md](benchmark-200k.md) · [benchmark-500k.md](benchmark-500k.md) · [benchmark-1m.md](benchmark-1m.md)

---

## 1. 블랙프라이데이 시나리오 요약

- **도메인**: 패션 커머스 (직잭/에이블리/무신사 스타일)
- **타이밍**: 00시 정각 선착순 타임딜, 1분 내 TPS 1,000 → 15,000 급증
- **목표**: 단순히 “버티는지”가 아니라, **어느 병목 지점에서 임계치에 도달하는지** 정의하고 관측

### 1.1 API별 목표 TPS

| **분류** | **API** | **평시 TPS** | **블랙프라이데이 목표 TPS** | **특징** |
| --- | --- | --- | --- | --- |
| **조회 (Read)** | 상품 목록 조회 | 500 | 10,000+ | 메인 홈, 카테고리, 검색 결과 포함 |
|  | 상품 상세 조회 | 300 | 7,000+ | 옵션·재고 포함 PDP |
|  | 주문 목록/상세 | 50 | 1,000+ | 마이페이지 배송 상태 확인 |
| **쓰기 (Write)** | 좋아요 등록/취소 | 100 | 3,000+ | Write-heavy, 관심 상품 저장 |
|  | 주문 취소 | 10 | 200 | 결제 실패·변심 등 |
| **관리 (Admin)** | 상품 수정/삭제 | 5 | 10 | 운영자 작업 |
|  | 좋아요 순 상품 조회 | 100 | 5,000+ | 실시간 랭킹/인기 목록 |

### 1.2 트래픽 해석 관점

- **트래픽 변동폭**: 평시 대비 20~30배 수준. **인덱스·캐시·랭킹 구조** 없이는 DB/Redis 모두 병목 가능.
- **Read vs Write**: 목표 TPS 기준으로 Read(상품/주문 조회)가 압도적이므로, **상품 목록/상세 + 랭킹을 Redis/비정규화로 어떻게 떼어내는지**가 핵심.
- **쓰기 부하**: 좋아요 API는 주문 취소보다 15배 이상 쓰기 QPS를 요구. 향후 `like_count` 비정규화까지 고려하면, **Redis/비동기·수직 분리** 등 쓰기 경로 설계 중요.

### 1.3 워크로드 믹스 예시

부하 도구(k6)는 혼합 트래픽으로 구성한다 (예시 비율).

- 상품 목록: 40% (`GET /api/v1/products?sort=...`)
- 상품 상세: 30% (`GET /api/v1/products/{id}`)
- 주문 목록/상세: 10% (`GET /api/v1/orders`, `GET /api/v1/orders/{id}`)
- 좋아요 등록/취소: 15% (`POST/DELETE /api/v1/likes`)
- 좋아요 순 랭킹: 5% (`sort=likes_desc` 또는 별도 랭킹 API)

> 비율은 k6 시나리오에서 VU별 시나리오 구성으로 조정 가능.  
> 단일 엔드포인트 부하 테스트 + 혼합 트래픽 시나리오를 각각 돌려서 비교한다.

### 1.4 SLO / 임계 기준

- **상품 목록 / 상세**: p95 ≤ 200ms, p99 ≤ 400ms
- **주문 목록 / 상세 / 좋아요 쓰기**: p95 ≤ 300ms, p99 ≤ 600ms
- **에러율**: 전체 요청 대비 1% 미만 (5xx, 타임아웃 포함)

측정 결과에서 **p95/p99가 SLO를 넘기기 시작하는 TPS 구간**을 “임계치 도달 지점”으로 기록한다.

---

## 2. 공통 테스트 조건

- **테스트 환경**: Spring Boot(commerce-api) 단일 인스턴스, MySQL, Redis, 부하 도구 k6
- **측정 지표**: avg / p95 / p99 / max (ms), 에러율
- **인증 필요 API** (주문 목록·상세, 좋아요): 헤더 `X-Loopers-LoginId` 필요. 테스트 유저 사전 생성 후 k6에서 동일 헤더 사용.
- **Warm / Cold 캐시**:
  - Cold: Redis 캐시 비운 뒤 곧바로 5~10분 부하 (캐시 미스/채우는 비용 관찰)
  - Warm: 5~10분 워밍업 이후 10분 측정 (캐시가 어느 정도 찬 상태 기준 p95/p99 관찰)

---

## 3. 인프라 기동 / 인증 설정

### 3.1 인프라 기동 (MySQL / Redis / Kafka)

```bash
docker-compose -f ./docker/infra-compose.yml up -d
```

- MySQL: localhost:3306, DB `loopers`, 계정 `application` / `application`
- Redis: master localhost:6379, replica localhost:6380

### 3.2 인증 설정 (주문·좋아요 테스트 전)

1. **API 포트 확인**: bootRun 로그의 Tomcat 포트. 8080이 nginx면 8081 등 사용.
2. **테스트 유저 생성 + k6 주문 목록 검증·실행** (한 번에):
   ```bash
   ./docs/load-test/local-auth-setup.sh 8080 perfuser
   ```
   포트는 실제 API 포트로. 404 나오면 commerce-api를 8081 등으로 직접 띄우고 `8081` 로 실행.

k6 스크립트에서 `BASE_URL`, `LOGIN_ID` 환경 변수로 동일 값 사용.

---

## 4. 주요 병목 가설 (AS-IS 기준)

벤치마크 결과는 각 `benchmark-*.md` 에서 실제 수치와 함께 검증하며, 아래 가설을 기준으로 분석한다.

- **DB Slow Query**
  - 상품 목록 OFFSET 딥 페이징 + 정렬(ORDER BY created_at/price) → page 커질수록 filesort/temporary 사용, p95/p99 증가.
  - 주문 목록 user_id + ordered_at 범위 스캔 + 정렬, 상태(status) 조건에 따라 인덱스 미활용 가능성.
- **좋아요 순 정렬 집계 폭발**
  - `sort=likes_desc` 쿼리가 `JOIN likes + GROUP BY + ORDER BY COUNT` 구조로 동작해, TPS 5,000+에서 CPU/I/O 집약적 집계가 폭증.
- **캐시 미적용**
  - 메인 홈/랭킹/인기 PDP가 같은 조건으로 반복 조회되어도 Redis/로컬 캐시 없이 DB에 직접 Hit.
- **쓰기 경합 / Row-level Lock**
  - 좋아요 INSERT/DELETE가 특정 인기 상품에 몰리면 likes 인덱스와 (향후) `product.like_count` 갱신 시 Row-level Lock 경쟁 가능.
- **Redis CPU Overload (향후 도입 시)**
  - 좋아요·랭킹을 Redis 카운터나 Sorted Set으로 처리하면, 단일 Redis 인스턴스 CPU 100% 근접 가능성.  

> 실제 부하 테스트에서 위 가설 중 어느 것이 먼저, 어느 구간에서 터지는지(p95/p99·CPU·슬로우쿼리 등)를 `benchmark-*.md` 와 함께 정리한다.

