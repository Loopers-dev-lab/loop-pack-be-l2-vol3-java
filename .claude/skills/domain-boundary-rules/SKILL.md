---
name: domain-boundary-rules
description: "Domain Service 경계 가두기 규칙, Application에서의 Repository 호출 판단, 레이어 간 호출 규칙. 도메인 서비스와 Application Layer 간 호출 관계를 설계할 때 활성화한다."
---

Domain Service가 도메인 경계를 가두는 패턴과, Application Layer에서의 호출 규칙을 정의한다.
슬랙 QA 논쟁(12, 14, 15번)과 멘토 피드백으로 확정된 실전 규칙이다.

## 핵심 원칙

> "Repository Interface는 Domain에 놓고, Application에서 호출하되,
> Domain Service가 경계를 가둔 곳은 Domain Service를 통해서만 접근한다."

## 의존성 방향

```
Interfaces → Application → Domain ← Infrastructure
```

- 모든 화살표가 **Domain을 향한다** — Domain은 아무것에도 의존하지 않는다
- Application → Domain 방향이므로, Application에서 Repository(Domain Interface) 호출은 의존 방향 위반이 아니다

---

## "경계를 가둔다"란

특정 도메인 규칙이 반드시 지켜져야 할 때, Domain Service를 **관문(Gateway)**으로 두어
외부에서 직접 Repository를 호출하지 못하게 하는 것이다.

### 왜 가두는가?

```java
// 가두지 않은 경우: Application에서 직접 수정 → 도메인 규칙 우회 위험
inventoryRepository.save(inventory); // 검증 없이 저장!

// 가둔 경우: Domain Service를 통해서만 접근 → 규칙 반드시 거침
inventoryService.reserve(productId, qty); // validateAvailable() 검증 필수
```

---

## 의사결정 플로우차트

```
Q1: 해당 도메인에 Domain Service가 경계를 가두고 있는가?
  → YES: Application은 Domain Service만 호출 (Repository 직접 호출 X)
  → NO:  Application에서 Repository를 직접 호출해도 OK

Q2: Facade가 비대해졌는가? (메서드 50줄 이상, 여러 Aggregate 교차)
  → YES: Application Service를 도입하여 복잡한 상호작용을 분리
  → NO:  Facade만으로 충분
```

### 가둬야 하는 경우
- 도메인 규칙이 복잡하고 반드시 지켜져야 할 때
- 여러 도메인 객체가 협력하는 비즈니스 로직이 있을 때
- Repository 직접 호출 시 불변 조건이 깨질 위험이 있을 때
- 예: 재고 예약/확정/해제, 주문 생성, 결제 처리, 비밀번호 변경

### 가두지 않아도 되는 경우
- 단순 CRUD (생성/조회/수정/삭제)
- 도메인 규칙이 Entity 자체에 캡슐화되어 있고, 별도 협력 검증이 필요 없을 때

---

## 프로젝트 도메인별 경계 가두기 현황

| 도메인 | Domain Service가 경계를 가두는가? | 이유 | Application 호출 방식 |
|--------|-------------------------------|------|---------------------|
| User | **YES** — UserService | 중복검사, 암호화, 교차검증 | UserService 통해서만 |
| Brand | **YES** — BrandService | 상태관리, 삭제규칙 | BrandService 통해서만 |
| Product | **YES** — ProductService | 노출여부, 상태관리 | ProductService 통해서만 |
| Inventory | **YES** — InventoryService | reserve/commit/release, 비관적 락 | InventoryService 통해서만 |
| Like | **YES** — LikeService | 중복확인 + Product likeCount 변경 | LikeService 통해서만 |
| CartItem | **YES** — CartItemService | merge 규칙 | CartItemService 통해서만 |
| UserAddress | **YES** — UserAddressService | 기본주소 자동 전환 | UserAddressService 통해서만 |
| Order | **YES** — OrderService | 상태전이, 불변식 보호 | OrderService 통해서만 |

---

## 코드 예시

```java
// Domain Service가 경계를 가둔 경우
@Component
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    @Transactional
    public void reserve(Long productId, int qty) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId);
        inventory.reserve(qty);  // Entity가 도메인 규칙 검증 + 상태 변경
        inventoryRepository.save(inventory);
    }
}

// Application Layer — Domain Service를 통해서만 접근
@Component
public class OrderFacade {
    private final InventoryService inventoryService;  // Domain Service
    // private final InventoryRepository → 직접 사용하지 않음!

    @Transactional
    public OrderInfo createOrder(...) {
        inventoryService.reserve(productId, qty);  // Domain Service 통해서
        // inventoryRepository.save(...)           // 직접 호출 금지
    }
}
```

---

## 레이어 책임 프레임워크 (What / When / How)

| 레이어 | 질문 | 책임 |
|--------|------|------|
| **Domain** | "무엇을?" (What) | 도메인 모델, 정책, 규칙, 불변 조건을 **정의** |
| **Application** | "언제?" (When) | 도메인 객체들을 **언제, 어떤 순서로** 호출하는가 (orchestration) |
| **Infrastructure** | "어떻게?" (How) | Repository Interface를 **어떤 기술로** 구현하는가 |

이 프레임워크로 컴포넌트 위치를 판단한다:
- Repository Interface가 정의하는 것 = "무엇을 저장/조회하는가" = **Domain의 책임**
- Repository를 호출하는 것 = "언제 조회하고 저장하는가" = **Application의 책임**
- Repository 구현 = "어떤 기술로 저장하는가" = **Infrastructure의 책임**

---

## DIP — 목적과 수단

```
❌ "DIP를 지키기 위해 Interface를 Domain에 놓자"
  → DIP를 지키는 것이 목적이 되어버림

✅ "도메인을 보호하기 위해 Interface를 Domain에 놓고, DIP를 적용하자"
  → 도메인 보호가 목적, DIP는 수단
```

---

## 설계 체크리스트

- [ ] Domain Service가 경계를 가둔 도메인에서, Application이 Repository를 직접 호출하지 않는가?
- [ ] Application Layer의 Facade/Service가 비즈니스 규칙을 판단하지 않고 조율(orchestration)만 하는가?
- [ ] 의존 방향이 항상 Domain을 향하는가? (Domain → 다른 레이어 의존 없음)
- [ ] DIP를 "지키기 위해서"가 아닌 "도메인을 보호하기 위해서" 적용하고 있는가?