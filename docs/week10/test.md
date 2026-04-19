# Week 10 테스트 명세

> Spring Batch 기반 주간/월간 랭킹 집계 시스템 구현에 대한 테스트 전략 및 케이스 정리

---

## 테스트 피라미드 구성

```
                /\
               /E2E\          API E2E (TestRestTemplate + Testcontainers)
              /______\         Batch E2E (SpringBatchTest + Testcontainers)
             /        \
            /  Unit     \     Facade / Controller (Mock)
           /______________\
```

---

## 1. Application Layer — Facade 단위 테스트

> Mock을 사용하여 Repository·ProductFacade 의존성을 격리하고, Facade 의 조합 로직만 검증한다.

### 1-1. `RankingFacadeTest` (일간, 수정)

**대상**: `RankingFacade` — Redis ZSET 기반 일간 랭킹 조회  
**변경 이유**: `RankingPageResult` record 도입으로 반환 타입 변경 반영

| 테스트 메서드 | 검증 내용 |
|---|---|
| `happyPath` | ZSET Top-N 결과와 상품 정보를 Aggregation하여 `RankingPageResult`로 반환 |
| `visibilityFilter` | 숨김/삭제 상품은 `items`에서 제외, 원래 rank는 유지 |
| `nullDateDefaultsToToday` | date=null이면 KST 오늘 날짜로 Redis 키 생성 |
| `emptyRanking` | ZSET이 비면 빈 리스트 반환 |
| `getDailyRank_returnsRank` | ZREVRANK 결과를 그대로 반환 |
| `getDailyRank_nullWhenAbsent` | 순위권 밖이면 null 반환 |
| `getDailyRank_nullProductId` | productId=null이면 null 반환 |
| `KST 자정 경계 — 23:59:59` | 그날 날짜 키로 조회 |
| `KST 자정 경계 — 00:00:00` | 다음 날짜 키로 조회 |
| `getDailyRanking 23:59:59 경계` | 그날 키로 getTopN 호출 |
| `getDailyRanking 00:00:00 경계` | 새 날짜 키로 getTopN 호출 |

---

### 1-2. `WeeklyRankingFacadeTest` (신규)

**대상**: `WeeklyRankingFacade` — MV 테이블 기반 주간 랭킹 조회  
**테스트 더블**: `WeeklyRankingRepository` Mock, `ProductFacade` Mock, `Clock` 고정

| 테스트 메서드 | 검증 내용 |
|---|---|
| `happyPath` | MV 엔트리와 상품 정보를 Aggregation하여 rank 순서대로 반환 |
| `visibilityFilter` | 숨김/삭제 상품은 응답 제외, 남은 상품의 원래 rank 유지 |
| `emptyEntries` | MV 엔트리가 없으면 빈 리스트 반환 |
| `nullDateUsesYesterdayForQuery` | date=null이면 KST 어제 날짜로 Repository 조회 (배치 base_date 규칙과 일치) |

---

### 1-3. `MonthlyRankingFacadeTest` (신규)

**대상**: `MonthlyRankingFacade` — MV 테이블 기반 월간 랭킹 조회  
**테스트 더블**: `MonthlyRankingRepository` Mock, `ProductFacade` Mock, `Clock` 고정

| 테스트 메서드 | 검증 내용 |
|---|---|
| `happyPath` | MV 엔트리와 상품 정보를 Aggregation하여 rank 순서대로 반환 |
| `visibilityFilter` | 숨김/삭제 상품은 응답 제외, 남은 상품의 원래 rank 유지 |
| `emptyEntries` | MV 엔트리가 없으면 빈 리스트 반환 |
| `nullDateUsesYesterdayForQuery` | date=null이면 KST 어제 날짜로 Repository 조회 |

---

## 2. Interfaces Layer — Controller 단위 테스트

> `@WebMvcTest` + MockMvc로 HTTP 레이어만 격리하여 검증한다.  
> Facade는 Mock 처리하여 Controller 의 파라미터 변환·보정 로직에만 집중한다.

### 2-1. `WeeklyRankingV1ControllerTest` (신규)

