# Phase 0: 기반 세팅

## 설계 결정

### Payment 상태 머신
```
PENDING ──→ IN_PROGRESS ──→ PAID
  (PG 요청 전/실패)  (PG 접수됨)    (결제 성공)
                         └──→ FAILED
                              (결제 실패)
```

- PENDING: PG 요청 전 또는 PG 호출 자체가 실패한 상태. **재시도/복구 대상**
- IN_PROGRESS: PG에서 transactionKey를 받은 상태. 콜백 대기 중
- PAID/FAILED: 최종 확정 상태

### Order 상태 확장
```
ORDERED → PAYMENT_PENDING → PAID
                           → PAYMENT_FAILED → CANCELLED
```

### Payment를 별도 Aggregate로 분리한 이유
1. **트랜잭션 경계 분리**: PG 호출(외부 시스템)은 @Transactional 밖에서 수행해야 한다. Payment와 Order가 같은 Aggregate이면 이 분리가 어려움
2. **독립적 상태 머신**: Payment는 PG 시스템의 비동기 흐름을 추적하는 자체 상태를 가짐
3. **복구 용이성**: PENDING/IN_PROGRESS 상태의 Payment만 조회해서 일괄 복구 가능

### 상태 전이를 코드로 방어하는 이유
- `PAID → PENDING` 같은 비정상 전이를 원천 차단
- 동시성 이슈(콜백 중복 수신 등)에서도 잘못된 상태 변경을 방지
- 디버깅 시 현재 상태가 명확하게 드러남
