# 02. Payment 도메인 설계

## 목적

주문에 대한 결제 정보를 관리하는 도메인 모델을 설계한다.
PG 시스템과의 연동 결과를 내부 시스템에 반영하기 위한 상태 관리가 핵심이다.

---

## 핵심 설계 판단

### 1. Order vs Payment 분리 여부

| 방안 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **A. Order에 결제 상태 포함** | OrderStatus에 PAYMENT_PENDING 등 추가 | 단순함, 조회 쉬움 | 주문과 결제 책임 혼재, 재결제 시 상태 이력 유실 |
| **B. Payment 엔티티 분리 (추천)** | 별도 PaymentModel로 결제 이력 관리 | 1주문:N결제 가능, 책임 분리 명확 | 조회 시 JOIN 필요 |

**추천: B안** — 하나의 주문에 대해 결제 실패 → 재시도가 가능하므로, 결제 시도마다 별도 레코드가 필요하다.

### 2. 결제 상태 모델

```java
public enum PaymentStatus {
    PENDING,     // 결제 요청 접수 (PG에 요청 전송 완료)
    SUCCESS,     // 결제 성공 (PG 콜백 수신)
    FAILED,      // 결제 실패 (PG 콜백 수신 또는 타임아웃)
    CANCELLED    // 결제 취소
}
```

### 3. 주문 상태와의 연계

```
현재 OrderStatus: CREATED → CONFIRMED → SHIPPING → DELIVERED
                                                   → CANCELLED

결제 연동 후:
CREATED → PAYMENT_PENDING → CONFIRMED → SHIPPING → DELIVERED
                          → PAYMENT_FAILED → (재시도 가능)
                                           → CANCELLED
```

**판단 필요**: OrderStatus에 `PAYMENT_PENDING`, `PAYMENT_FAILED`를 추가할지, 기존 상태를 유지하고 Payment 상태로만 관리할지

---

## 예상 엔티티 설계

### PaymentModel

```java
@Entity
@Table(name = "payments")
public class PaymentModel extends BaseEntity {

    // BaseEntity의 id(Long, auto-increment) 사용

    @Column(nullable = false, unique = true)
    private String transactionKey;   // PG에서 받은 거래 키

    @Column(nullable = false)
    private Long orderId;            // 주문 ID (FK 대신 ID 참조)

    @Column(nullable = false)
    private Long userId;             // 결제 요청 사용자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType cardType;       // 카드사

    @Column(nullable = false)
    private String cardNo;           // 카드번호

    @Embedded
    private Money amount;            // 결제 금액 (기존 Money VO 재사용)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;    // 결제 상태

    private String failureReason;    // 실패 사유 (한도초과, 잘못된카드 등)
}
```

### 관계 설계

```
OrderModel (1) ←──── (N) PaymentModel
  - 하나의 주문에 여러 결제 시도 가능
  - PaymentModel은 orderId(Long)으로 참조 (JPA 연관관계 X)
  - 이유: Order와 Payment의 생명주기가 다름, 느슨한 결합 유지
```

**판단 필요**: `@ManyToOne` JPA 연관관계 vs 단순 ID 참조

---

## 패키지 구조

```
com.loopers/
├── domain/payment/
│   ├── PaymentModel.java          # 결제 엔티티
│   ├── PaymentStatus.java         # 결제 상태 Enum
│   ├── CardType.java              # 카드사 Enum
│   └── PaymentRepository.java     # Repository 인터페이스
├── application/payment/
│   ├── PaymentFacade.java         # 결제 유스케이스 조합
│   ├── PaymentService.java        # 결제 도메인 서비스
│   ├── PaymentInfo.java           # Application DTO
│   └── PaymentCommand.java        # 결제 요청 Command
├── infrastructure/payment/
│   ├── PaymentRepositoryImpl.java # Repository 구현체
│   └── PaymentJpaRepository.java  # JPA Repository
└── interfaces/api/payment/
    ├── PaymentV1Controller.java   # 결제 API
    ├── PaymentV1ApiSpec.java      # Swagger 인터페이스
    └── PaymentV1Dto.java          # Request/Response DTO
```

---

## API 설계

### 결제 요청

```http
POST /api/v1/payments
X-Loopers-LoginId: user1
X-Loopers-LoginPw: password

{
  "orderId": 1351039135,
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451"
}
```

**Response (결제 요청 접수)**
```json
{
  "meta": { "result": "SUCCESS" },
  "data": {
    "paymentId": 1,
    "transactionKey": "20250816:TR:9577c5",
    "orderId": 1351039135,
    "status": "PENDING",
    "amount": 5000
  }
}
```

### 결제 상태 조회

```http
GET /api/v1/payments/{paymentId}
```

### 주문별 결제 내역 조회

```http
GET /api/v1/payments?orderId={orderId}
```

---

## 트랜잭션 경계 설계

```
결제 요청 흐름:

1. [TX-1] 주문 조회 + 결제 레코드 생성 (status: PENDING)
   → 내부 DB 커밋

2. [TX 없음] PG 시스템 호출 (외부 HTTP)
   → transactionKey 수신

3. [TX-2] transactionKey 업데이트
   → 내부 DB 커밋

--- (비동기 대기) ---

4. [TX-3] PG 콜백 수신 → 결제 상태 업데이트 (SUCCESS/FAILED)
   → 주문 상태 연동
```

**핵심 원칙**: 외부 호출(PG)은 트랜잭션 밖에서 수행한다.
- 이유: PG 응답 지연(100ms~500ms) 동안 DB 커넥션 점유 방지
- 리스크: TX-1 커밋 후 PG 호출 실패 시 PENDING 상태 결제 잔존 → 복구 로직 필요

---

## 결정 필요 사항

| # | 항목 | 선택지 |
|---|------|--------|
| 1 | Order-Payment 관계 | JPA `@ManyToOne` vs 단순 ID 참조 |
| 2 | OrderStatus 확장 | PAYMENT_PENDING/FAILED 추가 vs 기존 유지 |
| 3 | 결제 금액 산출 | Order의 finalAmount 사용 vs Request에서 받기 |
| 4 | 카드번호 저장 | 전체 저장 vs 마스킹 저장 vs 저장하지 않음 |
