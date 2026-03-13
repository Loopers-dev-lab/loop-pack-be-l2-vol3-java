# ADR: 상품 좋아요 수 비정규화

## 맥락

현재 서비스는 `Like`를 멤버와 상품 간의 관계 테이블로 저장하면서, 동시에 `Product.likeCount` 필드에 좋아요 수를 별도로 저장한다.

즉, 좋아요 데이터는 아래 2가지 형태로 존재한다.

1. `likes` 테이블: 누가 어떤 상품을 좋아요했는지에 대한 관계 데이터
2. `products.like_count`: 상품 조회 시 바로 사용할 집계 값

이 설계는 얼핏 보면 중복 저장처럼 보일 수 있다. 실제로 `likes` 테이블만 두고, 상품 목록/상세 조회 시마다 `COUNT(*)`로 좋아요 수를 계산하는 선택지도 가능하다.

하지만 현재 요구사항과 조회 패턴을 보면, 좋아요 수를 상품에 비정규화하지 않으면 조회 성능과 정렬 복잡도가 빠르게 커진다.

## 문제 정의

현재 상품 조회 요구사항은 단순히 "좋아요 여부를 저장"하는 수준이 아니다.

- 상품 상세 응답에 좋아요 수가 포함된다.
- 상품 목록 응답에 좋아요 수가 포함된다.
- 상품 목록은 `likes_desc` 정렬을 지원한다.
- 내 좋아요 목록 조회에서도 상품 응답 모델을 그대로 재사용한다.

즉, 좋아요 수는 쓰기 부가 정보가 아니라 **상품 조회의 핵심 정렬/응답 필드**다.

이 상황에서 매 조회마다 `likes` 테이블을 `COUNT` 집계해서 붙이면 다음 문제가 생긴다.

1. 목록 조회 시 집계 조인 또는 서브쿼리가 필요하다.
2. `likes_desc` 정렬이 비싸진다.
3. 페이지네이션과 정렬 인덱스 설계가 복잡해진다.
4. 상세/목록/좋아요 목록 등 여러 조회 경로가 같은 집계 비용을 반복 부담한다.

## 관찰된 근거

### 1. 좋아요 수는 실제 조회 모델에 직접 노출된다

- `apps/commerce-api/src/main/java/com/loopers/application/product/view/ProductView.java`
- `apps/commerce-api/src/main/java/com/loopers/application/product/view/PublicProductListItemView.java`
- `apps/commerce-api/src/main/java/com/loopers/interfaces/api/product/ProductDto.java`

위 코드에서 상품 응답 모델은 모두 `likeCount`를 직접 포함한다.

### 2. 정렬 기준 자체가 `likeCount`를 사용한다

- `apps/commerce-api/src/main/java/com/loopers/domain/product/query/ProductSortOption.java`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/product/ProductRepositoryImpl.java`

현재 상품 목록 조회는 `likes_desc` 정렬을 지원하고, 실제 쿼리도 `Product.likeCount` 기준 정렬을 사용한다.

### 3. 성능 ADR에서도 `brand + likes DESC`가 최우선 쿼리로 다뤄진다

- `docs/adr/2026-03-13-product-search-index-benchmark-adr.md`
- `docs/adr/2026-03-13-product-search-paging-adr.md`

두 문서 모두 `brand + likes DESC`를 대표 성능 쿼리로 다룬다. 이건 좋아요 수가 단순 표시값이 아니라, 조회 성능 설계의 중심 변수라는 뜻이다.

### 4. 현재 구현은 집계 조회가 아니라 원자적 카운트 업데이트를 선택했다

- `apps/commerce-api/src/main/java/com/loopers/application/product/ProductLikeAplicationService.java`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/product/ProductJpaRepository.java`

좋아요 등록/취소 시 `ProductRepository.updateLikeCount(...)`를 통해 상품의 `likeCount`를 직접 증가/감소시킨다.

```java
productRepository.updateLikeCount(productId, 1);
productRepository.updateLikeCount(productId, -1);
```

실제 저장소 구현은 JPQL 조건부 update를 사용한다.

### 5. 동시성 테스트도 이 선택을 전제로 작성돼 있다

- `apps/commerce-api/src/test/java/com/loopers/application/product/ProductLikeAplicationServiceConcurrencyTest.java`
- `apps/commerce-api/src/test/java/com/loopers/application/product/ProductStockApplicationServiceConcurrencyTest.java`
- `docs/adr/2026-03-05-stock-deduction-concurrency-adr.md`