**대상**: `WeeklyRankingV1Controller` — `GET /api/v1/rankings/weekly`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `happyPath` | Facade 결과가 JSON 필드(`date`, `page`, `size`, `totalElements`, `items[].rank`)로 올바르게 직렬화됨 |
| `nullDatePassedToFacade` | date 파라미터 생략 시 Facade에 null이 전달되어 날짜 기본값 처리가 Facade에 위임됨 |
| `pageZeroClampedToOne` | page=0이면 1로 보정되어 Facade 호출, 응답 `page` 필드도 1 |
| `sizeClampedToMax` | size=200이면 100으로 보정되어 Facade 호출, 응답 `size` 필드도 100 |
| `sizeZeroClampedToDefault` | size=0이면 20으로 보정되어 Facade 호출, 응답 `size` 필드도 20 |
| `badDateFormat` | `2026-04-11`처럼 yyyy-MM-dd 형식이면 400 BAD_REQUEST |

---

### 2-2. `MonthlyRankingV1ControllerTest` (신규)

**대상**: `MonthlyRankingV1Controller` — `GET /api/v1/rankings/monthly`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `happyPath` | Facade 결과가 JSON 필드로 올바르게 직렬화됨 |
| `nullDatePassedToFacade` | date 파라미터 생략 시 Facade에 null 전달 |
| `pageZeroClampedToOne` | page=0 → 1로 보정 |
| `sizeClampedToMax` | size=200 → 100으로 보정 |
| `sizeZeroClampedToDefault` | size=0 → 20으로 보정 |
| `badDateFormat` | 잘못된 date 포맷 → 400 BAD_REQUEST |

---

## 3. Batch E2E 테스트

> `@SpringBatchTest` + Testcontainers(MySQL)로 실제 DB에 Job을 실행하고 MV 테이블 결과를 검증한다.

### 3-1. `WeeklyRankingJobE2ETest` (신규 + 추가)

**대상**: `weeklyRankingJob` — 주간 랭킹 배치  
**슬라이딩 윈도우**: `[targetDate - 7일 00:00, targetDate 00:00)`  
**base_date**: `targetDate - 1일`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `failsWithoutTargetDate` | targetDate JobParameter 없이 실행하면 ExitStatus=FAILED |
| `completedWithEmptyMetrics` | 집계 대상 데이터 없으면 COMPLETED, MV 테이블에 데이터 없음 |
| `populatesMvTableWithRanking` | score 내림차순으로 rank가 부여되어 MV 테이블에 적재됨 (score 공식: `LN(1+view)*0.1 + LN(1+like)*0.2 + LN(1+orderAmount)*0.7`) |
| `replacesExistingMvOnRerun` | 동일 targetDate로 재실행 시 기존 base_date 데이터가 새 랭킹으로 교체됨 |
| `excludesDataOutsideWindow` | 윈도우 시작(`targetDate-7일`)보다 이전 데이터는 집계에서 제외됨 |
| `aggregatesMultipleBucketHoursForSameProduct` | 동일 product_id의 여러 bucket_hour 데이터가 SUM으로 합산되어 score 계산됨 |
| `limitsToTop100` | 집계 대상이 101개 이상이어도 MV 테이블에는 상위 100건만 적재됨 |

---

### 3-2. `MonthlyRankingJobE2ETest` (신규 + 추가)

**대상**: `monthlyRankingJob` — 월간 랭킹 배치  
**슬라이딩 윈도우**: `[targetDate - 30일 00:00, targetDate 00:00)`  
**base_date**: `targetDate - 1일`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `failsWithoutTargetDate` | targetDate JobParameter 없이 실행하면 ExitStatus=FAILED |
| `completedWithEmptyMetrics` | 집계 대상 데이터 없으면 COMPLETED, MV 테이블에 데이터 없음 |
| `populatesMvTableWithRanking` | score 내림차순으로 rank가 부여되어 MV 테이블에 적재됨 |
| `replacesExistingMvOnRerun` | 동일 targetDate로 재실행 시 기존 base_date 데이터가 새 랭킹으로 교체됨 |
| `excludesDataOutsideWindow` | 윈도우 시작(`targetDate-30일`)보다 이전 데이터는 집계에서 제외됨 |
| `aggregatesMultipleBucketHoursForSameProduct` | 동일 product_id의 여러 bucket_hour 데이터가 SUM으로 합산됨 |
| `limitsToTop100` | 집계 대상이 101개 이상이어도 MV 테이블에는 상위 100건만 적재됨 |

---

## 4. API E2E 테스트

