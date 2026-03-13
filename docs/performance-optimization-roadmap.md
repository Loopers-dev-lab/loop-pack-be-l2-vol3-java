## 성능 개선 로드맵 — PLP·좋아요·주문 우선 전략

> 이 문서는 실제 벤치마크 결과(100k/200k/500k/1m), k6 스크립트, 그리고 멘토 문서(Devin/ALEN/LEN/Kev)를 종합해, **내 프로젝트에서 가장 위험한 병목 구간**을 우선순위로 정하고 **구체적인 대안과 트레이드오프**를 정리한 로드맵이다.

- **SLO**: 고객 조회 API(상품 목록/상세, 주문 목록/상세, 좋아요 랭킹)의 **P95 응답 시간 200ms 이하**(벤치마크 스크립트 목표와 동일)이며, **p99도 함께 모니터링**한다. (Kev)
- **원칙**
  - **SOT = DB**: 캐시는 항상 부하 차단용 파생 데이터. 장애 시 DB만으로 서비스가 가능해야 한다. (Devin/ALEN 공통)
  - **캐시는 변경 직후 Evict** + **TTL + Jitter**를 기본으로, 필요하면 조회 시 TTL 연장(Sliding)까지 고려한다. (Devin)
  - **인덱스는 읽기를 위해 쓰기를 포기하는 선택**이며, 개수·컬럼 순서(특히 **Equal → Range 순서**), 카디널리티/선택도, **데이터 분포·생명주기**에 따른 쓰기 병목을 항상 함께 본다. (ALEN/LEN/Kev)
  - **캐시는 히트율과 가성비**(파레토 10:90, 유저 기반 데이터의 낮은 히트율)를 기준으로, “정말 느린 쿼리·핫 키” 위주로 적용한다. (Kev)
  - **트랜잭션은 길이보다 원자성**(한 번에 되거나 전부 롤백)이 중요하고, 동시성 문제는 가능하면 **아토믹 업데이트**로 해결한다. (Devin)

---

## 1. 리스크 맵 & 우선순위 요약

### 1.1 벤치마크·코드 기준 우선순위

- **1순위 — 좋아요 쓰기(API: POST/DELETE /api/v1/likes)**
  - **증거**
    - k6-likes-write: TPS 800~900 수준에서 p99 100~200ms, max 500~1000ms, 에러율 100%(비200), 비고: **Row lock 경합 관찰** (200k/1m 공통).
    - 상위 상품 집중 시나리오에서 특정 product row에 INSERT/DELETE가 몰림.
  - **구조상 리스크**
    - 현재는 likes 테이블에만 INSERT/DELETE지만, 추후 `product.like_count`까지 같은 트랜잭션에서 UPDATE하면 **단일 상품 row가 더 큰 핫스팟**이 된다.

- **2순위 — 상품 목록 PLP (정렬 + OFFSET, 특히 likes_desc & 딥 페이징)**
  - **증거**
    - PLP 부하 스크립트가 정렬(latest/price_asc/price_desc/likes_desc) × page(0~2, 50~100)를 명시적으로 때림.
    - likes_desc 행: 항상 비고 **JOIN+GROUP BY**, 100k~1m 전 구간에서 동일 패턴.
    - latest 50~100 page: 모든 스케일에서 비고 **OFFSET 딥페이징**으로 명시, p95/p99가 정렬 0~2page 대비 상승.
  - **구조상 리스크**
    - `LEFT JOIN likes` + `GROUP BY` + `ORDER BY COUNT(*)` + OFFSET → MySQL이 임시 테이블 + filesort 사용, 인덱스로 커버하기 어려운 패턴.
    - 최신/가격 정렬도 PageRequest 기반 OFFSET으로 page 50~100에서 스캔 낭비.

- **3순위 — PDP(상품 상세), 주문 목록/상세**
  - **PDP**
    - 벤치마크 p95는 10~20ms 수준이지만, max 100~200ms 꼬리가 관측.
    - **캐시 없이 product + brand + likes COUNT 3쿼리**가 그대로 RDB에 가는 구조라, 실제 서비스에서 특정 핫 상품에 트래픽이 몰리면 병목 후보.
  - **주문 목록/상세**
    - 현재 벤치마크는 시드 문제로 에러율 100%(비200)라 latency 수치는 신뢰 어려움.
    - 쿼리 패턴 자체가 `user_id + 기간 + status + ORDER BY ordered_at DESC + OFFSET` 딥 페이징으로, 데이터/기간이 커지면 PLP와 유사한 병목 구조.

