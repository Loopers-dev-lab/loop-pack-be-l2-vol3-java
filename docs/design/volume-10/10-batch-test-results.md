# ProductRankingMvJob E2E 테스트 결과

> 실행일: 2026-04-17
> 테스트 클래스: `ProductRankingMvJobE2ETest`
> 경로: `apps/commerce-batch/src/test/java/com/loopers/job/rankingmv/ProductRankingMvJobE2ETest.java`
> 결과: **9/9 PASSED** (기능 7 + 시각화 1 + 대규모 1)

---

## 테스트 환경

| 항목 | 값 |
|------|-----|
| DB | MySQL 8.0 (Testcontainers — Docker 컨테이너) |
| Spring Batch Test | `@SpringBatchTest` + `@SpringBootTest` |
| DDL | `schema-batch-test.sql` (BEFORE_TEST_CLASS) |
| targetDate | `20260416` |

---

## 테스트 목록

### 1. weeklySuccess — 주간 정상: 시드 데이터 기반 주간 TOP 100 적재

| 항목 | 내용 |
|------|------|
| **시나리오** | 상품 150개 + 7일치 메트릭 시드 → weekly Job 실행 |
| **검증** | Job 상태 COMPLETED, MV 100건 적재, 1위 = product_id 150 (최고 점수), staging 150건 전체 존재 |
| **결과** | PASSED |
| **의미** | 3-Step 파이프라인 (Cleanup → Partitioned Aggregate → Merge) 정상 동작. LIMIT 100 적용 확인 |

### 2. weeklyLessThan100Products — 주간: 상품이 100개 미만이면 있는 만큼만 적재

| 항목 | 내용 |
|------|------|
| **시나리오** | 상품 30개 + 7일치 메트릭 → weekly Job 실행 |
| **검증** | Job COMPLETED, MV 30건 (LIMIT 100이지만 데이터가 30개이므로 30건) |
| **결과** | PASSED |
| **의미** | TOP 100 상한은 있되 데이터가 부족하면 있는 만큼만 적재하는 유연한 처리 확인 |

### 3. monthlySuccess — 월간 정상: 30일 데이터 집계

| 항목 | 내용 |
|------|------|
| **시나리오** | 상품 50개 + 30일치 메트릭 → monthly Job 실행 |
| **검증** | Job COMPLETED, `mv_product_rank_monthly` 50건 적재 |
| **결과** | PASSED |
| **의미** | scope=monthly → 30일 윈도우 + `mv_product_rank_monthly` 테이블 분기 정상 동작 |

### 4. idempotentDoubleExecution — 멱등성: 같은 파라미터로 2회 실행해도 결과 동일

| 항목 | 내용 |
|------|------|
| **시나리오** | 상품 50개 + 7일치 메트릭 → weekly Job 2회 연속 실행 |
| **검증** | 2차 실행도 COMPLETED, MV 50건 (중복 없음) |
| **결과** | PASSED |
| **의미** | CleanupTasklet이 기존 period_key 데이터를 삭제 후 재적재 → 멱등성 보장. RunIdIncrementer로 JobInstance 구분 |

### 5. noDataProducesEmptyMv — 엣지: 데이터 없는 날짜로 실행하면 빈 MV

| 항목 | 내용 |
|------|------|
| **시나리오** | 상품 10개만 시드 (메트릭 없음) → weekly Job 실행 |
| **검증** | Job COMPLETED, MV 0건 |
| **결과** | PASSED |
| **의미** | product_metrics가 비어있어도 Job이 FAILED 되지 않고 정상 완료. Partitioner가 빈 범위를 안전하게 처리 |

### 6. partialDataAggregated — 엣지: 7일 미만 데이터면 있는 만큼만 집계