> `@SpringBootTest(RANDOM_PORT)` + Testcontainers + TestRestTemplate으로 HTTP 전체 흐름을 검증한다.  
> MV 테이블에 직접 데이터를 seeding하여 Batch 의존성 없이 API 동작을 독립 검증한다.

### 4-1. `WeeklyRankingV1ApiE2ETest` (신규 + 추가)

**대상**: `GET /api/v1/rankings/weekly`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `happyPath` | MV 데이터와 상품 정보가 Aggregation된 페이지 응답 반환 (`rank`, `productId`, `name`, `totalElements`, `date` 검증) |
| `visibilityFilter` | displayYn=N 상품은 응답 items에서 제외됨 |
| `defaultToYesterday` | date 파라미터 생략 시 KST 어제 날짜 기준으로 조회, 응답 `date` 필드도 어제 날짜 |
| `badDateFormat` | `2026-04-09` 형식(yyyy-MM-dd)은 400 BAD_REQUEST |
| `emptyRanking` | 해당 날짜 MV 데이터 없으면 빈 items, totalElements=0 |
| `pagination` | page=2&size=2 요청 시 2페이지(3번째 상품)만 반환, totalElements는 전체 건수 유지 |
| `sizeClampedToMax` | size=200 요청 시 응답 `size` 필드가 100으로 보정됨 |

---

### 4-2. `MonthlyRankingV1ApiE2ETest` (신규 + 추가)

**대상**: `GET /api/v1/rankings/monthly`

| 테스트 메서드 | 검증 내용 |
|---|---|
| `happyPath` | MV 데이터와 상품 정보가 Aggregation된 페이지 응답 반환 |
| `visibilityFilter` | displayYn=N 상품은 응답에서 제외됨 |
| `defaultToYesterday` | date 생략 시 KST 어제 날짜 기준 조회 |
| `badDateFormat` | 잘못된 date 포맷 → 400 BAD_REQUEST |
| `emptyRanking` | MV 데이터 없으면 빈 items, totalElements=0 |
| `pagination` | page=2&size=2 요청 시 2페이지 데이터만 반환, totalElements는 전체 건수 유지 |
| `sizeClampedToMax` | size=200 요청 시 응답 `size` 필드가 100으로 보정됨 |

---

## 5. 테스트 실행 결과

> 실행 환경: macOS / JDK 21 / Testcontainers(MySQL 8.0) / 2026-04-16

### 5-1. Application Layer — Facade 단위 테스트

#### `RankingFacadeTest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.019s | KST 23:59:59 에는 그 날짜 키로 조회한다 |
| PASS | 0.001s | KST 00:00:00 에는 다음 날짜 키로 조회한다 |
| PASS | 0.001s | 날짜 생략 케이스 — KST 23:59:59 에는 그 날 키로 getDailyRanking 을 호출한다 |
| PASS | 0.001s | 날짜 생략 케이스 — KST 00:00:00 에는 새 날짜 키로 getDailyRanking 을 호출한다 |
| PASS | 0.001s | ZSET Top-N 과 상품 정보를 Aggregation 하여 반환 |
| PASS | 0.001s | 삭제/숨김 상품은 응답에서 제외되고 size 는 축소된다 |
| PASS | 0.001s | date 가 null 이면 KST 오늘 날짜로 조회 |
| PASS | 0.001s | ZSET 이 비어 있으면 빈 리스트 |
| PASS | 0.001s | ZREVRANK 결과를 그대로 반환 |
| PASS | 0.001s | 순위권 밖이면 null |
| PASS | 0.000s | productId 가 null 이면 null 반환 |
| **합계** | **0.029s** | **11건 / 0실패** |

#### `WeeklyRankingFacadeTest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.016s | MV 테이블 엔트리와 상품 정보를 Aggregation 하여 반환한다. |
| PASS | 0.001s | 삭제/숨김 상품은 응답에서 제외되고 size 는 축소된다. |
| PASS | 0.001s | MV 엔트리가 없으면 빈 리스트를 반환한다. |
| PASS | 0.001s | date 가 null 이면 KST 어제 날짜 기준으로 조회한다. |
| **합계** | **0.019s** | **4건 / 0실패** |