이 로드맵은 위 **1~3순위**를 기준으로, 각 영역별로 **구체적인 대안(2~3개)** 과 **트레이드오프**를 나열하고, 현 프로젝트에서 **우선 채택할 안**을 명시한다.  
또한 Kev 멘토 관점에 따라, 이후 부하 테스트에서는 각 변화(인덱스/비정규화/캐시)를 **단계별·시나리오별로 분리**해 p95/p99와 “몇 배 개선되었는지”를 수치로 증명하는 것을 기본 원칙으로 삼는다.

### 1.2 API·문제별 제시된 대안 및 채택안 요약

| API/문제                                    | 제시된 대안                                                                                                                                                    | 채택안                                                   | 비고                               |
| ------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- | ---------------------------------- |
| **좋아요 쓰기** (POST/DELETE /api/v1/likes) | **A** product.like_count 비정규화 + 아토믹 업데이트<br>**B** product_stats 수직 분리 (like_count만 별도 테이블)<br>**C** 메모리 버퍼링 + 배치(DB Delta Update) | **A+B** (product_stats 도입, like_count 아토믹 업데이트) | §2.5. C는 중·장기 옵션.            |
| **좋아요 수 정렬 구조** (likes_desc)        | **A** product_stats.like_count 기반 정렬<br>**B** 조회 전용 집계 테이블/Materialized View<br>**C** Redis Sorted Set 랭킹                                       | **A** (비정규화 유지, 2장 구조 재사용)                   | §2.6, §3.2.5.                      |
| **PLP latest/price 정렬**                   | **A** 복합 인덱스 + OFFSET 유지<br>**B** Keyset Pagination(Seek) + 커서<br>**C** 1페이지 캐시(조건별) + OFFSET 유지                                            | **A+C** (인덱스 + PLP 1페이지 Redis 캐시)                | §3.1.5. B는 API 스펙 변경 시 검토. |
| **PLP likes_desc**                          | **A** product_stats.like_count JOIN + ORDER BY<br>**B** MV/집계 테이블<br>**C** Redis Sorted Set                                                               | **A** (2장 product_stats와 동일)                         | §3.2.5.                            |
| **PDP** (GET /api/v1/products/{id})         | **A** Read Model + 캐시(Redis 등)<br>**B** DB 최적화만 유지                                                                                                    | **A** (PDP Redis 캐시, TTL+Jitter, 수정/삭제 시 Evict)   | §4.4.                              |
| **주문 목록/상세**                          | **A** 인덱스 최적화 + 캐시 미적용<br>**B** 유저별 목록 캐시(짧은 TTL)                                                                                          | **A** (인덱스만. SLO 미달 시 B 검토)                     | §5.5.                              |

### 1.3 대안 적용 후 성능 테스트 자동화·결과 기록

- **자동화 스크립트**: `./docs/load-test/run-all-k6-phase.sh [PHASE_LABEL] [SCALE] [DURATION]`
  - 시나리오: PLP(latest/price_asc/likes_desc page0, latest_page50), PDP, likes_write
  - 실행 시 K6가 자동 실행되고, **결과 테이블이 `docs/performance/phase-benchmark-results.md` 문서 끝에 자동 append**됨.
- **Phase ↔ 대안 매핑**
  - **Baseline**: AS-IS (캐시·like_count 비정규화 미적용)
  - **Phase 1**: §2·§3.1·§3.2 채택안 — product_stats + like_count 아토믹 업데이트, PLP likes_desc 쿼리 변경, (선택) latest/price 복합 인덱스
  - **Phase 2**: Phase 1 + §3.1.C — PLP 1페이지 Redis 캐시
  - **Phase 3**: Phase 2 + §4.A — PDP Redis 캐시
- **규모별 실행**: 동일 스크립트에 `[SCALE]`으로 100000 / 200000 / 500000 / 1000000 지정. 결과는 `phase-benchmark-results.md`의 규모별 비교 요약·상세 결과에 수동 복사하거나, append된 블록을 정리해 기입.
- **상세**: [phase-benchmark-results.md](docs/performance/phase-benchmark-results.md) 참고.

---

## 2. 좋아요 쓰기 경로 — Row Lock 경합 완화 & 읽기 구조 준비 (1순위)

### 2.1 AS-IS 정리 & 병목 근거

- **AS-IS**
  - `LikeService.addLike`: `existsByUserIdAndProductId` → `likeRepository.save` (INSERT).
  - `LikeService.removeLike`: `findByUserIdAndProductId` → `likeRepository.delete` (DELETE).
  - `product.like_count` 비정규화는 아직 미도입(TO-BE 후보).
- **병목**
  - k6-likes-write에서 상품 범위를 좁히면 **동일 product_id에 INSERT/DELETE 집중**.
  - PK/UNIQUE 인덱스(user_id, product_id)에 B-Tree split + row-level lock 경합 발생.
  - 향후 `product.like_count`까지 같은 트랜잭션에서 UPDATE하면, 하나의 product row에 **INSERT/DELETE + UPDATE**가 동시에 몰려 더 큰 핫스팟 형성.

