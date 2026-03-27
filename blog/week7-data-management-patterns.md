# Data Management 패턴으로 이커머스 결제 시스템 점검하기

> microservices.io의 8가지 Data Management 패턴으로 현재 프로젝트를 점검하고, Event Sourcing 경량 적용(PaymentStatusHistory)을 도출한 기록.

---

## 1. 점검에 사용한 8가지 패턴

| 패턴 | 핵심 질문 |
|------|----------|
| **Database per Service** | 도메인별로 데이터가 격리되어 있는가? |
| **Saga** | 분산 트랜잭션을 어떻게 관리하는가? |
| **CQRS** | 읽기/쓰기 모델이 분리되어 있는가? |
| **Transactional Outbox** | 메시지 발행의 원자성을 어떻게 보장하는가? |
| **Polling Publisher** | Outbox 메시지를 어떻게 전달하는가? |
| **Event Sourcing** | 상태 변경 이력을 추적할 수 있는가? |
| **API Composition** | 여러 도메인의 데이터를 어떻게 조합하는가? |
| **Domain Event** | 도메인 간 통신에 이벤트를 활용하는가? |

---

## 2. 패턴별 적용 현황

| 패턴 | 적용 상태 | 핵심 발견 |
|------|----------|----------|
| Database per Service | 부분 적용 | 배치 대사(reconciliation)에서 cross-domain JOIN 존재 — **의도적 설계** |
| Saga | Orchestration 적용 | PaymentFacade가 오케스트레이터, 5-layer recovery 구조 |
| CQRS | 암묵적 적용 | product_metrics(읽기 모델), Product.like_count(비정규화), Redis 재고 캐시 |
| Transactional Outbox | 이중 적용 | PaymentOutbox(상태 기반, Polling) + EventOutbox(무상태, Debezium CDC) |
| Polling Publisher | Polling + CDC 하이브리드 | OutboxPollerScheduler(5초) + Debezium(CDC) |
| Event Sourcing | **미적용** | 결제 상태 전이 이력 없음 → **개선 대상** |
| API Composition | 적용 | ProductFacade(Product + Brand), OrderFacade(Order + Product + Coupon) |
| Domain Event | 적용 | DomainEventPublisher → Outbox + Spring Event 이중 발행 |

---

## 3. 핵심 발견: 5-layer Recovery의 추적 불가 문제

현재 결제 시스템은 5-layer recovery 구조로 상태를 복구한다:

```
Layer 1: Outbox Poller (5초)      — 미처리 결제 재시도
Layer 2: Callback DLQ (30초)      — 미수신 콜백 재처리
Layer 3: Payment Polling (10초)   — PG 상태 직접 확인
Layer 4: WAL Recovery (10초)      — DB 실패 시 WAL 파일 복구
Layer 5: Batch Recovery (1/5/10분) — 최종 안전망
```

**문제**: "어떤 경로로 최종 상태에 도달했는가?"를 추적할 수 없다.

- `PaymentModel.updatedAt`만 기록되고, `from_status` 정보가 유실
- 콜백으로 PAID가 되었는지, Polling으로 PAID가 되었는지, 배치로 FAILED가 되었는지 알 수 없음
- 결제 분쟁(dispute) 시 상태 변경 증거가 없음

---

## 4. 설계 선택: Event Sourcing 전체 vs History 테이블

### Event Sourcing 전체를 도입하지 않은 이유

| 항목 | Event Sourcing | History 테이블 |
|------|---------------|---------------|
| 상태 결정 방식 | 이벤트 재생으로 현재 상태 구성 | 현재 상태 + INSERT-only 로그 |
| 필요 인프라 | 이벤트 스토어 + 스냅샷 + CQRS 프로젝션 | 기존 DB에 테이블 1개 추가 |
| 복잡도 | 높음 (이벤트 버전닝, 스냅샷 전략) | 낮음 (INSERT만) |
| Payment 핵심 | "최종 상태가 무엇인가?" | 동일 |

Payment의 핵심은 **"최종 상태가 무엇인가"**이지 "모든 이벤트를 재생해서 상태를 구성"하는 것이 아니다. History 테이블은 Event Sourcing의 감사(audit) 측면만 경량 적용한 것.

---

## 5. 3가지 상태 변경 경로와 기록 전략

결제 상태가 변경되는 경로가 3가지 존재한다. 세 경로 모두 기록해야 History가 의미 있다.