#### `MonthlyRankingFacadeTest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.642s | MV 테이블 엔트리와 상품 정보를 Aggregation 하여 반환한다. |
| PASS | 0.003s | 삭제/숨김 상품은 응답에서 제외되고 size 는 축소된다. |
| PASS | 0.002s | MV 엔트리가 없으면 빈 리스트를 반환한다. |
| PASS | 0.005s | date 가 null 이면 KST 어제 날짜 기준으로 조회한다. |
| **합계** | **0.653s** | **4건 / 0실패** |

---

### 5-2. Interfaces Layer — Controller 단위 테스트

#### `WeeklyRankingV1ControllerTest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.011s | 200 — Facade 결과가 JSON으로 올바르게 직렬화된다. |
| PASS | 0.002s | date 파라미터 생략 시 Facade 에 null 이 전달되어 날짜 기본값 처리가 위임된다. |
| PASS | 0.002s | size=0 은 기본값 20으로 보정되어 Facade 에 전달된다. |
| PASS | 0.004s | 잘못된 date 포맷은 400 BAD_REQUEST 를 반환한다. |
| PASS | 0.002s | size=200 은 100으로 보정되어 Facade 에 전달된다. |
| PASS | 0.002s | page=0 은 1로 보정되어 Facade 에 전달된다. |
| **합계** | **0.027s** | **6건 / 0실패** |

#### `MonthlyRankingV1ControllerTest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.132s | 200 — Facade 결과가 JSON으로 올바르게 직렬화된다. |
| PASS | 0.003s | date 파라미터 생략 시 Facade 에 null 이 전달되어 날짜 기본값 처리가 위임된다. |
| PASS | 0.003s | size=0 은 기본값 20으로 보정되어 Facade 에 전달된다. |
| PASS | 0.009s | 잘못된 date 포맷은 400 BAD_REQUEST 를 반환한다. |
| PASS | 0.003s | size=200 은 100으로 보정되어 Facade 에 전달된다. |
| PASS | 0.002s | page=0 은 1로 보정되어 Facade 에 전달된다. |
| **합계** | **0.157s** | **6건 / 0실패** |

---

### 5-3. Batch E2E 테스트

#### `WeeklyRankingJobE2ETest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.099s | 집계 대상 데이터가 없을 때 배치가 COMPLETED 되고 MV 테이블에 데이터가 없다. |
| PASS | 0.072s | 슬라이딩 윈도우 바깥 데이터는 집계에서 제외된다. |
| PASS | 0.060s | targetDate 파라미터 없이 실행하면 배치가 실패한다. |
| PASS | 0.068s | 집계 대상 데이터가 있으면 MV 테이블에 score 내림차순으로 랭킹이 적재된다. |
| PASS | 0.075s | 동일 상품의 여러 bucket_hour 데이터가 합산되어 점수가 계산된다. |
| PASS | 0.123s | 재실행 시 기존 MV 데이터가 새 랭킹으로 교체된다. |
| PASS | 0.253s | 집계 대상 상품이 100개를 초과하더라도 MV 테이블에는 상위 100건만 적재된다. |
| **합계** | **0.753s** | **7건 / 0실패** |

#### `MonthlyRankingJobE2ETest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.711s | 집계 대상 데이터가 없을 때 배치가 COMPLETED 되고 MV 테이블에 데이터가 없다. |
| PASS | 0.124s | 슬라이딩 윈도우 바깥 데이터는 집계에서 제외된다. |
| PASS | 0.078s | targetDate 파라미터 없이 실행하면 배치가 실패한다. |
| PASS | 0.086s | 집계 대상 데이터가 있으면 MV 테이블에 score 내림차순으로 랭킹이 적재됨 |
| PASS | 0.084s | 동일 상품의 여러 bucket_hour 데이터가 합산되어 점수가 계산된다. |
| PASS | 0.135s | 재실행 시 기존 MV 데이터가 새 랭킹으로 교체된다. |
| PASS | 0.277s | 집계 대상 상품이 100개를 초과하더라도 MV 테이블에는 상위 100건만 적재된다. |
| **합계** | **1.499s** | **7건 / 0실패** |

---

### 5-4. API E2E 테스트