### 2.2 대안 A — `product.like_count` 비정규화 + 아토믹 업데이트

- **개념**
  - `product.like_count` 컬럼을 두고, 좋아요 등록/취소 시 **단일 UPDATE 문**으로 `like_count = like_count ± 1` 처리.
  - Devin 멘토가 강조한 “단순 증감은 **아토믹 업데이트** 우선” 전략.

- **구현 스케치**
  - 스키마: `product.like_count INT NOT NULL DEFAULT 0`.
  - 쓰기 흐름 (한 트랜잭션 내부):
    - addLike:
      - likes INSERT (UNIQUE(user_id, product_id)로 중복 방지).
      - 성공 시: `UPDATE product SET like_count = like_count + 1 WHERE id = ?`.
    - removeLike:
      - likes DELETE 성공 시: `UPDATE product SET like_count = like_count - 1 WHERE id = ? AND like_count > 0`.
  - 읽기:
    - PLP likes_desc: `ORDER BY like_count DESC`.
    - PDP: detail에서 단순 SELECT로 like_count 노출.

- **장점**
  - PLP likes_desc 쿼리에서 **JOIN + GROUP BY 제거** → 단순 `ORDER BY like_count`.
  - 인덱스 `(deleted_at, brand_id, like_count DESC)`로 필터+정렬을 한 번에 처리 가능.
  - PDP에서도 `COUNT(*)` 없이 product 한 번으로 좋아요 수 조회.

- **단점 / 트레이드오프**
  - 좋아요마다 product UPDATE 추가 → **쓰기 QPS가 높을수록 product row에 락 경합** 발생.
  - 좋아요 실데이터와 like_count가 어긋날 가능성 → 주기적 재집계 배치 필요.
  - ALEN/LEN이 지적한 것처럼, **핫 상품이 많고 like QPS가 높을 경우** product 테이블이 쓰기 병목 지점이 될 수 있음.

### 2.3 대안 B — 수직 분리: `product_stats`(또는 `product_like_stats`) 테이블

- **개념**
  - 상품 기본 정보(product)와 **좋아요·뷰·리뷰 카운트 등 고QPS 쓰기 컬럼**을 분리.
  - ALEN 멘토의 “like_count 수직 분리” 제안.

- **구조 예시**
  - `product` : id, brand_id, name, price, stock, ...
  - `product_stats` : product_id(PK, FK), like_count, view_count, review_count, ...
  - 좋아요 등록/취소 시 `product_stats.like_count`를 아토믹 업데이트.

- **장점**
  - 좋아요 폭주 시에도 **product(가격/재고/이름)** row에 락이 걸리지 않아 운영(가격 변경, 재고 변경) 영향 최소화.
  - 읽기 측면에서는 A안과 동일하게 `JOIN product_stats` + `ORDER BY like_count`로 랭킹 조회 가능.

- **단점 / 트레이드오프**
  - 테이블/코드가 하나 더 생겨 복잡도 증가.
  - PLP/PDP에서 `product_stats` JOIN이 항상 필요.
  - QPS가 실제로 높지 않다면 수직 분리의 이득이 제한적 → LEN 멘토가 말한 것처럼 **“정말 핫 상품이 존재하는지”**를 확인해야 함.

### 2.4 대안 C — 메모리 버퍼링 + 배치(DB Delta Update)

- **개념**
  - 실시간으로 product_stats 혹은 product를 갱신하지 않고, **ConcurrentHashMap/Redis 카운터**에 delta를 누적한 뒤, **주기적 배치**로 DB에 반영.

- **장점**
  - DB에 가는 UPDATE 횟수를 크게 줄여, Row lock 경합 완화.
  - 스파이크 트래픽에서 DB 보호 효과 큼.

- **단점 / 트레이드오프**
  - 실시간성이 떨어짐(배치 간격만큼 지연).
  - 프로세스/Redis 장애 시 누적된 delta 유실 가능성 → 보강 장치 필요.
  - 구현 난이도 및 운영 복잡도 상승.

### 2.5 프로젝트에서의 선택

- **단기(이번 과제 범위)**
  - **우선안**: **대안 A + B 부분 도입**
    - `product_stats` 테이블을 도입하고, 좋아요 수는 여기서 **아토믹 업데이트**.
    - PLP/PDP는 `JOIN product_stats` + `like_count` 사용.
  - **이유 (근거)**
    - 이미 벤치마크에서 좋아요 쓰기 경합이 뚜렷하고, 나중에 likes_desc 정렬/캐시 등을 올릴 예정이라, 쓰기-읽기 분리가 선행되어야 함.
    - ALEN/LEN이 지적한 **Row-level Lock 리스크**를 product에서 분리해 운영 리스크를 줄임.
