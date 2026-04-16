# 랭킹 배치 시스템 설계 의사결정

이 문서는 랭킹 배치 파이프라인 구현 과정에서 내린 주요 설계 의사결정을 기록합니다.

---

## 1. Batch 처리 방식: Chunk-Oriented vs Tasklet

### 선택지

| 옵션 | 설명 |
|------|------|
| A. Chunk-Oriented | Reader → Processor → Writer 패턴 |
| B. Tasklet | 단일 `INSERT INTO SELECT` 쿼리 |

### 장단점 비교

| 관점 | Chunk-Oriented (A) | Tasklet (B) |
|------|-------------------|-------------|
| **메모리 효율** | 청크 단위 처리로 대용량 가능 | 전체 데이터 한 번에 처리 |
| **구현 복잡도** | 높음 (Reader/Processor/Writer 분리) | 낮음 (SQL 한 줄) |
| **확장성** | Processor에서 로직 추가 용이 | SQL 수정 필요 |
| **장애 대응** | 청크 단위 재시작 가능 | 전체 롤백 |
| **디버깅** | 각 단계별 로깅/모니터링 가능 | SQL 결과로만 확인 |

### 최종 선택: **A. Chunk-Oriented**

**근거:**
1. 상품 수가 수만~수십만 개로 증가할 경우 메모리 이슈 방지
2. Processor에서 rank_number 부여 로직 분리 (단위 테스트 용이)
3. Spring Batch의 재시작, 스킵, 리스너 기능 활용 가능
4. 각 단계별 로깅으로 운영 모니터링 편의

**트레이드오프:**
- Tasklet 대비 코드량 증가
- 단순 집계에는 과한 구조일 수 있음

**향후 개선:**
- 대용량 처리 시 `JdbcPagingItemReader` + 파티셔닝 검토

---

## 2. MV 갱신 전략: DELETE + INSERT vs MERGE (UPSERT)

### 선택지

| 옵션 | 설명 |
|------|------|
| A. DELETE + INSERT | 해당 기간 전체 삭제 후 새로 삽입 |
| B. UPSERT | `ON DUPLICATE KEY UPDATE` 활용 |

### 장단점 비교

| 관점 | DELETE + INSERT (A) | UPSERT (B) |
|------|--------------------| -----------|
| **멱등성** | 완벽 보장 | 보장 (단, 삭제된 상품 처리 주의) |
| **구현 복잡도** | 단순 | UNIQUE 제약 조건 관리 필요 |
| **성능 (삽입)** | Bulk INSERT 최적화 가능 | 행마다 중복 체크 |
| **성능 (갱신)** | 전체 재생성 | 변경분만 갱신 |
| **데이터 정합성** | 항상 최신 스냅샷 | 삭제된 상품 잔류 가능 |

### 최종 선택: **A. DELETE + INSERT**

**근거:**
1. TOP 100 랭킹은 전체가 변경될 수 있어 "변경분만 갱신" 이점이 작음
2. 삭제된 상품이 랭킹에 남는 문제 원천 차단
3. 구현 단순화 (`deleteByPeriodStartDate()` + `saveAll()`)
4. 청크 처리에서 첫 번째 청크 Write 시점에 DELETE 실행으로 멱등성 보장

**트레이드오프:**
- 데이터가 많아지면 DELETE 비용 증가
- 삭제 후 INSERT 전 시점에 순간적 데이터 부재 (읽기 일관성)

**향후 개선:**
- 읽기 일관성이 중요해지면 새 파티션에 INSERT 후 파티션 스왑 검토

---

## 3. 일간 랭킹 데이터 소스: Redis vs MV 테이블

### 선택지

| 옵션 | 설명 |
|------|------|
| A. Redis 유지 | 일간은 기존 Redis ZSET, 주간/월간만 MV |
| B. MV 통일 | 일간도 product_metrics_daily에서 배치 집계 |

### 장단점 비교

| 관점 | Redis 유지 (A) | MV 통일 (B) |
|------|---------------|-------------|
| **실시간성** | 즉시 반영 | 배치 주기만큼 지연 |
| **아키텍처 일관성** | 이원화 (Redis + DB) | 단일화 (DB만) |
| **운영 복잡도** | Redis 장애 대응 필요 | DB만 관리 |
| **응답 형식 통일** | viewCount 등 null | 모든 필드 제공 가능 |
| **쓰기 부하** | Redis (빠름) | DB (상대적 느림) |

### 최종 선택: **A. Redis 유지**

**근거:**
1. 일간 랭킹은 사용자 행동(조회, 좋아요, 주문)에 즉시 반영되어야 함
2. 이벤트 기반 점수 갱신(`ZINCRBY`)이 Redis에 최적화됨
3. 일간 배치를 추가하면 배치 Job이 3개로 늘어나 운영 부담 증가
4. 기존 인프라(Redis) 활용으로 추가 리소스 불필요