#### `WeeklyRankingV1ApiE2ETest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.380s | 200 — 상품 정보가 Aggregation 된 주간 랭킹 Page 를 반환한다. |
| PASS | 0.351s | 숨김/삭제 상품은 응답에서 제외된다. |
| PASS | 0.352s | date 파라미터 생략 시 어제 날짜 기준 주간 랭킹을 조회한다. |
| PASS | 0.238s | 잘못된 date 포맷은 400을 반환한다. |
| PASS | 0.391s | 랭킹 데이터가 없으면 빈 items 와 totalElements=0 을 반환한다. |
| PASS | 0.340s | page=2, size=2 요청 시 2페이지 데이터를 반환한다. |
| PASS | 0.380s | size=200 요청 시 100으로 보정된 size 가 응답에 반영된다. |
| **합계** | **2.439s** | **7건 / 0실패** |

#### `MonthlyRankingV1ApiE2ETest`

| 결과 | 소요시간 | 테스트명 |
|---|---|---|
| PASS | 0.832s | 200 — 상품 정보가 Aggregation 된 월간 랭킹 Page 를 반환한다. |
| PASS | 0.355s | 숨김/삭제 상품은 응답에서 제외된다. |
| PASS | 0.295s | date 파라미터 생략 시 어제 날짜 기준 월간 랭킹을 조회한다. |
| PASS | 0.358s | 잘못된 date 포맷은 400을 반환한다. |
| PASS | 0.275s | 랭킹 데이터가 없으면 빈 items 와 totalElements=0 을 반환한다. |
| PASS | 0.369s | page=2, size=2 요청 시 2페이지 데이터를 반환한다. |
| PASS | 0.239s | size=200 요청 시 100으로 보정된 size 가 응답에 반영된다. |
| **합계** | **2.729s** | **7건 / 0실패** |

---

### 5-5. 전체 결과 요약

| 레이어 | 테스트 클래스 | 총 건수 | 성공 | 실패 | 총 소요시간 |
|---|---|---|---|---|---|
| Facade (Unit) | RankingFacadeTest | 11 | 11 | 0 | 0.029s |
| Facade (Unit) | WeeklyRankingFacadeTest | 4 | 4 | 0 | 0.019s |
| Facade (Unit) | MonthlyRankingFacadeTest | 4 | 4 | 0 | 0.653s |
| Controller (Unit) | WeeklyRankingV1ControllerTest | 6 | 6 | 0 | 0.027s |
| Controller (Unit) | MonthlyRankingV1ControllerTest | 6 | 6 | 0 | 0.157s |
| Batch E2E | WeeklyRankingJobE2ETest | 7 | 7 | 0 | 0.753s |
| Batch E2E | MonthlyRankingJobE2ETest | 7 | 7 | 0 | 1.499s |
| API E2E | WeeklyRankingV1ApiE2ETest | 7 | 7 | 0 | 2.439s |
| API E2E | MonthlyRankingV1ApiE2ETest | 7 | 7 | 0 | 2.729s |
| **합계** | | **59** | **59** | **0** | **~8.3s** |

> 모든 59건 PASS, 실패 0건

---

## 6. 테스트 결과 인사이트

### 인사이트 1 — 레이어별 실행 속도 차이가 뚜렷하다

| 레이어 | 평균 케이스 소요 | 이유 |
|---|---|---|
| Facade Unit | ~1ms | Mock만 사용, I/O 없음 |
| Controller Unit | ~5–22ms | Spring MVC 컨텍스트 로딩 포함 |
| Batch E2E | 60–277ms | Testcontainers DB + JDBC 실행 |
| API E2E | 238–832ms | Testcontainers DB + HTTP 왕복 |

단위 테스트는 1ms 미만으로 끝나는 반면 E2E 테스트는 100~800ms 수준이다.
피라미드 하단에 단위 테스트를 집중시켜 빠른 피드백 루프를 유지하는 설계가 측정으로 입증된다.

### 인사이트 2 — `limitsToTop100` 이 같은 레이어에서 가장 느리다

- 주간: `limitsToTop100` 253ms vs 평균 ~80ms
- 월간: `limitsToTop100` 277ms vs 평균 ~100ms

101개 product + metric 행 insert → Job 실행 → MV 카운트 검증 순서로 데이터가 가장 많아 느리다.
실제 운영에서 배치 SQL이 대량 데이터를 처리할 때 성능 병목이 발생할 수 있음을 시사하며,
인덱스(`bucket_hour`, `product_id`) 및 LIMIT 절의 중요성을 재확인한다.

### 인사이트 3 — Batch E2E 첫 번째 케이스가 느린 이유는 Testcontainers 초기화