- **중·장기**
  - 좋아요 QPS와 트래픽 패턴을 보고, 필요 시 **대안 C(버퍼링+배치)** 로 확장.

### 2.6 ② 좋아요 수 정렬 구조 — 비정규화 vs Materialized View 선택 근거

- **선택**: 이번 과제에서는 **비정규화(= `product_stats.like_count` 유지)** 를 1차 선택으로, **Materialized View/집계 테이블(3.2.3 대안 B)** 은 “랭킹 전용 Read Model이 별도로 필요해질 때”의 2차 옵션으로 둔다.
- **근거**
  - 실시간성: 좋아요 수는 **PDP·PLP에서 즉시 반영**되는 것이 UX 상 중요하다. MV를 배치로 갱신하면 갱신 주기(수십 초~수 분) 동안 랭킹·정렬이 어긋난다.
  - 복잡도: 현재 구조에서 `product_stats.like_count`는 이미 도입 예정이며, **동일 값을 그대로 정렬에 재사용**하면 된다. 반면 MV는 별도 테이블/잡/오케스트레이션이 필요하다.
  - 병목 위치: 10만/100만 건 벤치마크에서 좋아요 API는 이미 Row lock 경합이 명확하고(`benchmark-100k.md`/`benchmark-1m.md` 4.5절, p95 20~80ms + max 수백 ms), PLP likes_desc는 JOIN+GROUP BY 구조 때문에 **정렬 시 CPU·메모리 사용량이 높다**. 쓰기 병목은 2장에서 수직 분리+아토믹 업데이트로 완화하고, 읽기 병목은 `product_stats.like_count` 인덱스로 개선하는 것이 **한 번의 설계로 두 문제를 동시에 줄이는 방향**이다.
- **동기화 전략(등록/취소 시 count 일관성)**
  - 트랜잭션 내부에서만 다음 순서를 허용한다.
    - addLike: `likes` INSERT 성공 시에만 `product_stats.like_count = like_count + 1` (`UPDATE ... WHERE product_id = ?`).
    - removeLike: `likes` DELETE 성공 시에만 `product_stats.like_count = GREATEST(like_count - 1, 0)`.
  - 예외 발생 시 트랜잭션 전체 롤백으로 **likes row와 like_count 간의 강한 정합성**을 보장한다.
  - 장기적으로는 1일 1회 등으로 `likes` 테이블을 기준으로 `product_stats.like_count` 를 재집계하는 배치를 추가해, 일시적인 데이터 어긋남을 보정하는 “세이프티 넷”을 둔다.

---

## 3. 상품 목록 PLP — 정렬 + OFFSET 딥 페이징 (2순위)

### 3.1 latest / price 정렬 — OFFSET + 인덱스 + 1페이지 캐시

#### 3.1.1 병목 요약

- k6-product-plp: **latest 50~100 page** 시나리오가 모든 스케일에서 별도 측정, 비고 **OFFSET 딥페이징**.
- PageRequest.of(page, size) → `LIMIT size OFFSET page*size` 생성.
- `ProductRepositoryImpl.findNotDeleted`/`findNotDeletedForList` 가 OFFSET 기반 `Pageable` 사용.

#### 3.1.2 대안 A — 복합 인덱스 + OFFSET 유지

- **구조**
  - 인덱스:
    - latest: `(deleted_at, brand_id, created_at DESC)`
    - price_asc/price_desc: `(deleted_at, brand_id, price)` (정렬 방향은 MySQL 8에서는 하나의 인덱스로 커버 가능).
- **장점**
  - 구현 변경이 가장 적음(기존 OFFSET 구조 유지).
  - 1~몇십 페이지까지는 인덱스를 통해 빠르게 스캔 가능 → 벤치마크 p95가 이미 10~20ms 수준인 이유와 부합.
- **단점 / 트레이드오프**
  - page가 아주 커지면(예: page 1000 이상) 여전히 앞 페이지 스캔 비용 존재.
  - Read QPS가 폭발적으로 늘어날 경우 캐시 없이 DB만으론 한계.

#### 3.1.3 대안 B — Keyset Pagination(Seek) + 커서

- **구조**
  - `WHERE (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT size` 형태로 “마지막 행 이후”를 조회.
- **장점**
  - page가 커져도 성능이 안정적(OFFSET 스캔 없음).
- **단점 / 트레이드오프**
  - **임의 페이지 점프 불가**(혹은 구현 난이도 증가).
  - 기존 API(page, size 기반)와 호환성을 조정해야 함.

#### 3.1.4 대안 C — 1페이지 캐시(조건 조합별) + OFFSET 유지

- **ALEN 멘토 권장**
  - “같은 검색을 다시 할 확률이 높지 않다 → **PLP 1페이지만 캐시**.”