좋아요 수 증감은 동시 요청에서도 정합성이 유지돼야 한다는 전제로 테스트와 ADR이 이미 정리돼 있다.

## 고려한 선택지

### 선택지 A. `likes` 테이블만 두고 조회 시마다 COUNT 한다

장점:

- 데이터 중복이 없다.
- 쓰기 시 별도 카운트 업데이트가 필요 없다.

단점:

- 상세/목록/내 좋아요 목록 모두에서 집계 비용이 반복된다.
- `likes_desc` 정렬 쿼리가 무거워진다.
- 조회 성능 최적화 포인트가 상품 테이블이 아니라 집계 쿼리 쪽으로 이동한다.
- 페이지네이션과 인덱스 전략이 더 복잡해진다.

### 선택지 B. `Product.likeCount`를 비정규화하고 쓰기 시 동기화한다

장점:

- 상세/목록 조회가 단순해진다.
- `likes_desc` 정렬을 상품 테이블 인덱스로 최적화할 수 있다.
- 상품 응답 모델이 추가 집계 없이 일관되게 유지된다.
- 조회 부담을 쓰기 시의 작은 원자 update로 옮길 수 있다.

단점:

- `likes`와 `likeCount` 간 불일치 가능성이 생긴다.
- 좋아요 등록/취소와 카운트 업데이트 사이의 정합성을 신경 써야 한다.
- 필요하면 보정 배치/검증 쿼리가 필요할 수 있다.

## 결정

`Product.likeCount`를 유지하는 비정규화 설계를 채택한다.

좋아요 관계 데이터는 `likes` 테이블에 저장하고, 조회/정렬 최적화를 위해 `products.like_count`를 별도 필드로 함께 관리한다.

좋아요 등록/취소 시에는 `ProductLikeAplicationService -> ProductRepository.updateLikeCount -> ProductJpaRepository.updateLikeCount` 경로로 원자적 카운트 업데이트를 수행한다.

## 결정 이유

### 1. 좋아요 수는 조회 모델의 일부가 아니라 조회 경로의 핵심 축이다

상세/목록 응답에 항상 포함되고, 정렬 기준으로도 직접 사용된다. 이런 필드를 매번 집계해서 계산하는 쪽보다 read model에 가깝게 유지하는 편이 현재 요구사항에 맞다.

### 2. `likes_desc` 정렬을 상품 조회 쿼리 안에서 해결해야 한다

이번 성능 ADR에서 확인했듯이, 유저 상품 목록 성능은 상품 테이블의 정렬/인덱스 전략이 핵심이다. `likeCount`를 상품 컬럼으로 두어야 인덱스 설계와 cursor paging 전략을 일관되게 가져갈 수 있다.

### 3. 쓰기 비용보다 읽기 이득이 더 크다

좋아요 등록/취소는 개별 이벤트지만, 목록/상세 조회는 반복적으로 일어난다. 읽기 경로 단순화와 정렬 최적화 이득이 더 크다고 판단한다.

### 4. 정합성 문제는 원자 update + 테스트로 관리 가능하다

현재 구현은 단순 엔티티 save가 아니라 원자적 update 경로를 택했고, 동시성 테스트도 이 경로를 검증하고 있다. 즉 비정규화의 핵심 리스크를 코드와 테스트로 관리하는 방향이 이미 자리잡고 있다.

## 결과

### 긍정적 결과

- 상품 목록/상세 응답 모델이 단순해진다.
- `likes_desc` 정렬 최적화가 가능해진다.
- product list 성능 ADR과 인덱스 전략이 자연스럽게 이어진다.
- cursor paging 설계 시 정렬 필드를 상품 테이블에서 직접 다룰 수 있다.

### 감수하는 비용

- `likes`와 `products.like_count` 사이의 일시적 불일치 가능성
- 운영 중 보정 필요 시 재집계 쿼리 또는 배치 필요
- 좋아요 취소/삭제/브랜드 연쇄 삭제 등의 경로에서 count 동기화 책임 증가

## 후속 작업

1. `likes`와 `products.like_count` 불일치 여부를 점검할 수 있는 검증 쿼리 또는 운영 점검 절차를 준비한다.
2. 브랜드 삭제/상품 삭제 등 연쇄 변경 경로에서 likeCount 정합성이 유지되는지 계속 검증한다.
3. 좋아요 수가 랭킹/추천에 더 적극적으로 사용되기 시작하면, 보정 배치 또는 이벤트 기반 동기화 전략 필요성을 다시 검토한다.