### 경로 1: Entity 메서드 (PaymentFacade)

```java
// PaymentModel — @Transient 전이 리스트로 자동 추적
public void markPaid() {
    PaymentStatus from = this.status;
    validateTransition(PaymentStatus.PAID);
    this.status = PaymentStatus.PAID;
    pendingTransitions.add(new StatusTransition(from, PaymentStatus.PAID, "PG_RESPONSE", null));
}
```

`markPending → markPaid` 연속 호출 시 두 전이 모두 기록된다 (REQUESTED→PENDING, PENDING→PAID).
`PaymentRepositoryImpl.save()`에서 pendingTransitions를 자동으로 History 테이블에 INSERT.

### 경로 2: JPQL 조건부 UPDATE (PaymentRecoveryService, WalRecoveryScheduler)

```java
int affected = paymentRepository.updateStatusConditionally(
    payment.getId(), PaymentStatus.PAID, allowedStatuses);
if (affected > 0) {
    historyRepository.save(PaymentStatusHistory.create(
        payment.getId(), payment.getStatus(), PaymentStatus.PAID, "CALLBACK", null));
}
```

Entity 메서드를 우회하는 JPQL UPDATE이므로, 호출부에서 명시적으로 기록한다.

### 경로 3: Native SQL (PaymentRecoveryTasklet)

```java
// 복구 대상 ID 조회 → History INSERT → Status UPDATE 순서
List<Long> ids = ... // SELECT id FROM payments WHERE status = 'REQUESTED' ...
entityManager.createNativeQuery(
    "INSERT INTO payment_status_history (...) SELECT id, 'REQUESTED', 'FAILED', 'BATCH_RECOVERY', ... FROM payments WHERE id IN :ids"
).setParameter("ids", ids).executeUpdate();
```

JPA 엔티티를 완전히 우회하는 배치 복구이므로, companion INSERT로 기록한다.

---

## 6. 스킵한 개선점과 근거

분석 결과 도출된 다른 개선점들은 의도적으로 스킵했다:

| 개선점 | 판단 | 근거 |
|--------|------|------|
| Batch cross-domain JOIN 분리 | SKIP | 대사(reconciliation)는 정확도 최우선. `payments JOIN orders JOIN coupon_issue`를 이벤트 기반 검증으로 바꾸면 오히려 정합성 검증 신뢰도가 낮아짐 |
| Payment CQRS 명시적 분리 | SKIP | 결제 조회 트래픽이 상품 조회 대비 미미. 별도 Read Model의 ROI가 낮음 |
| PaymentFacade 분리 (287 lines) | SKIP | 결제 오케스트레이션은 단일 유스케이스. TX-0/TX-1/TX-2 경계를 분리하면 오히려 흐름 파악이 어려워짐 |
| Multi-instance Outbox Poller | SKIP | PG orderId 멱등성이 중복 처리를 이미 방지. 스케일아웃 시 SELECT FOR UPDATE 추가하면 됨 |

---

## 7. 산술 근거

- 피크 TPS 5,000 결제 요청 × 평균 2~3회 상태 전이 = 10,000~15,000 History INSERT/초
- INSERT-only 테이블, 인덱스 1개 (payment_id) → MySQL 8.0 기준 수만 rows/초 처리 가능
- 디스크: row당 ~100 bytes × 15,000/초 × 86,400초 ≈ **1.3GB/일** → created_at 기준 파티셔닝으로 관리
- 기존 payments 테이블 쓰기 성능에 미치는 영향: 별도 테이블이므로 기존 UPDATE 쿼리에 추가 부하 없음

---

## 8. 라이팅 포인트

1. **Event Sourcing은 "전부 아니면 전무"가 아니다** — 감사 로그(audit trail)만 필요하면 History 테이블로 충분하다. "이벤트 재생으로 상태를 구성"하는 풀 Event Sourcing은 요구사항이 정당화할 때 도입한다.

2. **"3가지 경로 모두 커버해야 한다"는 발견이 핵심** — Entity 메서드만 기록하면 JPQL/Native SQL 경로의 전이가 유실된다. 상태 변경 경로를 빠짐없이 파악하는 것이 History 설계의 출발점.

3. **"스킵한다"도 설계 판단이다** — 8가지 패턴을 점검했지만 실제로 구현한 개선은 1가지. 나머지 4가지를 스킵한 근거를 기록하는 것이 "왜 이렇게 했는가?"에 답하는 것.