- **구조**
  - Redis 기준 키: `product:list:v1:{brandId}:{sort}:page=0:size={size}` (버전 포함).
  - TTL: **30초~1분** 정도의 짧은 TTL + **Jitter** (0~30초).
  - Kev/ALEN 관점: **파레토 10:90**을 고려해, 실제로는 “인기 브랜드/이벤트/likes_desc 1페이지”처럼 **핫한 조합 위주**로 캐시 가성비를 보고, 필요 시 만료 임박 시점에 백그라운드 리빌딩(Refresh-Ahead)을 추가로 검토한다.
- **장점**
  - PLP 1페이지(트래픽 집중 구간)를 Redis/로컬 캐시로 흡수, DB 부하 급감.
  - 키 수가 폭발하지 않는다(1페이지만 캐시).
- **단점 / 트레이드오프**
  - 2페이지 이상은 캐시 미적용(또는 별도 정책 필요).
  - 수정/삭제/좋아요 시 1페이지 캐시 **Evict/Overwrite** 로 관리 필요.

#### 3.1.5 프로젝트 선택

- **현 단계 선택**
  - 인덱스 기반 **대안 A(OFFSET 유지)** + **대안 C(1페이지 캐시)**를 먼저 적용.
  - Keyset Pagination(대안 B)은 API 스펙을 바꾸는 수준이라, **향후 모바일/UX 요구사항까지 정리된 후** 도입 검토.

---

### 3.2 likes_desc — JOIN+GROUP BY 제거를 위한 Read Model 전략

#### 3.2.1 병목 요약

- 벤치마크:
  - 200k: likes_desc 0~2 page p95 15.95ms, p99 44.90ms, 비고 **JOIN+GROUP BY**.
  - 500k/1m: p95는 SLO 안이지만, index-benchmark-1m 에서 **like_count 인덱스 도입 시 ms 수준**으로 내려가는 것과 비교될 정도의 구조적 리스크로 분류.
- 코드:
  - `ProductRepositoryImpl.findNotDeletedOrderByLikesDesc`: `LEFT JOIN likes` + `GROUP BY` + `ORDER BY COUNT(like.id) DESC` + OFFSET.

#### 3.2.2 대안 A — `product_stats.like_count` 기반 정렬 (2장에서 선택한 구조 재사용)

- **구조**
  - `product_stats`에 미리 유지한 like_count 사용.
  - 쿼리: `JOIN product_stats ps ON p.id = ps.product_id WHERE ... ORDER BY ps.like_count DESC`.
- **장점**
  - JOIN+GROUP BY가 아니라 **단순 JOIN + ORDER BY**로 변경.
  - 인덱스 `(like_count DESC, product_id)` 혹은 `(deleted_at, brand_id, like_count DESC)` 로 정렬 최적화 가능.
- **단점 / 트레이드오프**
  - 좋아요 쓰기 경로가 반드시 2장 구조를 따라야 함(동기화 실패 시 랭킹 오염).

#### 3.2.3 대안 B — 조회 전용 집계 테이블 / Materialized View

- **구조**
  - `product_like_ranking`(product_id, like_count, rank_updated_at).
  - 배치(예: 1~5분 간격) 혹은 이벤트 기반으로 갱신.
- **장점**
  - 랭킹 API를 완전히 분리해 “랭킹 전용 Read Model”로 관리.
  - PLP likes_desc와 별개로, `/api/v1/products/ranking/likes` 같은 랭킹 API 추가도 용이.
- **단점 / 트레이드오프**
  - **실시간성 < 갱신 주기** 만큼 떨어짐.
  - 인프라 복잡도 상승(배치/이벤트/별도 도메인).

#### 3.2.4 대안 C — Redis Sorted Set 기반 랭킹 캐시

- **구조**
  - `ZINCRBY product:likes:ranking {delta} {productId}` 형태로 좋아요 수를 Redis sorted set으로 관리.
  - 랭킹 조회 시 Redis에서 top N 읽고, 필요 시 DB 보정.
- **장점**
  - 랭킹 조회 QPS에 매우 강함(인메모리).
- **단점 / 트레이드오프**
  - Redis를 사실상 “세미 원본”처럼 쓰게 되어, 장애/동기화 정책을 명확히 해야 함.
  - 이번 과제 스코프(단일 DB 중심)에 비해 과도한 복잡도.

#### 3.2.5 프로젝트 선택

- **우선안**
  - 2장에서 선택한 **`product_stats.like_count` 기반 대안 A**를 PLP likes_desc에도 그대로 사용.
  - 별도 랭킹 전용 API가 필요해지면, B/C를 “차기 단계 옵션”으로 문서에 남기고 실제 구현은 팀 합의 후 진행.