- 월간 `completedWithEmptyMetrics`: 711ms (나머지 케이스 평균 ~100ms)

Testcontainers MySQL 컨테이너가 첫 번째 테스트에서 초기화되기 때문이다.
이후 케이스는 동일 컨텍스트를 재사용하여 80~277ms 로 안정된다.
CI 파이프라인에서 타임아웃을 설정할 때 컨테이너 워밍업 시간을 반드시 고려해야 한다.

### 인사이트 4 — `failsWithoutTargetDate` 가 NullPointerException 경로로 실패하는 것은 의도된 동작이다

배치 파라미터 검증이 Spring Batch Job 레벨이 아닌 `ItemReader` 에서 발생한다.
`targetDate` 가 null 이면 `weeklyRankingReader`/`monthlyRankingReader` 가 NPE를 던지고,
Spring Batch가 이를 잡아 `ExitStatus=FAILED` 로 전환한다.
이 테스트는 **파라미터 없이 배치를 실행했을 때 조용히 COMPLETED 되는 버그**를 방지하는 안전망 역할을 한다.
향후 개선 방향으로 `JobParametersValidator` 를 별도 구현하면 더 명시적인 실패 메시지를 남길 수 있다.

### 인사이트 5 — Controller 의 파라미터 보정 로직은 단위 테스트로 완결된다

`page=0 → 1`, `size=0 → 20`, `size=200 → 100` 보정은 Controller 레이어에서만 발생한다.
MockMvc 단위 테스트에서 Facade Mock의 실제 호출 인자를 `verify()` 로 검증하기 때문에,
E2E 테스트는 보정 결과만 응답 JSON 필드로 확인하면 충분하다.
관심사 분리 원칙에 따라 Controller 테스트가 보정 로직을, E2E 테스트가 전체 흐름을 각각 담당한다.

### 인사이트 6 — API E2E에서 Batch 의존성을 분리한 설계가 유지보수성을 높인다

`WeeklyRankingV1ApiE2ETest` / `MonthlyRankingV1ApiE2ETest` 는 MV 테이블에 직접 `jdbcTemplate.update()` 로 seeding한다.
배치가 실패하거나 스펙이 변경되어도 API E2E는 독립적으로 실행 가능하다.
Batch E2E(적재 검증)와 API E2E(조회 검증)를 분리함으로써 각 테스트의 실패 원인이 명확하게 드러난다.

---

## 7. 테스트 전략 요약

### 레이어별 역할 분담

| 레이어 | 테스트 클래스 | 테스트 더블 | 핵심 검증 대상 |
|---|---|---|---|
| Application (Facade) | `*FacadeTest` | Mock (Repository, ProductFacade) | 조합 로직, 가시성 필터, 날짜 기본값 |
| Interfaces (Controller) | `*ControllerTest` | MockMvc + MockBean (Facade) | 파라미터 파싱, 보정, JSON 직렬화 |
| Batch E2E | `*JobE2ETest` | 없음 (Testcontainers) | Job 실행, SQL 집계, MV 적재 정확성 |
| API E2E | `*ApiE2ETest` | 없음 (Testcontainers) | HTTP 전체 흐름, 페이징, 필터링 |

### 데이터 소스별 테스트 격리

| 기간 | 데이터 소스 | 검증 방식 |
|---|---|---|
| 일간 (daily) | Redis ZSET | Facade Mock → Repository stub |
| 주간 (weekly) | `mv_product_rank_weekly` | Batch E2E (적재) + API E2E (조회) |
| 월간 (monthly) | `mv_product_rank_monthly` | Batch E2E (적재) + API E2E (조회) |

### 슬라이딩 윈도우 경계 검증 근거

배치 SQL의 윈도우 조건은 `bucket_hour >= startTime AND bucket_hour < endTime`이다.

- **주간**: `startTime = targetDate - 7일 00:00`, `endTime = targetDate 00:00`
- **월간**: `startTime = targetDate - 30일 00:00`, `endTime = targetDate 00:00`

`excludesDataOutsideWindow` 테스트는 `targetDate - 8일`(주간) / `targetDate - 31일`(월간) 데이터가
score가 아무리 높아도 MV에 적재되지 않음을 직접 검증하여 윈도우 경계 오프셋 버그를 방지한다.
