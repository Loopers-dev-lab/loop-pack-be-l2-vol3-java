# 향후 확장 시퀀스 다이어그램

> 현재 구현 범위 이후 결제/쿠폰 도메인 도입 시 예상되는 핵심 흐름을 시퀀스로 정리한다.

---

## A. 재고 예약 + 쿠폰 홀드 + 결제 흐름

현재 설계에서는 `POST /api/v1/orders`로 직접 주문을 생성하지만, 향후 결제/쿠폰 도메인이 추가되면 재고 예약(Inventory) + 쿠폰 홀드(IssuedCoupon) + 결제(Payment) 흐름으로 확장한다.

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Service as OrderSheetService
  participant Inventory as Inventory(Entity)
  participant InvRepo as InventoryRepository
  participant Coupon as IssuedCoupon(Entity)
  participant CouponRepo as IssuedCouponRepository
  participant Payment as PaymentService
  participant Order as Order(Entity)

  Note over User,Service: 1단계: 주문서 생성 + 재고 예약 + 쿠폰 홀드

  User->>Service: createOrderSheet(userId, items, couponId)
  loop 각 item에 대해
    Service->>InvRepo: findByProductIdForUpdate(productId)
    InvRepo-->>Service: inventory(locked)
    Service->>Inventory: reserve(quantity)
    Note over Inventory: reserved_qty += quantity<br/>가용 = quantity - reserved_qty
    alt 가용 재고 부족
      Inventory-->>Service: 409 재고 부족
      Note over Service: 롤백 - 이전 예약 모두 해제
    else 예약 성공
      Service->>InvRepo: save(inventory)
    end
  end

  opt 쿠폰 적용
    Service->>CouponRepo: findById(couponId)
    CouponRepo-->>Service: issuedCoupon
    Service->>Coupon: reserve()
    Note over Coupon: status: ISSUED → RESERVED
    Service->>CouponRepo: save(issuedCoupon)
  end

  Service-->>User: OrderSheet(status=READY, expiresAt=+30min)

  Note over User,Payment: 2단계: 결제 요청

  User->>Payment: requestPayment(orderSheetId)
  Payment->>Payment: PG 승인 요청

  alt 결제 성공
    Note over Payment,Order: 재고 확정 차감 + 쿠폰 사용 확정 + 주문 생성
    loop 각 item에 대해
      Payment->>Inventory: confirmReservation(quantity)
      Note over Inventory: quantity -= qty<br/>reserved_qty -= qty
    end
    Payment->>Coupon: redeem(orderId)
    Note over Coupon: status: RESERVED → REDEEMED
    Payment->>Order: createOrder(status=PAID)
    Payment-->>User: 200 주문 완료

  else 결제 실패
    Note over Payment,Coupon: 예약 해제 + 쿠폰 복구
    loop 각 item에 대해
      Payment->>Inventory: releaseReservation(quantity)
      Note over Inventory: reserved_qty -= qty
    end
    Payment->>Coupon: release()
    Note over Coupon: status: RESERVED → ISSUED
    Payment-->>User: 결제 실패 안내
  end
```

**책임 분리 포인트**:
- `Inventory(Entity)`: 예약/확정/해제 (도메인 행위 - 재고 보호는 재고의 책임)
- `IssuedCoupon(Entity)`: 상태 전이 reserve/redeem/release (도메인 행위)
- `OrderSheetService`: 예약 조율 (애플리케이션 서비스)
- `PaymentService`: 결제 결과에 따른 확정/롤백 조율

---

## B. 결제 콜백 (멱등/역순 대응)

```mermaid
sequenceDiagram
  autonumber
  participant PG as PG(외부)
  participant Callback as PaymentCallbackHandler
  participant PaymentRepo as PaymentRepository
  participant Payment as Payment(Entity)

  PG->>Callback: POST /payments/callback {txn_id, status, amount}

  Callback->>PaymentRepo: findByTransactionId(txn_id)
  alt 이미 처리된 결제 (terminal 상태)
    PaymentRepo-->>Callback: payment(APPROVED or FAILED)
    Note over Callback: 멱등 - 상태 변경 금지
    Callback-->>PG: 200 OK (이미 처리됨)
  else 처리 대기 (PENDING)
    PaymentRepo-->>Callback: payment(PENDING)
    alt 콜백 성공
      Callback->>Payment: approve(amount)
      Note over Payment: status: PENDING → APPROVED
      Note over Callback: Inventory.confirmReservation + Coupon.redeem
    else 콜백 실패
      Callback->>Payment: fail(failureCode, message)
      Note over Payment: status: PENDING → FAILED
      Note over Callback: Inventory.releaseReservation + Coupon.release
    end
    Callback-->>PG: 200 OK
  end
```

**핵심 정책**:
- `transaction_id` 기반 멱등 처리 - 동일 콜백 재전송 시 상태 변경 없음
- terminal 상태(APPROVED, FAILED)에 도달하면 이후 콜백은 무시
- 성공: `Payment=APPROVED`, `Inventory=CONFIRMED`, `Coupon=REDEEMED`
- 실패: `Payment=FAILED`, `Inventory=RELEASED`, `Coupon=ISSUED`
