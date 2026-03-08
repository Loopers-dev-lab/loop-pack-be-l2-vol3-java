---
name: analyze-query
description: 'Spring @Transactional, JPA, QueryDSL 코드의 트랜잭션 범위, 영속성 컨텍스트, 쿼리 실행 시점을 분석하고 불필요하게 큰 트랜잭션이나 의도치 않은 쿼리 발생 가능성을 점검합니다. "트랜잭션 분석해줘", "쿼리 분석", "영속성 컨텍스트 점검", "/analyze-query order"를 요청할 때 사용합니다. 코드 구현이나 테스트 작성에는 사용하지 마세요.'
argument-hint: "{domain}"
---

# Analyze Query

Spring @Transactional, JPA, QueryDSL 코드를 4축 × 16항목으로 분석하여 트랜잭션 범위, 영속성 컨텍스트, 쿼리 실행 관점의 개선 가능 지점을 보고합니다.

## 3-Phase 워크플로우

```
COLLECT  → Facade, Service, Repository, Entity 코드 수집
ANALYZE  → 4축 × 16항목 점검
REPORT   → 요약표 + 트랜잭션 범위 트리 + 발견 사항 + 개선 제안
```

---

## Phase 0: 인자 확인

인자 없이 호출된 경우(`/analyze-query`만), 분석 대상 도메인을 사용자에게 확인한다.

- `application/` 하위 패키지를 탐색하여 도메인 목록을 보여준다
- 사용자가 선택하면 해당 도메인으로 Phase 1을 시작한다

---

## Phase 1: COLLECT

분석 대상 코드를 수집합니다. 읽기 전용이므로 사용자 확인 없이 바로 ANALYZE로 진입합니다.

### 절차

1. 대상 도메인의 코드를 계층별로 읽는다:
   - `interfaces/` — Controller
   - `application/` — Facade, Service, Command, Info
   - `domain/` — Entity, Repository 인터페이스, Domain Service
   - `infrastructure/` — Repository 구현체, JpaRepository
2. Facade에서 호출하는 **타 도메인의 Service**도 수집한다
3. 체크리스트를 상기한다: `references/analysis-checklist.md` 읽기

### 산출물

분석 대상 파일 목록 (내부 메모, 사용자에게 공유하지 않음)

---

## Phase 2: ANALYZE

4축 × 16개 항목을 순서대로 점검합니다. 각 항목의 상세 확인 방법은 [references/analysis-checklist.md](references/analysis-checklist.md) 참고.

### 축 1: Transaction Boundary (TB1~TB3)

트랜잭션의 시작 지점, 포함 작업, 범위를 파악합니다.

| # | 항목 | 확인 내용 |
|---|------|---------|
| TB1 | 시작 지점 | @Transactional 선언 위치 (Facade / Service / 그 외) |
| TB2 | 작업 분류 | 트랜잭션 내부 작업을 쓰기 / 조회 / 외부호출로 분류 |
| TB3 | 범위 시각화 | 유스케이스별 호출 트리 형태로 트랜잭션 범위 표현 |

### 축 2: 불필요한 범위 (OT1~OT5)

트랜잭션이 불필요하게 크게 잡혀 있는 패턴을 식별합니다.

| # | 항목 | 확인 내용 |
|---|------|---------|
| OT1 | Controller @Transactional | Controller에서 @Transactional 사용 여부 |
| OT2 | 읽기/쓰기 혼합 | 읽기 전용 로직이 쓰기 트랜잭션에 포함 여부 |
| OT3 | 외부 시스템 호출 포함 | 외부 API/메시지 발행이 트랜잭션 내부에 포함 여부 |
| OT4 | 대량 조회 | 트랜잭션 내부에서 대량 조회/복잡한 QueryDSL 실행 여부 |
| OT5 | 긴 트랜잭션 유지 | 상태 변경 이후에도 트랜잭션이 길게 유지되는 여부 |

### 축 3: 영속성 컨텍스트 (PC1~PC5)

JPA 영속성 컨텍스트 관점에서 의도치 않은 동작 가능성을 점검합니다.

