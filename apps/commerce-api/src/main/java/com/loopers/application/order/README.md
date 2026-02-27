# Order Application Layer

> Claude Code 작업 시 이 영역의 설계 의도와 규칙을 참고하세요.

## 책임

- **OrderService**: 주문 생성. 트랜잭션 관리, OrderDomainService에 위임.

## 설계 규칙

1. **Service = 트랜잭션 + 위임**  
   `placeOrder()` → `orderDomainService.placeOrder()` 호출.  
   도메인 로직은 OrderDomainService, Product, Order에 위임.

2. **OrderResult 변환**  
   Order 엔티티 → OrderResult (orderId, status, totalAmount, orderLines) → API DTO.

## 주요 클래스

| 클래스 | 역할 |
|--------|------|
| OrderService | placeOrder() |
| OrderResult, OrderLineInfo | Application DTO |

## 참조

- [domain/order README](../../domain/order/README.md) — Order 도메인 규칙
- [CLAUDE.md](/CLAUDE.md) — 전체 아키텍처 규칙
