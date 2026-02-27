# Order 도메인

> Claude Code 작업 시 이 도메인의 설계 의도와 규칙을 참고하세요.

## 책임

- **Order**: 주문 엔티티. 여러 OrderLine 포함.
- **OrderLine**: 주문 항목 VO (productId, quantity, unitPrice 스냅샷).
- **OrderDomainService**: Order와 Product 간 재고 차감·주문 생성 조율.

## 설계 규칙

1. **주문 시 재고 차감**  
   `OrderDomainService.placeOrder()`에서 각 Product에 `decreaseStock()` 호출.  
   재고 부족 시 예외 → Order 미생성 (트랜잭션 롤백).

2. **가격 스냅샷**  
   OrderLine에 주문 시점 `unitPrice` 저장. 이후 Product 가격 변경과 무관.

3. **도메인 서비스**  
   Order, Product 간 협력은 `OrderDomainService`에서 처리.  
   Application Layer(OrderService)는 트랜잭션만 관리.

## 주요 클래스

| 클래스 | 역할 |
|--------|------|
| Order | 주문 엔티티, `Order.create()` 정적 팩토리 |
| OrderLine | 주문 항목 VO (불변) |
| OrderDomainService | placeOrder — 재고 차감 + Order 생성 |
| OrderRepository | 주문 저장/조회 인터페이스 |

## 주문 흐름

1. Product 조회 및 재고 검증
2. `product.decreaseStock(quantity)` — 재고 부족 시 예외
3. `Order.create(memberId, orderLines)` — Order + OrderLine 생성
4. `orderRepository.save(order)`

## 참조

- [CLAUDE.md](/CLAUDE.md) — 프로젝트 루트의 전체 아키텍처 규칙