| 항목 | 내용 |
|------|------|
| **시나리오** | 상품 20개 + 3일치 메트릭 (7일 미만) → weekly Job 실행 |
| **검증** | Job COMPLETED, MV 20건 |
| **결과** | PASSED |
| **의미** | 슬라이딩 윈도우 7일 중 3일만 있어도 있는 데이터만으로 집계. 부분 데이터 허용 |

### 7. cancellationReflectedInScore — 엣지: 취소 반영: cancel_amount가 score에 반영

| 항목 | 내용 |
|------|------|
| **시나리오** | 상품1: 매출 100만/취소 0, 상품2: 매출 200만/취소 150만(순매출 50만) → weekly Job 실행 |
| **검증** | Job COMPLETED, 1위 = product_id 1 (순매출 100만 > 50만) |
| **결과** | PASSED |
| **의미** | Score SQL에서 `sales_amount - cancel_amount_by_event_date` 반영 확인. 취소가 많은 상품의 순위 하락 검증 |

---

## 수정 이력 (테스트 통과를 위한 코드 수정)

### 수정 1: Partitioner를 Bean에서 private 메서드로 변경

**파일**: `ProductRankingMvJobConfig.java`

| Before | After |
|--------|-------|
| `@JobScope @Bean productIdPartitioner()` | `private Partitioner createPartitioner(targetDate, scope)` |

**원인**: `@Value("#{jobParameters['targetDate']}")` SpEL을 사용하는 Bean에 `@JobScope`가 없어 context 로딩 시 `SpelEvaluationException` 발생. `@JobScope`를 추가하면 `@SpringBatchTest`의 `JobScopeTestExecutionListener`와 충돌.

**해결**: Partitioner를 Spring Bean이 아닌 private 메서드로 변경하여 `partitionedAggregateStep` 내부에서 직접 호출. `targetDate`, `scope`는 이미 `@JobScope`인 step 메서드의 파라미터로 주입받으므로 별도 Bean 불필요.

### 수정 2: runJob 반환 타입 변경

**파일**: `ProductRankingMvJobE2ETest.java`

| Before | After |
|--------|-------|
| `private JobExecution runJob(String scope)` | `private BatchStatus runJob(String scope)` |

**원인**: `@SpringBatchTest`의 `JobScopeTestExecutionListener`가 테스트 클래스의 모든 `getDeclaredMethods()`를 스캔하여 `JobExecution` 반환 타입 메서드를 찾음. `runJob(String)`을 발견하고 인자 없이 호출 시도 → `HippyMethodInvoker`에서 `No matching arguments found for method: runJob` 에러.

**해결**: 반환 타입을 `BatchStatus`로 변경하여 listener의 스캔 대상에서 제외. 모든 테스트는 `execution.getStatus()`만 사용하므로 기능적 영향 없음.

---

## 커버리지 분석

| 검증 범위 | 테스트 |
|-----------|--------|
| **3-Step 파이프라인 정상 흐름** | weeklySuccess, monthlySuccess |
| **Partitioning (product_id 범위 분할)** | weeklySuccess (150개 → GRID_SIZE=4 파티션) |
| **LIMIT 100 상한** | weeklySuccess (150개 중 100개), weeklyLessThan100Products (30개 중 30개) |
| **scope 분기 (weekly/monthly)** | weeklySuccess, monthlySuccess |
| **멱등성 (Cleanup + RunIdIncrementer)** | idempotentDoubleExecution |
| **빈 데이터 안전 처리** | noDataProducesEmptyMv |
| **부분 기간 데이터** | partialDataAggregated |
| **취소 반영 (cancel_amount)** | cancellationReflectedInScore |
| **Score 순위 정확성** | weeklySuccess (1위=150L), cancellationReflectedInScore (1위=1L) |

---

## 실 환경 배치 실행 + API 호출 검증

> 실행일: 2026-04-17
> 환경: Docker MySQL 8.0 + Redis Master/Replica + commerce-api (localhost:8080)
> 캡처 파일: [`docs/captures/04-ranking-api-capture.md`](../captures/04-ranking-api-capture.md)