#### 3.2.6 ① 상품 목록 조회 성능 개선 — 브랜드 필터 + 좋아요순 정렬 EXPLAIN/벤치마크 비교

- **데이터 준비**
  - `benchmark-100k.md`/`benchmark-1m.md` 의 시더 설정을 그대로 사용해, **상품 10만/100만 건 + 브랜드 500개**(Zipf 분포) 데이터를 준비한다.
  - k6 스크립트는 PLP용으로, **브랜드 필터 + likes_desc 정렬 + page 0~2**를 집중적으로 때리는 시나리오를 사용한다.
- **AS-IS 쿼리 & EXPLAIN 특징**
  - 쿼리: `FROM product p LEFT JOIN likes l ON p.id = l.product_id WHERE p.deleted_at IS NULL AND p.brand_id = ? GROUP BY p.id ... ORDER BY COUNT(l.id) DESC LIMIT ? OFFSET ?`.
  - EXPLAIN(100만 건 기준, 실제 측정 예정):
    - `type = ALL`, `rows ≈ 1,000,000`, `Extra = Using temporary; Using filesort` 로, **브랜드 필터·정렬 모두 인덱스로 커버되지 않고 풀스캔 + 임시 테이블 + filesort** 경로를 타는 것이 기본 패턴이다.
  - API 레벨 측정은 `benchmark-1m.md` 4.1절 likes_desc 0~2 page 결과(p95 7.25ms, JOIN+GROUP BY 비고)를 baseline 으로 사용한다.
- **TO-BE 쿼리 & 인덱스**
  - 2장에서 도입한 `product_stats` 를 사용해 쿼리를 변경한다.
    - `FROM product p JOIN product_stats ps ON p.id = ps.product_id WHERE p.deleted_at IS NULL AND p.brand_id = ? ORDER BY ps.like_count DESC, p.id DESC LIMIT ? OFFSET ?`.
  - 인덱스 후보:
    - `product`: `(deleted_at, brand_id, id)` — 기본 필터 + 조인 키 최적화.
    - `product_stats`: `(like_count DESC, product_id)` 혹은 `(product_id, like_count)` — like_count 정렬 + 조인 키를 동시에 커버.
  - EXPLAIN 기대 형태:
    - `product` 에서 `range`/`ref` 로 브랜드 구간만 스캔(`rows`가 1,000,000 → 브랜드 당 수천~수만 건 수준으로 감소).
    - `product_stats` 에서 `ref` 로 product_id 조인, `Extra = Using index condition` 혹은 `Using index; Using where` 수준으로 정렬 시 filesort 의존도 감소.
- **API 레벨 Before/After 기록 계획**
  - Before: `benchmark-100k.md`/`benchmark-1m.md` 의 `PLP likes_desc 0~2 page` p95/p99 값을 **AS-IS** 로 고정(현재 각각 p95 8.46ms, 7.25ms 수준).
  - After: 위 인덱스 및 쿼리 교체 후 동일 k6 스크립트를 재실행해, **speedup 배수(p95_Before / p95_After)** 를 이 문서에 테이블로 추가한다.
  - EXPLAIN 결과(`rows`, `type`, `Extra`)도 Before/After 캡처해, 인덱스 최적화가 실제로 풀스캔/임시 테이블/filesort 를 제거했는지 근거로 남긴다.

---

## 4. PDP(상품 상세) — 캐시 없이 3쿼리 구조의 향후 병목 (3순위)

### 4.1 AS-IS & 벤치마크 해석

- AS-IS
  - 컨트롤러 → `productFacade.getProductDetail(productId)`.
  - Facade 내부: productService.findByIdAndNotDeleted + brandService.findByIdAndNotDeleted + likeService.getLikeCount(productId) **3쿼리**.
- 벤치마크
  - 100k~1m에서 p95는 8~18ms로 SLO 내, max가 100~200ms로 튀는 꼬리 존재.
  - k6-product-pdp: 현재는 랜덤 ID 범위로 부하를 주지만, 실제 서비스에서는 소수의 핫 상품에 조회가 몰리는 패턴이 일반적 → 현재 구조는 그 트래픽이 모두 DB 3쿼리로 가는 형태.

### 4.2 대안 A — Read Model + 캐시(로컬/Redis) 조합

- **구조**
  - 2장에서 도입한 `product_stats` + PDP DTO를 묶어 **“PDP용 Read Model”** 생성.
  - PDP 캐시:
    - Redis 키: `product:detail:v1:{id}` (버전 포함).
    - 저장 값: PDP DTO (product + brand + like_count).
  - 캐시 계층:
    - 단일 인스턴스 환경: **로컬 캐시 + TTL** 우선 (ALEN/Kev), 필요 시 Redis 병행.
    - 다중 인스턴스/수평 확장: Redis 주력이며, 필요 시 Redis 변경 시 Pub-Sub으로 로컬 캐시를 무효화하는 구조까지 선택지로 둔다.