**트레이드오프:**
- 일간/주간/월간 응답 필드가 다름 (일간은 viewCount 등 null)
- Redis 장애 시 일간 랭킹 불가능

**향후 개선:**
- Redis 장애 시 product_metrics_daily 기반 폴백 쿼리 추가 검토
- 응답 필드 통일 필요 시 일간도 MV 테이블 도입 고려

---

## 4. API 분기 전략: Strategy 패턴 vs Switch 분기

### 선택지

| 옵션 | 설명 |
|------|------|
| A. Strategy 패턴 | `RankingStrategy` 인터페이스 + DailyStrategy/WeeklyStrategy/MonthlyStrategy |
| B. Switch 분기 | Service 내 `switch (period)` 로 분기 |

### 장단점 비교

| 관점 | Strategy (A) | Switch (B) |
|------|-------------|-----------|
| **코드량** | 많음 (인터페이스 + 3개 구현체) | 적음 (한 파일 내) |
| **확장성** | 새 기간 추가 시 클래스만 추가 | switch에 case 추가 |
| **테스트** | 각 Strategy 독립 테스트 | 통합 테스트 위주 |
| **런타임 유연성** | DI로 동적 선택 가능 | 컴파일 타임 결정 |
| **학습 비용** | 패턴 이해 필요 | 직관적 |

### 최종 선택: **B. Switch 분기**

**근거:**
1. 현재 분기가 3개(DAILY, WEEKLY, MONTHLY)로 적음
2. 각 분기의 로직이 거의 동일 (날짜 범위 계산 + Repository 호출)
3. 오버엔지니어링 방지 (YAGNI 원칙)
4. 코드 가독성 및 유지보수 용이

**코드 예시:**
```java
public List<PeriodRankingResult> getPeriodRankings(LocalDate date, RankingPeriod period, ...) {
    return switch (period) {
        case DAILY -> getDailyPeriodRankings(date, ...);
        case WEEKLY -> getWeeklyPeriodRankings(date, ...);
        case MONTHLY -> getMonthlyPeriodRankings(date, ...);
    };
}
```

**향후 개선:**
- 기간 타입이 5개 이상으로 늘거나, 각 타입별 복잡한 비즈니스 로직이 필요해지면 Strategy 패턴 리팩토링 검토
- 이 결정 사항은 `RankingPeriod.java`에 주석으로 문서화됨

---

## 5. Batch Job 분리 vs 통합

### 선택지

| 옵션 | 설명 |
|------|------|
| A. Job 분리 | `weeklyRankingJob`, `monthlyRankingJob` 별도 정의 |
| B. Job 통합 | `rankingAggregationJob` 하나에 period 파라미터로 분기 |

### 장단점 비교

| 관점 | Job 분리 (A) | Job 통합 (B) |
|------|-------------|-------------|
| **운영 명확성** | Job 이름만으로 역할 구분 | 파라미터까지 확인 필요 |
| **스케줄링** | 각각 독립적 cron 설정 | 조건부 로직 필요 |
| **코드 재사용** | 공통 클래스 추출로 해결 | 자연스러운 재사용 |
| **장애 격리** | 한 Job 실패가 다른 Job에 영향 없음 | Step 단위로 격리 가능 |
| **모니터링** | Job별 독립 메트릭 | 파라미터로 필터링 |

### 최종 선택: **A. Job 분리**

**근거:**
1. 주간/월간 배치 실행 주기가 다름 (주간: 매주 월요일, 월간: 매월 1일)
2. Job 이름으로 운영 담당자가 쉽게 식별 가능
3. Admin API에서 `/weekly-ranking`, `/monthly-ranking` 으로 명확한 엔드포인트
4. 한 Job 실패 시 다른 Job에 영향 없음

**공통 로직 추출:**
```
batch/job/common/
├── RankingJobConstants.java    # 상수, SQL 템플릿
└── RankingMetricsAggregation.java  # 집계 DTO + RowMapper
```

**트레이드오프:**
- Job 설정 파일이 2개로 늘어남
- Reader/Processor/Writer 로직 일부 중복

**향후 개선:**
- 분기별(QUARTERLY), 연간(YEARLY) 랭킹 추가 시 동일 패턴으로 Job 추가
- 공통 로직이 더 많아지면 `AbstractRankingJobConfig` 추상 클래스 검토

---

## 요약

| 의사결정 | 최종 선택 | 핵심 근거 |
|----------|----------|-----------|
| Batch 처리 방식 | Chunk-Oriented | 대용량 처리, 확장성, 재시작 지원 |
| MV 갱신 전략 | DELETE + INSERT | 멱등성 완벽 보장, 구현 단순 |
| 일간 데이터 소스 | Redis 유지 | 실시간성 우선 |
| API 분기 전략 | Switch 분기 | 현재 복잡도에 적합, YAGNI |
| Batch Job | 분리 | 운영 명확성, 장애 격리 |