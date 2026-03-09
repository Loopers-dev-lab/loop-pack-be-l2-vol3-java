# 05. 설계 고민 사항

---

## 1. 유비쿼터스 언어 선택

- 주문을 `Order`가 아닌 `Orders`로 정의
  - `ORDER`는 SQL 예약어이므로 테이블명 충돌을 방지한다.
- 좋아요를 `Like`가 아닌 `Favorite`로 정의
  - `LIKE`도 SQL 예약어이다.
  - 추후 브랜드 좋아요 등 확장 시 `Favorite`가 더 범용적이다.

## 2. Facade의 역할과 Service 간 호출 금지

- Service가 다른 Service를 직접 호출하지 않는다.
- Facade가 여러 Service를 조합하여 유스케이스를 완성한다.
  - 예: 주문 시 `Facade → ProductService.checkAndDecreaseStock()` → `Facade → OrderService.createOrder()`
- Service 간 결합을 방지하여 각 Service가 독립적으로 테스트/변경 가능하다.

## 3. VO 적용 범위

- `Stock`, `Money`는 자연스럽게 VO로 설계했다.
- `ProductName`, `BrandName`, `MemberName`도 VO로 설계했다.
  - VO로 감싸면 생성 시점에 자기 검증이 가능하다.
  - 다만 과도한 VO 적용은 복잡도를 증가시킬 수 있어 핵심 도메인 값에만 적용한다.

## 4. Entity에 VO를 직접 사용하지 않는 이유

- `@Embedded` 사용 시 컬럼 매핑 관리가 복잡해진다. (`@AttributeOverride` 등)
- VO가 변경되면 Entity와 DB 스키마에 영향이 전파된다.
- JPA 쿼리 작성 시 경로가 복잡해진다. (`product.price.value`)
- Entity는 원시 타입, Domain Model에서 VO를 사용하여 레이어 간 책임을 분리한다.
- 기존 프로젝트 패턴(MemberEntity: String, MemberModel: VO)과 일관성을 유지한다.

## 5. 스냅샷 패턴과 연관관계

- `order_product`는 `product`를 참조하는 게 아니라 주문 시점의 데이터를 복사(스냅샷)한다.
  - `product_name`, `product_price`, `brand_name`을 직접 저장한다.
- ERD에서 `order_product`와 `product` 간 관계선을 그리지 않는다.
  - 스냅샷은 원본과 독립적이므로 관계가 아니다.
- `product_id`는 원본 추적용으로 남겨두되, FK 제약은 걸지 않는다.
  - 분석/통계, 재주문 등 활용 가능성을 위해 보존한다.

## 6. FK 미사용 결정

- 모든 테이블 간 FK 제약을 설정하지 않는다.
- 데이터 정합성은 애플리케이션 레벨(Service)에서 관리한다.
  - 존재 여부 검증은 Service에서 수행한다.
- FK 없이도 JPA `@ManyToOne`, `@OneToMany` 등 연관관계는 동작한다.
  - JPA는 컬럼 값으로 JOIN할 뿐, DB FK 제약과는 독립적이다.

## 7. Soft Delete 전략 분리

- `favorite`를 제외한 모든 엔티티는 `is_del`(char) 컬럼으로 논리 삭제를 처리한다.
- `favorite`는 등록/취소가 빈번하므로 물리 삭제(hard delete)로 처리한다.
- 기존 BaseEntity의 `deleted_at`(ZonedDateTime) 대신 `is_del`(char)을 선택했다.

## 8. 클래스 다이어그램의 상세 수준

- 레이어 전체(Controller, Facade, Service, Repository)를 그리지 않는다.
- 도메인 모델의 핵심 필드와 관계만 표현한다.
- 설계 의도를 전달하는 것이 목적이므로 간결함을 우선한다.
