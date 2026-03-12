![](https://velog.velcdn.com/images/praesentia-ykm/post/b3979ec4-a67a-4276-8c32-cd22d8e3cbbb/image.png)
## 들어가며
[이전 글](https://velog.io/@praesentia-ykm)에서 33개의 Q&A로 설계를 먼저 한 이야기를 했다. 이번에는 그 설계 과정에서 가장 머리를 싸맸던 부분에 대해 써보려 한다.

**"이 코드를 어디에 둬야 하는가?"**

DDD를 공부하면 "바운디드 컨텍스트", "어그리게이트", "도메인 서비스" 같은 용어가 쏟아진다. 개념 자체는 어렵지 않았다. 문제는 **실제 코드에 적용하려 할 때** 발생했다.

> "ProductService에 있어야 해, 아니면 ProductFacade에 있어야 해?"
> "Stock이랑 Product를 한 테이블에 두면 안 돼?"
> "LikeFacade가 ProductService를 직접 부르는 게 맞아?"

이런 질문에 "상황에 따라 다릅니다"는 답이 되지 않는다. **어떤 상황에서 어떻게 달라지는지**, 그 갈림길의 기준을 찾고 싶었다.

따라서, 이번엔 DDD 설계 흐름을 10개의 키워드로 정리하고, 각 키워드마다 **"어떤 기준으로 개념을 분리하는가"**에 대한 나의 생각을 표현해보려고 한다.

---

## DDD 설계 흐름을 그려보자!

| # | 단계 | 키워드 | 핵심 질문 |
|---|------|--------|----------|
| 1 | 전략 | 서브도메인 식별 | 이 사업의 핵심은 무엇이고, 어디에 설계 역량을 집중할 것인가? |
| 2 | 전략 | 유비쿼터스 언어 | 이 단어가 이 맥락에서 뭘 의미하나? |
| 3 | 전략 | 바운디드 컨텍스트 | 같은 단어가 다른 의미를 갖는 경계는? |
| 4 | 전략 | 컨텍스트 매핑 | 나눈 컨텍스트들이 서로 어떻게 대화하는가? |
| 5 | 전술 | 어그리게이트 | 이 데이터를 단독으로 다룰 일이 있는가? |
| 6 | 전술 | 엔티티 vs 값 객체 | 이것이 고유 정체성을 갖는가, 속성의 묶음인가? |
| 7 | 전술 | 도메인 이벤트 | 경계를 넘는 통신은 어떻게 하는가? |
| 8 | 전술 | 도메인 서비스 vs 애플리케이션 서비스 | 이 코드가 비즈니스 의사결정을 내리는가? |
| 9 | 전술 | 리포지토리 경계 | 어그리게이트 루트 단위로만 존재하는가? |
| 10 | 아키텍처 | 레이어 구분 | 의존성 방향이 안쪽을 향하는가? |

![](https://velog.velcdn.com/images/praesentia-ykm/post/72623405-b0b5-484b-9ab8-5269dc0d3f63/image.png)

1~4는 **"무엇을 나눌 것인가"**(전략), 5~9는 **"나눈 것을 어떻게 구현할 것인가"**(전술), 10은 **"구현물을 어떻게 배치할 것인가"**(아키텍처)다.

한 가지 미리 짚어둘 게 있다. **나누기만 하면 부서진다.** 1~4에서 경계를 그으면, 5~9에서 그 경계 사이의 관계를 정의해야 한다. 나누기와 연결하기는 항상 쌍이다.

---

## 전략적 설계 — 무엇을 나눌 것인가

### 1. 서브도메인 식별 — "이 중에 뭐가 제일 중요한가?"

**키워드: Core / Supporting / Generic**

서브도메인 식별은 "사업에서 어떤 영역이 있는가"를 나누고, **"어디에 설계 역량을 집중할 것인가"**를 결정하는 단계다.

| 유형 | 의미 | 설계 전략 |
|------|------|-----------|
| **Core** | 비즈니스 경쟁력의 핵심 | 직접 설계하고 정교하게 구현 |
| **Supporting** | Core를 보조. 중요하지만 차별화 요소는 아님 | 직접 구현하되 Core만큼의 투자는 불필요 |
| **Generic** | 어디서나 비슷하게 필요한 범용 기능 | 외부 솔루션 사용 가능 |

이커머스에서 이걸 적용하면:

| 서브도메인 | 유형 | 판단 근거 |
|-----------|------|----------|
| **카탈로그** (상품 + 브랜드) | Core | 고객에게 보여줄 상품을 관리. 비즈니스 전시의 핵심 |
| **주문** | Core | 거래를 기록하고 관리. 매출의 직접적 근간 |
| **재고** | Supporting | 주문과 카탈로그를 보조. 중요하지만 독자적 경쟁력은 아님 |
| **좋아요** | Supporting | 고객 선호 추적. 카탈로그 정렬(인기순)에 활용 |
| **회원/인증** | Generic | 어디서나 비슷한 범용 기능. 외부 솔루션 대체 가능 |
![](https://velog.velcdn.com/images/praesentia-ykm/post/97df448e-cc08-4ad8-a4eb-b4fdf5b87a0f/image.png)


근데 왜 재고가 Supporting이지?

처음엔 이렇게 생각했다. 재고는 **"반응하는 도메인"**이다. 주문이 들어오면 차감되고, 상품이 등록되면 생성된다. 스스로 뭔가를 일으키기보다, 다른 도메인의 상태 변경에 영향을 받는 자식도메인 같은 느낌이었다.

근데 이 기준만으로는 부족했다. 주문도 "고객이 구매 버튼을 누르면" 반응하는 도메인인데, Core잖아. "반응한다"는 것만으로 Supporting을 판별할 수 없었다.

차이는 여기서 갈렸다: **"이 도메인이 없어도 사업이 성립하는가?"**

- 주문이 없으면? 이커머스가 아니다. 물건을 팔 수 없다.
- 재고가 없으면? 판매는 된다. 다만 관리가 허술해질 뿐이다. 실제로 소규모 쇼핑몰은 재고 관리 없이도 돌아간다.

**"반응만 하는 도메인 + 없어도 사업이 돌아가면 = Supporting."** 없으면 사업 자체가 불가능하면 아무리 반응형이어도 Core다.

이 분류가 코드에 주는 영향은 명확하다. 카탈로그와 주문은 도메인 모델을 정교하게 설계하고, 회원/인증은 `userId`만 받아서 참조한다. 실제로 `MemberFacade`는 단순한 CRUD뿐이고, `OrderFacade`는 재고 차감, 스냅샷 생성, All or Nothing 검증까지 복잡한 규칙이 들어있다.

---

### 2. 유비쿼터스 언어 — "이 단어가 이 맥락에서 뭘 의미하나?"

**키워드: 같은 단어, 다른 의미**

"상품이 뭔데?"라고 물었을 때, 대답이 달라지는 지점이 있다.

현재 `ProductModel`은 이렇게 생겼다.

```java
public class ProductModel extends BaseEntity {
    private String name;        // "상품을 전시한다" 관점
    private String description; // "상품을 전시한다" 관점
    private Money price;        // "상품의 가치를 매긴다" 관점
    private Long brandId;       // "상품이 어떤 브랜드인지" 관점
    private int likeCount;      // "상품이 얼마나 인기있는지" 관점
}
```

하나의 클래스에 다섯 가지 관심사가 공존한다. "상품이 뭔데?"라고 물으면 맥락마다 답이 완전히 다르다.

| 맥락 | "상품"의 의미 | 관심 있는 속성 | 관심 없는 속성 |
|------|-------------|---------------|---------------|
| **카탈로그** | 고객에게 보여줄 전시물 | name, description, price, brand | quantity, likeCount |
| **재고** | 창고에서 관리할 물건 | productId, quantity, status | name, description, brand |
| **좋아요** | 사용자가 선호를 표현한 대상 | productId (참조만) | name, price, quantity |
| **주문** | 거래의 대상 (가격이 확정된 시점) | productId, 주문시점가격, 수량 | 현재가격, 재고, 좋아요 |

같은 "상품"인데 **필요한 속성이 완전히 다르다.** 이 차이를 인식하는 것 자체가 유비쿼터스 언어를 정의하는 과정이다.

그리고 이 과정은 다음 단계인 바운디드 컨텍스트와 **동시에** 일어난다. "상품"이라는 단어의 의미가 달라지는 지점을 발견하는 순간이 곧 경계를 긋는 순간이다.

비유를 들자면, 지도에서 국경선을 긋는 것과 각 나라의 공용어를 정하는 것이다. 언어를 먼저 정하고 국경을 그리는 게 아니라, **언어 차이가 국경을 드러낸다.**

---

### 3. 바운디드 컨텍스트 — "같은 단어가 다른 의미를 갖는 경계는?"

**키워드: 경계 긋기**

유비쿼터스 언어에서 의미가 갈라지는 지점이 바운디드 컨텍스트의 경계다.

> **판별 기준:** 같은 단어가 다른 속성/행위를 요구하는 지점 = 바운디드 컨텍스트 경계

이 기준은 **동일 도메인 용어가 여러 맥락에서 사용될 때만** 적용된다. 애초에 다른 단어를 쓰는 영역(예: "상품"과 "결제수단")은 비즈니스 관심사 자체가 다르므로 별도 컨텍스트다.

재밌는 건 무의식적으로 이미 이 경계를 지키고 있었다는 것이다.

- `LikeModel`은 `ProductModel`을 직접 참조하지 않고 `productId`만 보유한다.
- `StockModel`도 `productId`만 보유한다.
- `OrderItemModel`에는 주문 시점의 `productName`, `productPrice`를 **스냅샷**으로 복사한다.

각 도메인은 "상품" 전체를 알 필요 없이 자기에게 필요한 단편만 들고 있다. 이것이 바운디드 컨텍스트 간의 **느슨한 참조(ID 참조)**다.

![](https://velog.velcdn.com/images/praesentia-ykm/post/6a57db0a-1182-4a68-bc92-c64b8ab6fde2/image.png)

근데 "의미가 다르면 나눈다"로만 끝나지 않았다. **"이 둘이 하나의 트랜잭션으로 묶여야 하는가?"**도 경계 판단에 영향을 줬다.

Brand와 Product가 그 예시다. 직감적으로는 분리하고 싶었다. 브랜드는 브랜드고 상품은 상품이니까. 근데 Q&A 과정에서 이런 질문을 던졌었다.

> Q1: 브랜드를 삭제하면 소속 상품은 어떻게 되는가?

답은 "브랜드 삭제 시 소속 상품 전체를 연쇄 soft delete"이고, 이것은 **하나의 트랜잭션**으로 처리되어야 한다.

```java
@Transactional
public void deleteBrand(Long brandId) {
    brandService.delete(brandId);
    productService.softDeleteByBrandId(brandId);  // 연쇄 삭제 — 같은 트랜잭션
}
```

만약 Brand와 Product가 다른 바운디드 컨텍스트에 있다면 이 트랜잭션은 **분산 트랜잭션**이 된다. "브랜드 삭제"라는 단순한 요구사항에 Saga 패턴 같은 복잡도를 도입하는 건 과하다.

**경계 판단 기준 정리:**
1. 같은 단어가 다른 속성/행위를 요구하면 → 다른 BC
2. 하나의 트랜잭션으로 묶여야 하면 → 같은 BC

![](https://velog.velcdn.com/images/praesentia-ykm/post/f0268056-f930-42c8-b0e7-d8bf89309f5d/image.png)

---

### 4. 컨텍스트 매핑 — "나눈 것들이 어떻게 대화하는가?"

**키워드: 관계 정의**

바운디드 컨텍스트를 나눠 놓고 끝이 아니다. **나눈 것들 사이의 통신 방식을 정해야 한다.** 이걸 빼먹으면 "잘 나눈 것 같은데 결국 다 얽혀있네?"라는 상황이 된다.

현재 프로젝트의 의존 관계:

![](https://velog.velcdn.com/images/praesentia-ykm/post/d8132a29-13a3-415e-b604-2a50c5532bf8/image.png)

모놀리스에서는 Facade가 다른 도메인의 Service를 직접 호출한다. 여기서 중요한 건 **"지금은 직접 호출하되, 시스템이 커졌을 때 어디서 잘라야 하는가"를 아는 것**이다.

| 호출 | 방식 | 시스템 분리 시 전환 |
|------|------|-------------------|
| `ProductFacade` → `BrandService` | 직접 호출 | 같은 BC — 분리 불필요 |
| `ProductFacade` → `StockService` | 직접 호출 | API 호출 또는 이벤트 |
| `LikeFacade` → `ProductService` | 직접 호출 | **도메인 이벤트** |
| `OrderFacade` → `ProductService` + `StockService` | 직접 호출 | Saga 패턴 |

특히 `LikeFacade`가 카탈로그 BC의 엔티티를 직접 수정하는 부분을 보면:

```java
// LikeFacade — 좋아요 BC가 카탈로그 BC의 엔티티를 직접 수정
public void like(Long userId, Long productId) {
    ProductModel product = productService.getProduct(productId);
    // ... 좋아요 로직
    product.incrementLikeCount();  // ← 다른 BC의 엔티티를 직접 변경
}
```

모놀리스에서는 이게 실용적이다. 하지만 이 코드가 **BC 경계를 넘는 직접 수정**이라는 사실은 인식하고 있어야 한다. 시스템이 커져서 물리적으로 분리할 때, 이 부분은 도메인 이벤트로 전환된다.

> 좋아요 발생 → `LikeCreatedEvent` 발행 → 카탈로그 BC가 수신 → `likeCount` 갱신

"지금은 직접 호출하되, 여기가 나중에 잘라야 할 지점"이라는 걸 아는 것과 모르는 것은 다르다. 컨텍스트 매핑의 가치가 여기에 있다.

---

## 전술적 설계 — 나눈 것을 어떻게 구현할 것인가

### 5. 어그리게이트 — "단독으로 접근할 일이 있는가?"

**키워드: 접근성과 잠금**

같은 바운디드 컨텍스트 안에서도 "어디까지를 하나의 단위로 묶을 것인가"를 결정해야 한다. 이게 어그리게이트 경계다.

Product와 Stock은 1:1 관계인데, 깊은 관계니까 하나로 합쳐야 하지 않을까? 처음엔 그렇게 생각했다. 근데 직감적으로 **"재고를 보기 위해 매번 상품을 거쳐야 한다면?"**이 걸렸다. 재고 차감은 상품 정보가 필요 없는데, 상품을 통해서만 접근해야 하면 비효율적이지 않은가.

이 직감을 기준으로 정리하면:

| 케이스 | "단독으로 접근할 일 있나?" | 결론 |
|--------|:---:|------|
| 재고 ← 상품 | 있다 (재고만 차감) | 분리 |
| 상품 ← 브랜드 | 있다 (상품만 조회) | 분리 |
| 주문항목 ← 주문 | 없다 (항상 주문 통해) | 합침 |

이 기준은 DDD에서 흔히 쓰는 **"같이 잠글 필요가 있는가?"**와 결국 같은 얘기다.

| 변경 시나리오 | Product 변경? | Stock 변경? | 결론 |
|-------------|:----------:|:----------:|------|
| 상품명 수정 | O | X | 독립 |
| 가격 수정 | O | X | 독립 |
| 재고 차감 (주문) | X | O | 독립 |
| 상품 등록 (초기 재고 포함) | O | O | Facade에서 조율 |

![](https://velog.velcdn.com/images/praesentia-ykm/post/d113df10-a09f-4601-aa25-ee039d93a65c/image.png)


단독으로 접근이 필요하다는 건 곧 독립적으로 변경된다는 뜻이고, 독립적으로 변경되면 같이 잠글 필요가 없다. **접근성에서 출발해도 잠금에서 출발해도 같은 결론에 도달한다.**

유일하게 둘 다 변경되는 "상품 등록"은 **비즈니스 규칙이 아니라 절차**다. "상품을 등록하면서 초기 재고도 만든다"는 순서의 문제이지, 둘이 반드시 원자적으로 잠겨야 하는 건 아니다. Facade에서 조율하면 된다.

```java
@Transactional
public ProductModel register(..., Long brandId, int initialStock) {
    brandService.getBrand(brandId);                          // 1. 브랜드 존재 확인
    ProductModel product = productService.register(...);     // 2. 상품 생성
    stockService.create(product.getId(), initialStock);      // 3. 재고 생성
    return product;
}
```

> **단독으로 접근할 일이 있으면 별도 어그리게이트. 동시 변경이 필요한 경우는 Facade에서 조율.**

---

### 6. 엔티티 vs 값 객체 — "이것이 고유 정체성을 갖는가?"

**키워드: 정체성 유무**

어그리게이트 안의 객체는 엔티티(Entity)와 값 객체(Value Object)로 나뉜다. 판별 기준은 하나다.

> **고유한 식별자(id)가 필요한가? → 엔티티. 속성 값이 같으면 같은 것인가? → 값 객체.**

"어떤 5000원"인지가 중요하면 엔티티다. "5000원이면 다 같은 5000원"이면 값 객체다.

```java
// 값 객체 — 5000원이면 다 같은 5000원
@Embeddable
public class Money {
    public static final Money ZERO = new Money(0);

    @Column(name = "price", nullable = false)
    private int value;

    public Money(int value) {
        if (value < 0) throw new CoreException(ErrorType.BAD_REQUEST, "가격은 음수일 수 없습니다.");
        this.value = value;
    }

    public Money add(Money other) { return new Money(this.value + other.value); }
    public Money multiply(int multiplier) { return new Money(this.value * multiplier); }
}
```

```java
// 값 객체 — 같은 이름이면 같은 브랜드명
@Embeddable
public class BrandName {
    @Column(name = "name", nullable = false, unique = true)
    private String value;

    public BrandName(String value) {
        if (value == null || value.isBlank())
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        this.value = value;
    }
}
```

값 객체의 특징 세 가지:
1. **불변(Immutable):** `add()`는 기존 객체를 바꾸지 않고 새 객체를 반환한다
2. **자기 검증:** 생성 시점에 유효성을 강제한다 (음수 불가, 빈값 불가)
3. **행위 포함 가능:** `Money.multiply()`처럼 해당 값과 관련된 연산을 가질 수 있다

현재 프로젝트의 분류:

| 객체 | 타입 | 근거 |
|------|------|------|
| `ProductModel` | 엔티티 | 고유 id로 식별. "상품 A"와 "상품 B"는 속성이 같아도 다른 상품 |
| `Money` | 값 객체 | 5000원은 어떤 5000원이든 같은 5000원 |
| `BrandName` | 값 객체 | "나이키"라는 이름은 그 자체로 동일한 의미 |
| `MemberName` | 값 객체 | 이름 값 자체가 의미. `masked()` 행위 메서드 보유 |
| `LoginId` | 값 객체 | 영문+숫자 패턴 검증을 생성 시 강제 |

---

### 7. 도메인 이벤트 — "경계를 넘는 통신을 어떻게 하는가?"

**키워드: 비동기 통신 / 결합도 제거**

4번(컨텍스트 매핑)에서 "나눈 것들이 어떻게 대화하는가"를 정했다면, 도메인 이벤트는 그 대화의 **구현 수단** 중 하나다.

현재 프로젝트에는 도메인 이벤트가 **하나도 없다.** 모든 컨텍스트 간 통신은 Facade에서 직접 호출한다.

```java
// 현재: LikeFacade가 ProductService를 직접 호출
product.incrementLikeCount();

// 도메인 이벤트 도입 시:
// 좋아요 생성 → LikeCreatedEvent 발행 → 카탈로그 BC가 수신 → likeCount 갱신
```

왜 지금 도입하지 않았을까? 트레이드오프 판단이었다.

| 기준 | 직접 호출 | 도메인 이벤트 |
|------|----------|-------------|
| 구현 복잡도 | 낮음 | 높음 (이벤트 발행/구독 인프라 필요) |
| 정합성 | 즉시 정합성 | 최종 정합성 (eventual consistency) |
| 결합도 | 높음 (다른 BC 서비스 직접 참조) | 낮음 (이벤트만 알면 됨) |
| 적합한 규모 | 모놀리스 | 마이크로서비스 |

![](https://velog.velcdn.com/images/praesentia-ykm/post/140597e1-5b12-404b-97b1-9bb7ee0e5cde/image.png)

모놀리스에서 도메인 이벤트를 도입하면 "아직 필요 없는 복잡도"가 된다. 다만 **어디가 이벤트로 전환될 지점인지**는 컨텍스트 매핑(4번)에서 이미 식별해뒀다. 필요해지는 시점에 전환하면 된다.

---

### 8. 도메인 서비스 vs 애플리케이션 서비스 — "이 코드가 비즈니스 의사결정을 내리는가?"

**키워드: 의사결정 vs 조율**

"이 로직을 `domain/`에 둘까, `application/`에 둘까?" — 설계하면서 가장 오래 고민한 질문이다.

처음에 세운 기준은 이거였다.

> **규칙은 영업부에 설명해도 알아들을 수 있는 것. 절차는 개발자들만 고민하고 구성해야 하는, 사용자 요청에 대한 시나리오.**

"같은 이름의 브랜드는 등록할 수 없다" — 영업부도 안다. 규칙이다. → `BrandService`
"브랜드 확인하고, 상품 만들고, 재고 만들어라" — 영업부는 모른다. 개발자가 짠 순서다. 절차다. → `ProductFacade`

여기까지는 잘 작동했다. 그리고 `BrandService`는 `domain/` 패키지에 넣었다. "규칙을 담으니까 도메인 서비스"라고 생각했다.

**근데 이 판단이 틀렸다.**

리팩토링 과정에서 더 날카로운 기준을 만났다.

> **"이 코드가 비즈니스 의사결정을 내리는가?"**

이 질문을 `BrandService.register()`에 대입해봤다.

```java
public BrandModel register(String name, String description) {
    BrandName brandName = new BrandName(name);  // ← VO가 이름 유효성 검증 (의사결정)
    brandRepository.findByName(name).ifPresent(existing -> {
        throw new CoreException(ErrorType.CONFLICT);  // ← DB 상태 확인 후 거부 (조율)
    });
    return brandRepository.save(new BrandModel(brandName, description));
}
```

**"브랜드 이름이 비어있으면 안 된다"** — 이건 `BrandName` VO가 생성 시점에 스스로 결정한다. 의사결정이다.
**"중복 이름이 있는지 DB에서 확인한다"** — 이건 `BrandService`가 Repository를 호출해서 상태를 확인하는 것이다. 조율이다.

핵심은 이거였다. `BrandService`는 **의사결정을 내리는 게 아니라, 엔티티/VO가 내린 의사결정이 실행될 수 있도록 정보를 준비하고 조율하는 것**이다. 3단계로 보면:

1. **정보 준비** (application) — Repository에서 데이터 조회
2. **비즈니스 의사결정** (domain) — 엔티티/VO가 규칙을 적용
3. **결과 적용** (application) — 저장, 이벤트 발행 등

`BrandService`의 "유니크 검증"도 결국 1→3이다. DB 상태를 확인(정보 준비)하고 → 충돌이면 거부(결과 적용). **비즈니스 의사결정 자체는 `BrandName` VO가 이미 담당하고 있다.**

이 기준으로 현재 프로젝트의 모든 Service를 점검했다.

| Service | 하는 일 | 의사결정을 내리는가? | 결론 |
|---------|--------|:---:|------|
| `BrandService` | 이름 유니크 체크 + CRUD | No (VO가 유효성 검증) | **애플리케이션 서비스** |
| `ProductService` | 상품 CRUD | No (Money VO가 가격 검증) | **애플리케이션 서비스** |
| `StockService` | 재고 생성/차감 | No (StockModel.decrease()가 판단) | **애플리케이션 서비스** |
| `LikeService` | 좋아요 등록/취소 | No (멱등성은 DB 상태 확인) | **애플리케이션 서비스** |
| `OrderService` | 주문/주문상품 CRUD | No (OrderModel.validateOwner()가 판단) | **애플리케이션 서비스** |

**도메인 서비스가 하나도 없었다.** 모든 비즈니스 의사결정은 엔티티와 VO가 내리고 있었고, Service는 그 결정이 실행되도록 조율하는 역할이었다. 그래서 전부 `domain/` → `application/`으로 이동시켰다.

![](https://velog.velcdn.com/images/praesentia-ykm/post/adf659f8-c0a3-4586-99b5-fea7c266d28e/image.png)

그럼 `application/` 안에서 **Service와 Facade는 어떻게 구분하는가?**

| 구분 | Service | Facade |
|------|---------|--------|
| **역할** | 단일 도메인 CRUD 조율 | 여러 도메인 Service를 조합 |
| **의존** | Repository (하나) | Service (여러 개) |
| **예시** | `BrandService` (브랜드 CRUD) | `ProductFacade` (브랜드 확인 + 상품 생성 + 재고 생성) |

코드로 보면:

**엔티티 메서드 — 비즈니스 의사결정을 내린다:**

```java
// "재고가 부족하면 차감할 수 없다" — StockModel이 스스로 판단
public void decrease(int amount) {
    if (this.quantity < amount)
        throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
    this.quantity -= amount;
}
```

**Service (애플리케이션) — 단일 도메인 CRUD를 조율한다:**

```java
// application/brand/BrandService: Repository를 호출하여 CRUD 수행
// 의사결정은 BrandName VO가 내리고, Service는 DB 상태 확인 + 저장을 조율
public BrandModel register(String name, String description) {
    BrandName brandName = new BrandName(name);  // VO가 이름 유효성 검증
    brandRepository.findByName(name).ifPresent(existing -> {
        throw new CoreException(ErrorType.CONFLICT);  // DB 상태 확인 후 거부
    });
    return brandRepository.save(new BrandModel(brandName, description));
}
```

**Facade (애플리케이션) — 여러 도메인 Service를 조합한다:**

```java
// application/product/ProductFacade: 여러 Service를 조합하여 유스케이스 실행
public ProductModel register(..., Long brandId, int initialStock) {
    brandService.getBrand(brandId);                          // 1. 브랜드 존재 확인 (위임)
    ProductModel product = productService.register(...);     // 2. 상품 생성 (위임)
    stockService.create(product.getId(), initialStock);      // 3. 재고 생성 (위임)
    return product;
}
```

**Facade에 규칙이 있을 수도 있다:**

```java
// "브랜드 삭제 시 소속 상품도 삭제" — 영업부도 아는 규칙이지만, 두 도메인에 걸침
@Transactional
public void deleteBrand(Long brandId) {
    brandService.delete(brandId);
    productService.softDeleteByBrandId(brandId);  // ← 규칙이지만, 여러 도메인이라 Facade
}
```

이전에 "규칙이면 도메인 서비스"라고 단순하게 분류했던 것이 틀렸다. **핵심 기준은 "규칙인가 절차인가"가 아니라 "비즈니스 의사결정을 내리는가"다.** 대부분의 의사결정은 엔티티/VO 안에 캡슐화되어 있지만, 하나 발견했다.

**LikeFacade에 숨어있던 도메인 서비스:**

좋아요 멱등성 로직은 `LikeModel`과 `ProductModel` 두 엔티티의 상태를 종합해서 판단한다. "좋아요가 없으면 생성, 삭제됐으면 복구, 이미 있으면 무시" — 이 판단을 어느 한쪽 엔티티에 넣을 수 없다. `LikeModel`은 `ProductModel`을 모르고, `ProductModel`은 `LikeModel`을 모른다.

이 로직을 지우면 **"멱등성 있는 좋아요"라는 비즈니스 규칙 자체가 사라진다.** 조율이 아니라 의사결정이다. 그래서 도메인 서비스로 분리했다.

```java
// domain/like/LikeToggleService — 도메인 서비스
// Like + Product 두 엔티티의 상태를 종합하여 판단. 인프라 의존 없음.
public Optional<LikeModel> like(Optional<LikeModel> existing, ProductModel product,
                                 Long userId, Long productId) {
    if (existing.isEmpty()) {                          // 판단: 신규 → 생성 + 카운트 증가
        product.incrementLikeCount();
        return Optional.of(new LikeModel(userId, productId));
    }
    if (existing.get().getDeletedAt() != null) {       // 판단: 삭제됨 → 복구 + 카운트 증가
        existing.get().restore();
        product.incrementLikeCount();
    }
    // else: 이미 활성 → 멱등 무시
    return Optional.empty();
}
```

```java
// application/like/LikeFacade — 애플리케이션 서비스 (조율만)
// 데이터 조회 → 도메인 서비스에 판단 위임 → 결과 저장
public void like(Long userId, Long productId) {
    ProductModel product = productService.getProduct(productId);
    Optional<LikeModel> existing = likeService.findByUserIdAndProductId(userId, productId);

    Optional<LikeModel> newLike = likeToggleService.like(existing, product, userId, productId);
    newLike.ifPresent(likeService::save);
}
```

**도메인 서비스가 필요한 조건:** 두 개 이상의 어그리게이트에 걸친 비즈니스 의사결정을 내려야 하는데, 어느 한쪽 엔티티에 넣기 어려운 경우다. `LikeToggleService`가 정확히 이 케이스다.

**범위 한정:** 이 "의사결정" 기준은 **도메인 계층과 애플리케이션 계층 사이에서만** 유효하다. Controller(HTTP 변환)나 Repository(데이터 접근)에는 적용하지 않는다.

---

### 9. 리포지토리 경계 — "어그리게이트 루트 단위로만 존재하는가?"

**키워드: 어그리게이트 루트 = 리포지토리 단위**

DDD의 원칙: **리포지토리는 어그리게이트 루트 하나당 하나.** 어그리게이트 내부의 엔티티는 루트를 통해서만 접근한다.

근데 이 원칙을 의도적으로 깬 곳이 있다.

```java
// OrderService — OrderItemRepository가 별도로 존재
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;  // ← 원칙대로라면 없어야 함
}
```

DDD 정석으로는 `OrderItem`은 `Order` 어그리게이트의 내부 엔티티이므로, `OrderRepository`를 통해서만 접근해야 한다. 그러려면 JPA 연관관계(`@OneToMany`)가 필요하다. 근데 이 프로젝트는 ID 참조만 쓴다.

왜 JPA 연관관계 대신 ID 참조를 택했을까? 두 방식의 차이를 보면:

| 기준 | ID 참조 | JPA 연관관계 (`@OneToMany`) |
|------|---------|---------------------------|
| 조회 | `findByOrderId(id)` — 명시적 | `order.getItems()` — 암시적 lazy loading |
| N+1 문제 | 없음 | 있음 (fetch join 필요) |
| 양방향 동기화 | 불필요 | 필수 (`item.setOrder(this)` 빠뜨리면 버그) |
| 삭제 | 명시적 delete 호출 | `orphanRemoval`이면 리스트에서 빼는 것만으로 삭제 |
| 테스트 | ID만 넣으면 됨 | 전체 객체 그래프 구성 필요 |

JPA 연관관계의 복잡도는 **실제로 코드에서 터지는 문제들**이다. N+1은 성능 이슈를, 양방향 동기화 누락은 버그를, 암시적 삭제는 데이터 유실을 만든다.

반대로 DDD 리포지토리 원칙을 깨면 뭐가 터질까? "누군가 `OrderItemRepository`를 직접 호출할 수 있다"는 위험이 생긴다. 하지만 현재는 `OrderService`가 두 Repository를 모두 들고 있고, `OrderItem` 접근은 항상 `OrderService`를 통한다. **서비스 레이어가 접근을 통제하고 있으므로 실질적 위험은 낮다.**

원칙을 깨도 되는지 판단할 때 세 가지를 물었다:

1. **깨면 실제로 터지는가?** — JPA 연관관계는 실제 버그를 만든다 → 피한다
2. **다른 수단으로 보호 가능한가?** — 서비스 레이어가 접근 통제 중 → 보호됨
3. **그 보호를 조직이 유지할 수 있는가?** — 현재 규모에서 감당 가능 → 깨도 된다

이 판단은 영원히 유효하진 않다. 팀이 커지면 3번 조건이 깨질 수 있고, 그때는 다른 보호 수단을 마련해야 한다.

---

## 아키텍처 — 구현물을 어떻게 배치할 것인가

### 10. 레이어 구분 — "의존성 방향이 안쪽을 향하는가?"

**키워드: 의존성 방향**

전략적/전술적 설계가 끝난 후 코드로 옮기는 단계다. 각 레이어의 역할과 의존 방향을 정한다.

![](https://velog.velcdn.com/images/praesentia-ykm/post/4016c78a-3e3a-4136-a03f-c7b22602d70a/image.png)


핵심 규칙: **의존성은 항상 안쪽(domain)을 향한다.**

- Controller는 Facade/Service(application)를 알지만, application은 Controller를 모른다
- Controller는 domain을 직접 참조하지 않는다. Dto도 application DTO(Info/Result record)를 경유한다
- Facade는 여러 Service를 알지만, Service는 Facade를 모른다
- Service는 Repository Interface를 알지만, JPA 구현체를 모른다

Repository Interface가 `domain/` 패키지에 있는 이유가 여기에 있다. Domain Layer는 "데이터를 저장하고 조회하는 능력"이 필요하지만, "그것이 JPA로 구현되는지 MyBatis로 구현되는지"는 알 필요가 없다. Interface를 domain에 두고 구현체를 infrastructure에 두면, domain은 기술 선택에 의존하지 않는다.

---

## 최종 설계

10가지 키워드를 적용한 결과물이다.

### 도메인 — 비즈니스 의사결정

| 컴포넌트 | 담당하는 의사결정 |
|---------|---------|
| `BrandName` (VO) | 브랜드 이름 유효성 (비어있으면 안 됨) |
| `Money` (VO) | 가격 유효성 (음수 불가), 연산 (`add`, `multiply`) |
| `LoginId` (VO) | 로그인 ID 형식 검증 (영문+숫자 패턴) |
| `MemberName` (VO) | 이름 유효성, 마스킹 (`masked()`) |
| `StockModel` (Entity) | 재고 충분 여부 판단, 차감/증가 (`decrease`, `increase`) |
| `OrderModel` (Entity) | 주문 소유권 검증 (`validateOwner`), 상태 전이 |
| `ProductModel` (Entity) | likeCount 증감, 삭제 상태 검증 |
| `LikeToggleService` (도메인 서비스) | Like + Product 두 엔티티의 상태를 종합하여 좋아요 반응 결정 (신규/복구/멱등 무시) |

대부분의 비즈니스 의사결정은 엔티티와 VO 안에 캡슐화되어 있다. 하지만 **좋아요 멱등성 판단**은 `LikeModel`과 `ProductModel` 두 엔티티의 상태를 종합해야 하므로 어느 한쪽 엔티티에 넣을 수 없다. 이것이 `LikeToggleService` 도메인 서비스가 필요한 이유다.

### 애플리케이션 — 조율 (Service + Facade)

**Service — 단일 도메인 CRUD 조율:**

| 컴포넌트 | 조율 내용 |
|---------|---------|
| `BrandService` | 브랜드명 유니크 체크 + CRUD (이름 유효성은 `BrandName` VO가 판단) |
| `ProductService` | 상품 CRUD, likeCount 증감, soft delete |
| `StockService` | 재고 생성, 차감 (부족 여부는 `StockModel`이 판단) |
| `LikeService` | 좋아요 저장/조회 (멱등성 판단은 `LikeToggleService` 도메인 서비스가 담당) |
| `OrderService` | 주문/주문상품 CRUD (소유권 검증은 `OrderModel`이 판단) |
| `MemberSignupService` | 회원가입 (ID 중복 체크 + 생성, 형식 검증은 `LoginId` VO가 판단) |
| `MemberAuthService` | 인증 (비밀번호 검증은 엔티티에 위임) |
| `MemberPasswordService` | 비밀번호 변경 (검증은 엔티티/VO에 위임) |

**Facade — 여러 도메인 Service를 조합:**

| 컴포넌트 | 조율 내용 | 성격 |
|---------|---------|------|
| `BrandFacade` | 브랜드 삭제 → 소속 상품 연쇄 soft delete | 규칙 (여러 도메인에 걸침) |
| `ProductFacade` | 브랜드 존재 확인 → 상품 생성 → 재고 생성 | 절차 |
| `LikeFacade` | 데이터 조회 → `LikeToggleService`에 판단 위임 → 결과 저장 | 절차 |
| `OrderFacade` | 상품 조회 → 삭제 검증 → 재고 차감 → 주문 생성 (All or Nothing) | 규칙 (여러 도메인에 걸침) |
| `MemberFacade` | 회원 유스케이스 조율 (가입, 인증, 비밀번호 변경) | 절차 |

Service와 Facade 모두 `application/` 레이어에 있다. 차이는 **의존 범위**다. Service는 자기 도메인의 Repository 하나만 의존하고, Facade는 여러 Service를 조합한다. 어느 쪽이든 비즈니스 의사결정 자체는 엔티티/VO가 내리고, application 레이어는 그 결정이 실행되도록 정보를 준비하고 결과를 적용한다.

---

## 회고: 설계엔 정답은 없지만 오답은 있다.

10개의 키워드는 결국 두 가지 행위의 반복이었다: **나누기**와 **연결하기**.

전략적 설계(1~4)에서 비즈니스 영역을 나누고 그 관계를 정의한다. 전술적 설계(5~9)에서 코드 단위를 나누고 그 통신 방식을 정의한다. 아키텍처(10)에서 배치를 나누고 의존 방향을 정의한다.

어느 단계에서든 "나누기만 하고 연결하기를 빼먹으면" 시스템이 부서진다. 바운디드 컨텍스트를 나누고 컨텍스트 매핑을 안 하면 "잘 나눈 것 같은데 결국 다 얽혀있네?"가 된다. 어그리게이트를 나누고 Facade 조율을 안 하면 "각각은 깔끔한데 전체 유스케이스가 안 돌아가네?"가 된다.

이 과정을 겪으면서 의외였던 것이 세 가지 있었다.

첫째, **책임 분리는 결국 비즈니스가 결정한다.** 서브도메인은 "없어도 사업이 돌아가는가?"로, 어그리게이트는 "단독으로 접근할 비즈니스 시나리오가 있는가?"로 갈린다. 기술적 판단처럼 보이는 것도 출발점은 비즈니스였다.

둘째, **"규칙을 담는다"와 "의사결정을 내린다"는 다르다.** 처음에 `BrandService`가 "중복 이름 검증"이라는 규칙을 담고 있으니 도메인 서비스라고 생각했다. 하지만 리팩토링 후 깨달았다. Service가 하는 건 DB 상태를 확인하고 결과를 적용하는 **조율**이다. 실제 의사결정("이 이름이 유효한가?")은 `BrandName` VO가 내린다. "규칙이 있으면 도메인 서비스"가 아니라 **"비즈니스 의사결정을 직접 내리면 도메인 서비스"**다. 이 차이를 모르면 Service를 전부 `domain/`에 두는 실수를 하게 된다 — 실제로 내가 그랬다.

셋째, **"도메인 서비스가 없다"는 의심해봐야 한다.** 처음에 모든 Service를 application으로 옮기고 "도메인 서비스가 하나도 없다"고 결론 내렸다. 근데 곰곰이 보니 `LikeFacade.like()`의 if/else 분기가 비즈니스 의사결정이었다. 지우면 규칙이 사라지는 코드가 application 레이어에 있었던 것이다. "지우면 뭐가 사라지는가?"를 물었더니 숨어있던 도메인 서비스가 드러났다.

넷째, **원칙은 깰 수 있다. 단, 조건이 있다.** 깨면 실제로 코드에서 터지는 원칙은 지켜야 한다. 깨도 다른 수단으로 보호 가능하고, 그 보호를 현재 조직이 유지할 수 있다면, 깨는 것이 정답일 수 있다. DDD 리포지토리 원칙을 깬 것도 이 판단의 결과였다.

오늘도 느낀 바지만 역시 "은탄환은 없다" 하지만 시작은 "어디에 뭘 둬야 하지?"라는 고민의 출발점으로는 충분했다. 적어도 감으로 결정하는 것보다, **키워드를 들이대고 검증할 수 있다**는 점에서 설계의 질이 달라질 수 있다고 생각한다.
