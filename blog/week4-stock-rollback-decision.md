> **TL;DR**: 조건부 UPDATE가 성능상 우위인 걸 알면서도 비관적 락으로 되돌렸다. 도메인 로직이 인프라 레이어로 유출되는 것을 "최적화"라고 부를 수는 없었기 때문이다.

---

## 커밋 두 개가 반대 방향을 가리킨다

```
feat: 재고를 조건부 UPDATE로 전환 (비관적 락 제거)
refactor: 재고를 비관적 락 + 도메인 엔티티 방식으로 복원
```

같은 PR 안에서, 같은 사람이, 같은 코드를 되돌렸다. 실수가 아니다. 의식적 결정이다.

---

## 두 가지 방법

재고 차감에는 두 가지 방법이 있다.

```java
// 방법 A: 비관적 락 + 도메인 엔티티
Product product = repo.findByIdWithLock(id);   // SELECT FOR UPDATE
product.decreaseStock(quantity);                // Stock.decrease() — 도메인 규칙
// dirty checking → UPDATE

// 방법 B: 조건부 UPDATE
int updated = repo.decreaseStock(id, quantity);
// → UPDATE product SET stock = stock - :qty WHERE stock >= :qty
if (updated == 0) throw new CoreException(...);
```

방법 B가 객관적으로 낫다. 락 보유 시간이 짧고, 엔티티를 메모리에 로딩할 필요도 없다. `read-modify-write` 패턴 자체를 제거하니 동시성 안전성도 구조적으로 더 강하다.

그래서 B로 바꿨다. 테스트도 통과했다. PR에 올렸다. 그리고 되돌렸다.

---

## 바꾸고 나서 불편했던 것

코드를 다시 읽었을 때, 불편한 게 하나 있었다.

```java
// 재고 차감 — 조건부 UPDATE
int updated = productRepository.decreaseStock(req.productId(), req.quantity());
if (updated == 0) throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
```

`Stock.decrease()`가 어디에도 호출되지 않는다. 도메인 객체에 "재고가 음수가 되면 안 된다"는 규칙이 있는데, 정작 주문 흐름에서는 그 규칙이 JPQL WHERE절로 옮겨갔다. `Stock` VO는 존재하지만, 주문에서는 사실상 죽은 코드다.

문제를 정리하면 이렇다.

| 관점 | 비관적 락 + 도메인 엔티티 | 조건부 UPDATE |
|------|--------------------------|--------------|
| 성능 | 락 대기 발생 | 단일 SQL, 락 최소화 |
| 도메인 캡슐화 | `Stock.decrease()` 호출 | `Stock.decrease()` 미사용 |
| 비즈니스 규칙 위치 | 도메인 객체 내부 | JPQL WHERE절 |
| 테스트 | Fake Repository로 단위 테스트 가능 | @SpringBootTest 필수 |

성능은 B가 낫다. 하지만 "재고 부족하면 예외"라는 비즈니스 규칙이 도메인 객체가 아니라 인프라스트럭처에 있게 된다. 그리고 이 시점에서 재고 차감의 성능 병목이 증명된 적은 없다.

증명된 병목 없이 도메인 로직을 인프라로 이동시키는 것을 "최적화"라고 부르기 어렵다. 그건 **과도한 최적화**다.

---

## 되돌린 이유

같은 PR에서 쿠폰 사용에도 조건부 UPDATE를 적용했다. 쿠폰은 되돌리지 않았다.

차이는 **도메인 로직의 무게**에 있다. 쿠폰의 상태 전이(`AVAILABLE → USED`)는 단순 플래그 변경이다. 반면 재고 차감은 `Stock` VO가 음수 방지, 수량 검증을 캡슐화하고 있다. 이 로직이 WHERE절로 흡수되면, 도메인 모델의 존재 의미가 희석된다.

결국 "모든 동시성 제어를 조건부 UPDATE로 통일"하는 대신, **도메인의 특성에 따라 전략을 분화**하는 방향을 택했다.

| 대상 | 전략 | 이유 |
|------|------|------|
| 재고 | 비관적 락 + 도메인 엔티티 | 도메인 규칙이 무거움 |
| 쿠폰 | 조건부 UPDATE | 상태 전이가 단순 |
| 좋아요 | UNIQUE + COUNT 파생 | 잠글 대상 자체를 제거 |

---

## 돌아보며

되돌리는 커밋을 만들면서 한 가지를 배웠다. 설계에서 "더 좋은 방법"은 단일 축으로 판단할 수 없다. 성능축에서는 조건부 UPDATE가 우세하지만, 캡슐화축에서는 비관적 락이 우세하다. 둘 다 틀리지 않았다.

다만, 성능 최적화는 병목이 증명된 후에 해도 늦지 않다. 도메인 캡슐화가 깨지면 코드를 읽는 모든 사람이 "재고 차감 규칙이 어디에 있지?"를 매번 추적해야 한다.

**더 빠른 방법을 아는 것과, 그걸 지금 적용하는 것은 다른 문제다.**