- **TTL·Jitter**
  - TTL: 30초~수 분 (상품 수정 빈도와 최신성 요구에 따라). Kev 관점에 맞춰 **가능하면 짧게(예: 3~5분 이하)** 가져가고, 정말 변경이 적은 경우에만 늘린다.
  - Jitter: 0~30초 정도 랜덤 추가(Devin).
  - 핫 상품이면 조회 시 TTL을 조금씩 연장(Sliding Window).
- **무효화**
  - 상품 수정/삭제: 즉시 `product:detail:*` 삭제.
  - 좋아요 변경:
    - 실시간에 가깝게 반영하고 싶으면 add/removeLike 시 해당 PDP 캐시 삭제.
    - 결과적 정합성을 수 분 허용 가능하면 TTL 만료에만 맡김.

- **장점**
  - 핫 PDP 트래픽 대부분이 캐시에서 처리되어 DB 3쿼리가 크게 줄어듦.
  - Read Model + 캐시 구조가 향후 좋아요 랭킹/추천에도 재사용 가능.

- **단점 / 트레이드오프**
  - 캐시 키 관리와 무효화 책임이 생김.
  - TTL/정합성 정책을 팀 차원에서 합의해야 함.

### 4.3 대안 B — DB 최적화만으로 유지 (캐시 미적용)

- **개념**
  - 현재 벤치마크에서 p95가 여유로운 만큼, PDP에 굳이 캐시를 넣지 않고, PLP/좋아요 우선으로 개선 후 다시 본다.
- **장점**
  - 구현 단순. 캐시/정합성 고민 없음.
- **단점 / 트레이드오프**
  - 실제 운영 시 핫 PDP가 생기면, 부하가 그대로 DB로 몰린다.

### 4.4 프로젝트 선택

- **현 단계**
  - PLP/좋아요 우선 개선 후에도 여유가 있으면, **대안 A(캐시 + Read Model)** 을 PDP에 도입하는 것을 목표로 한다.
  - TTL은 상품 도메인의 변경 빈도와 정합성 요구를 반영해 결정(“변경 빈도 기준”은 LEN 멘토 기준 적용).

---

## 5. 주문 목록/상세 — user_id + 기간 + status + OFFSET (3순위)

### 5.1 AS-IS & 리스크

- AS-IS
  - 인프라 레이어:  
    `findByUserIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtDesc(userId, start, end, PageRequest.of(page, size))`
  - 쿼리: `WHERE user_id = ? AND ordered_at BETWEEN ? AND ? ORDER BY ordered_at DESC LIMIT size OFFSET page*size`.
- 벤치마크
  - 현재는 시드 문제로 대부분 4xx/5xx → 에러율 100%(비200), latency 수치는 신뢰 어려움.
  - 문서 상 “실 데이터 기준 수백만 row 중 범위 스캔 + 정렬 반복” 구조라고 명시.

### 5.2 대안 A — 인덱스 최적화 + 캐시 미적용 (ALEN/Kev 권장)

- **구조**
  - 인덱스: `(user_id, status, ordered_at DESC)` 또는 `(user_id, ordered_at DESC)` (status 필터 중요도에 따라 선택).  
    Kev/LEN 관점대로 **Equal → Range 순서**를 유지하고, 실제 쿼리가 status를 Equal/IN으로 쓰는지, status·기간별 데이터 분포가 어떤지까지 보고 결정한다.
- **장점**
  - 정합성이 매우 중요한 주문 도메인에 캐시를 두지 않고, RDB 인덱스만으로 SLO 200ms를 맞추는 단순한 구조.
  - 캐시 정합성/무효화 복잡도를 제거.
- **단점 / 트레이드오프**
  - 고객이 마이페이지를 반복 조회하는 경우도 모두 DB로 감당해야 함.

### 5.3 대안 B — 유저별 목록 캐시 (짧은 TTL + 강한 Evict, “최후 수단” 옵션)

- **구조**
  - 키: `order:list:v1:{userId}:{start}:{end}:{page}:{size}`.
  - TTL: 1~3분(짧게) + Jitter.
  - 무효화:
    - 주문 생성/취소 시 해당 userId의 `order:list:v1:{userId}:*` 전체 삭제.
- **장점**
  - 마이페이지 반복 조회가 많은 경우, DB 부하를 크게 줄일 수 있음.
- **단점 / 트레이드오프**
  - 키 조합이 많아질 수 있고, 무효화 누락 시 정합성 문제.
  - ALEN 멘토 관점에서는 “주문은 캐시 미적용”이 기본이고, Kev 멘토 관점에서도 **유저 기반 데이터는 캐시 히트율이 낮아 보통 인덱스로 충분**하므로, **“인덱스만으로 SLO를 만족하지 못할 때에만” 사용하는 최후 수단 옵션**으로 한정한다.