### 데이터 규모

| 항목 | 값 |
|------|-----|
| 노트북 사양 | Apple M5 Pro, 18코어, 48GB RAM |
| 상품 수 | 1,020개 (20브랜드 × 50종 + 기본 20개) |
| 메트릭 행 수 | 30,600행 (1,020 × 30일) |
| 데이터 생성 방식 | Python 스크립트로 브랜드명 + 모델명 + 컬러/사이즈 조합 랜덤 생성 (크롤링 아님) |

### 시드 데이터 트렌드 패턴 (6가지)

| 타입 | 비율 | 설명 |
|------|------|------|
| A) 급상승 | 5% (51개) | 과거 23일 미미 → 최근 7일 폭발 (view 6K, sales 250만/일) |
| B) 장기 강자 | 10% (102개) | 30일 꾸준히 높음 (view 3.5K, sales 180만/일) |
| C) 하락 추세 | 5% (51개) | 과거 23일 높음 → 최근 7일 급락 |
| D) 오늘 바이럴 | 2% (20개) | 오늘만 폭발 (view 18K, sales 600만) |
| E) 취소 높음 | 3% (31개) | 매출 높지만 취소 50~70% |
| F) 일반 | 75% (765개) | 보통 수준 (view 500, sales 20만/일) |

### 배치 실행 결과

| 항목 | weekly | monthly |
|------|--------|---------|
| 파티션 | 4 (productId 1~255, 256~510, 511~765, 766~1020) | 4 |
| 소요 시간 | 275ms | 309ms |
| 적재 건수 | 100 (TOP 100) | 100 |
| 메트릭 기간 | 7일 (04-01~04-07) | 30일 (03-08~04-07) |
| Job 상태 | COMPLETED | COMPLETED |

### API 호출 결과 (TOP 5 비교)

```
GET /api/v1/rankings?scope={daily|weekly|monthly}&date=20260407&page=0&size=20
```

| 순위 | 일간 (Redis) | 주간 (MV) | 월간 (MV) |
|:----:|-------------|-----------|-----------|
| 1 | 아디다스 캠퍼스 올리브 (바이럴) | 나이키 에어리프트 카키 (급상승) | 반스 슬립온 올리브 (장기강자) |
| 2 | 살로몬 아웃펄스 네이비 (바이럴) | 컨버스 런스타하이크 그레이 (급상승) | 스투시 카고바지 화이트 (장기강자) |
| 3 | 뉴발란스 530 올리브 (바이럴) | 스투시 월드투어후디 카키 (급상승) | 리복 클럽C85 인디고 (장기강자) |
| 4 | 디스이즈네버댓 SP로고T (바이럴) | 아디다스 포럼 네이비 (급상승) | 노스페이스 1996레트로 크림 (장기강자) |
| 5 | 컨버스 올스타 블랙 (바이럴) | 아디다스 오즈위고 크림 (급상승) | 뉴발란스 990v6 인디고 (장기강자) |

### 핵심 관찰

1. **일간/주간/월간 TOP 20이 완전히 다른 상품으로 구성** — Lambda Architecture의 시간 윈도우별 랭킹 차이가 명확
2. **바이럴 상품**: 일간 1위 → 주간 100위 밖 → 월간 100위 밖 (1일치만 반영)
3. **급상승 상품**: 일간 중위 → 주간 상위 → 월간 100위 밖 (23일간 미미)
4. **장기 강자**: 일간 하위 → 주간 하위 → 월간 상위 (30일 꾸준한 실적)
5. **Score 범위**: daily 0.73~0.83 < weekly 0.84~0.88 < monthly 0.94~0.96 (누적 기간에 비례)
6. **취소 반영**: 취소율 50~70% 상품은 순매출 차감으로 순위 하락 확인

---

## 대규모 테스트 결과 (10만 건)