| # | 항목 | 확인 내용 |
|---|------|---------|
| PC1 | flush 시점 | Entity 변경이 언제 flush 되는지, 의도치 않은 flush 가능성 |
| PC2 | 변경 감지 범위 | 조회용 Entity가 변경 감지(dirty checking) 대상이 되는지 |
| PC3 | 지연 로딩 | Lazy Loading으로 트랜잭션 후반에 추가 쿼리 발생 가능성 |
| PC4 | readOnly 미적용 | 조회 전용 메서드에 @Transactional(readOnly = true) 적용 여부 |
| PC5 | DTO Projection 적합성 | Entity 조회 대신 DTO Projection이 더 적합한 경우 |

### 축 4: 쿼리 실행 (QE1~QE3)

실제 발생하는 쿼리의 효율성과 정합성을 점검합니다.

| # | 항목 | 확인 내용 |
|---|------|---------|
| QE1 | N+1 쿼리 | 컬렉션 연관관계 조회 시 N+1 발생 가능성 |
| QE2 | 중복 조회 | 같은 Entity를 동일 트랜잭션 내에서 여러 번 조회하는지 |
| QE3 | 락 전략 | 동시성 제어가 필요한 지점에 적절한 락이 적용되었는지 |

### 판정 기준

각 항목은 다음 중 하나로 판정합니다:

| 판정 | 의미 |
|------|------|
| OK | 문제 없음 |
| WARN | 현재는 문제 아니지만 주의가 필요한 패턴 |
| ISSUE | 개선이 필요한 문제 발견 |
| N/A | 해당 없음 (해당 계층 없음, QueryDSL 미사용 등) |

---

## Phase 3: REPORT

분석 결과를 정리하여 보고합니다.

### 보고 형식

```
## 트랜잭션 분석: {도메인명}

### 요약
| 축 | OK | WARN | ISSUE | N/A |
|----|-----|------|-------|-----|
| Transaction Boundary | N | N | N | N |
| 불필요한 범위 | N | N | N | N |
| 영속성 컨텍스트 | N | N | N | N |
| 쿼리 실행 | N | N | N | N |

### 유스케이스별 트랜잭션 범위
{유스케이스명} — @Transactional (Facade)
  ├─ {작업1} [쓰기]
  ├─ {작업2} [조회]
  └─ {작업3} [쓰기]

### 발견 사항 (WARN/ISSUE만)
#### [{코드}] {항목명} — {판정}
- **위치**: {파일:라인}
- **내용**: 무엇이 발견되었는지
- **영향**: 어떤 문제가 발생할 수 있는지

### 개선 제안 (선택)
#### 제안 1: {제목}
- **현재**: 현재 구조 설명
- **제안**: 개선 방향
- **트레이드오프**: 개선 시 고려할 점
```

**WARN/ISSUE가 없으면**: "모든 항목 OK. 발견 사항 없음." 으로 간결하게 보고한다.

---

## 예시

사용자: `/analyze-query order`

1. **COLLECT**: OrderFacade, OrderService, OrderRepository, Order Entity + 타 도메인 Service 수집
2. **ANALYZE**: TB1~TB3 → OT1~OT5 → PC1~PC5 → QE1~QE3 순서로 점검
3. **REPORT**: 요약표 + 트랜잭션 범위 트리 + 발견 사항 + 개선 제안

---

## 트러블슈팅

### 도메인 코드가 없는 경우
- "해당 도메인의 코드를 찾을 수 없습니다. 도메인명을 확인해주세요."

### Facade가 없는 경우
- Service를 트랜잭션 시작 지점으로 분석한다
- OT 축에서 Facade 부재를 WARN으로 보고한다

### QueryDSL이 없는 경우
- OT4를 N/A로 처리한다
- JpaRepository 쿼리 메서드 기준으로 분석한다

### 크로스 도메인 호출이 있는 경우
- 타 도메인 Service의 트랜잭션 전파(propagation) 동작을 함께 분석한다
- 트랜잭션 범위 트리에 타 도메인 호출을 명시한다