### 5.4 주문 상세

- 상세는 PK 기반 단건 조회 + `order_item.order_id` 인덱스로 충분히 빠른 쿼리를 만들 수 있어,  
  **인덱스만으로 SLO를 지킬 수 있다면 캐시를 생략**하고, 필요 시만 PDP와 같은 Read-Through 캐시를 검토한다.

### 5.5 프로젝트 선택

- **현 단계**
  - ALEN/LEN 관점과 벤치마크 현실(에러율 100%)을 고려해, **우선 인덱스 최적화(대안 A)** 만 적용하고 캐시는 생략하는 쪽으로 둔다.
  - PLP/좋아요/PDP를 먼저 개선한 후에도 주문 목록이 SLO를 위협하면, 그때 **대안 B(유저별 캐시)** 를 검토한다.

---

## 6. 실행 순서 & 요약 체크리스트

### 6.1 실행 순서(Phase별)

- **Phase 1 — 좋아요 쓰기 + PLP 기본 인덱스**
  - `product_stats`(또는 동등한 수직 분리 테이블) 도입, 좋아요 쓰기 시 like_count 아토믹 업데이트.
  - PLP latest/price 정렬용 복합 인덱스 추가.
  - PLP/likes_desc 쿼리에서 `product_stats.like_count` 사용하도록 쿼리 교체.
- **Phase 2 — PLP 캐시 + likes_desc 최적화**
  - PLP 1페이지 캐시(조건 조합별 키 + TTL 30~60초 + Jitter) 적용.
  - likes_desc 쿼리가 `JOIN+GROUP BY` 에서 `JOIN product_stats + ORDER BY like_count` 로 내려갔는지 EXPLAIN/벤치마크 확인.
- **Phase 3 — PDP 캐시 및 정합성 정책**
  - PDP Read Model + 캐시(로컬/Redis) 도입.
  - 상품 수정/삭제/좋아요 시 무효화 정책 확정.
- **Phase 4 — 주문 목록 인덱스 & 필요 시 캐시**
  - 주문 목록 인덱스 `(user_id, status, ordered_at)` 설계 및 적용(실제 쿼리가 status를 Equal/IN으로 쓰는지, 데이터 분포가 어떤지까지 포함해 Kev/LEN 관점으로 검토).
  - 인덱스만으로 SLO 200ms 내인지 **주문 목록 단독 시나리오**로 측정(p95/p99), 충분하지 않을 때에만 유저별 짧은 TTL 캐시를 검토.

### 6.2 최종 체크리스트 (요약)

- **좋아요 쓰기**
  - [ ] Row lock 경합을 줄이기 위해 `product_stats.like_count` 수직 분리 및 아토믹 업데이트 도입.
  - [ ] 동시성 테스트(k6-likes-write 상위 상품 집중 시나리오 재실행).
- **PLP**
  - [ ] latest/price 정렬용 복합 인덱스 적용, 딥 페이징 시 EXPLAIN rows/Using filesort 확인.
  - [ ] likes_desc가 `JOIN+GROUP BY` 에서 `JOIN product_stats + ORDER BY like_count` 로 변경되었는지 확인.
  - [ ] PLP 1페이지 캐시(조건 조합별 키 + TTL + Jitter), **인기 브랜드/likes_desc 1페이지 같은 핫 조합 우선 캐싱** 및 수정/삭제/좋아요 시 무효화 정책 구현.
- **PDP**
  - [ ] PDP Read Model + 캐시 도입 여부 및 TTL/정합성 정책 결정.
  - [ ] 핫 상품 집중 시나리오로 k6-product-pdp 재실행하여 max 꼬리 감소 확인.
- **주문**
  - [ ] 주문 목록 인덱스 `(user_id, status, ordered_at)` 적용 및 EXPLAIN 확인, status·기간별 데이터 분포까지 함께 점검.
  - [ ] 캐시를 적용할지 여부를 SLO와 정합성 요구, **유저 기반 데이터의 캐시 히트율/가성비**를 기준으로 재판단.

이 로드맵을 기준으로, 실제 구현 시에는 각 Phase마다 **벤치마크 스크립트(k6-product-plp/PDP/orders-list/likes-write)** 를 **시나리오별로 분리**해 동일 조건으로 다시 돌려 Before/After 지표(p95/p99, 개선 배수)를 문서에 축적하고, 필요하면 멘토 문서의 옵션들(1페이지 캐시, TTL+Jitter, MV/Read Model, 수직 분리/배치 갱신, 로컬+Redis+Pub-Sub, 만료 임박 리빌딩 등)을 추가 적용한다.