> 실행일: 2026-04-17
> 환경: Testcontainers MySQL 8.0 (`--innodb-buffer-pool-size=256M`) + Gradle `-Xmx2g`
> 테스트 메서드: `largeScalePartitionedBatchTest`

### 데이터 규모

| 항목 | 값 |
|------|-----|
| 상품 수 | 100,000개 |
| 메트릭 행 수 | 3,000,000행 (100,000 × 30일) |
| 시드 방식 | `JdbcTemplate.batchUpdate()` (1,000건씩 벌크 INSERT) |
| 상품 시드 소요 | 1,137ms |
| 메트릭 시드 소요 | 79,518ms (~80초) |

### 6가지 트렌드 패턴

| 그룹 | Product ID 범위 | 비율 | 설명 |
|------|----------------|------|------|
| A) 급상승 | 1~5,000 | 5% | 최근 7일 폭발 (view 9K, sales 300만/일), 이전 미미 |
| B) 장기강자 | 5,001~15,000 | 10% | 30일 꾸준히 높음 (view 3K, sales 200만/일) |
| C) 하락추세 | 15,001~20,000 | 5% | 이전 높음 → 최근 7일 급락 |
| D) 바이럴 | 20,001~22,000 | 2% | 오늘만 폭발 (view 15K, sales 500만) |
| E) 취소높음 | 22,001~25,000 | 3% | 매출 높지만 취소 50~70% |
| F) 일반 | 25,001~100,000 | 75% | 보통 수준 |

### 배치 실행 결과

| 항목 | weekly | monthly |
|------|--------|---------|
| Partitioning | 4 Worker (각 25,000건 균등) | 4 Worker (각 25,000건 균등) |
| 소요 시간 | **2,205ms** | **2,564ms** |
| MV 적재 건수 | 100 (TOP 100) | 100 (TOP 100) |
| Staging 적재 | 100,000건 | 100,000건 |
| Job 상태 | COMPLETED | COMPLETED |

### Step별 소요 시간

| Step | weekly | monthly |
|------|--------|---------|
| cleanupStep | 19ms | 352ms (staging 10만건 삭제) |
| partitionedAggregateStep | 1,977ms | 2,014ms |
| ├ Worker 1 (partition0) | 1,663ms | 1,269ms |
| ├ Worker 2 (partition1) | 1,695ms | 1,274ms |
| ├ Worker 3 (partition2) | 1,642ms | 1,315ms |
| └ Worker 4 (partition3) | 1,697ms | 1,274ms |
| mergeStep | 74ms | 74ms |

### 1위 검증

| scope | 1위 상품 | 트렌드 유형 | 의미 |
|-------|---------|-----------|------|
| weekly | product_5000 (급상승) | 최근 7일 폭발 | 7일 윈도우에서 급상승 상품이 장기강자를 이김 |
| monthly | product_15000 (장기강자) | 30일 꾸준히 높음 | 30일 윈도우에서 장기강자가 급상승을 역전 |

### 파티션 균등 분배

```
[Partitioner] partition0: productId 1~25000 (25,000건)
[Partitioner] partition1: productId 25001~50000 (25,000건)
[Partitioner] partition2: productId 50001~75000 (25,000건)
[Partitioner] partition3: productId 75001~100000 (25,000건)
```

DISTINCT product_id 사전 조회 기반 분할로 4 파티션 완전 균등 분배. Worker별 소요 시간 편차 < 60ms.

### 규모별 성능 비교

| 규모 | 상품 수 | 메트릭 행 수 | weekly | monthly |
|------|--------|------------|--------|---------|
| 기능 테스트 | 150 | 1,050 | ~90ms | — |
| 실환경 검증 | 1,020 | 30,600 | 275ms | 309ms |
| **대규모 테스트** | **100,000** | **3,000,000** | **2,205ms** | **2,564ms** |

데이터가 100배 증가해도 소요 시간은 ~8배만 증가 — Partitioning + GROUP BY 최적화로 sub-linear scaling 달성.
