# ADR: 유저 상품 캐시 전략

## 후속 결정

목록 캐시 실험 이후 최종 런타임 경로는 다음과 같이 정리했다.

1. 런타임 Redis 캐시는 공개 상품 상세 조회(`GET /api/v1/products/{productId}`)에 적용한다.
2. 공개 상품 목록 캐시는 런타임에서 제거하고, 관련 코드/문서는 cursor 기반 목록 실험 이력 보존용으로만 유지한다.
3. cursor 기반 목록 요청은 동시 요청 상황에서도 캐시 대상이 아니라는 점을 테스트와 문서로 명시한다.

### 상세 캐시 전환 이유

- 과제 요구사항 중 "상품 상세 API 캐시"를 직접 충족해야 한다.
- 목록 캐시는 `cursor == null`, `size == 30`, `latest/likes`에만 국한된 실험용 hot-path 최적화였고, 과제 관점에서는 상세 캐시 증거가 더 직접적이다.
- 상세 조회는 `ProductQueryFacade.get(productId)` 단일 경계에 AOP를 적용하면 비즈니스 로직을 유지한 채 캐시 miss fallback과 무효화를 붙이기 쉽다.

### 상세 캐시 적용 방식

- 캐시 경로: `ProductController.getProduct -> ProductQueryFacade.get`
- AOP annotation: `@CachedPublicProductDetail`
- Redis key: `product:public-detail:v1:id={productId}`
- TTL: `cache.public-product-detail.ttl`
- 무효화:
  - `ProductApplicationService.update`
  - `ProductApplicationService.deleteSoft`
  - `ProductLikeAplicationService.increaseLikeCount`
  - `ProductLikeAplicationService.decreaseLikeCount`
  - `ProductLikeAplicationService.decreaseLikeCountIfPresent`

위 경로는 모두 `@EvictPublicProductDetailCache`로 연결한다.

## 맥락

유저 상품 목록 조회는 `latest`, `likes`, `price`, `name` 정렬과 브랜드/카테고리/가격 조건을 함께 지원한다.

하지만 실제 트래픽과 성능 관점에서 모든 조합을 캐시 대상으로 잡으면 다음 문제가 바로 생긴다.

1. 캐시 키 수가 빠르게 늘어난다.
2. TTL 실험 결과를 해석하기 어려워진다.
3. 좋아요 수 변경 시 invalidation 범위가 불필요하게 커진다.
4. 캐시 미스/동시 만료 시 stampede 위험을 통제하기 어려워진다.

이번 변경의 목표는 다음 4가지다.

1. 유저 상품 목록 캐시 범위를 작게 시작한다.
2. TTL과 key dimension 전략을 실험 가능하게 만든다.
3. `likes` 변경 시 최소 범위 invalidation을 적용한다.
4. Prometheus/Grafana로 hit/miss와 concurrent fill을 실제 관측한다.

## 실험 범위

이번 ADR은 **유저 공개 상품 캐시 전략**을 다룬다.

- 포함
  - `latest`, `likes` 정렬의 첫 페이지 30건 캐시
  - Redis 기반 수동 캐시 + AOP 적용
  - `likes` 정렬 top list 최소 invalidation
  - TTL jitter와 concurrent fill 메트릭
  - `docker/infra-compose.yml` 기준 seed 포함 로컬 실험 경로
- 제외
  - `price`, `name` 정렬 캐시
  - cursor 페이지 캐시
  - 상품 생성/수정/삭제에 대한 전체 list invalidation
  - stale-while-revalidate, single-flight, 분산락 도입

## 고려한 선택지

### 1. 캐시 범위 선택지

#### 선택지 A. 모든 유저 상품 목록 조합을 캐시한다

장점:

- 캐시 적용 범위가 넓다.
- 이론상 hit 기회를 최대화할 수 있다.

단점:

- 브랜드/카테고리/가격/정렬 조합으로 키가 빠르게 폭증한다.
- TTL 실험 결과를 해석하기 어렵다.
- invalidation 범위가 너무 넓어진다.

#### 선택지 B. `latest`, `likes` 첫 30건만 캐시한다

장점:

- hot path에만 집중할 수 있다.
- TTL 실험과 dimension 실험을 통제하기 쉽다.
- invalidation 범위를 작게 유지할 수 있다.

단점:

- 캐시 적용 범위가 제한적이다.
- 나머지 요청은 계속 DB 조회를 탄다.

### 2. invalidation 범위 선택지

#### 선택지 C. `likes` 변경 시 모든 public list 캐시를 지운다

장점:

- 구현이 단순하다.

단점:

- `latest` 캐시까지 함께 날려 불필요한 미스를 만든다.
- 좋아요 이벤트가 잦아질수록 캐시 효율이 떨어진다.

#### 선택지 D. `likes` 정렬 key만 최소 invalidation 한다

장점:

- 변경 영향이 `likes` list로 제한된다.
- 현 단계에서 가장 작은 수정으로 정합성을 높일 수 있다.

단점:

- 현재는 `KEYS product:public-list:v1:sort=likes*` 패턴 삭제라, key 수가 커지면 더 정교한 전략이 필요하다.

## 결정

다음 전략을 채택한다.

1. 유저 공개 상품 목록 캐시는 `latest`, `likes` 정렬기준 각각 30개만 적용한다.
2. 공개 목록 조합은 `PublicProductListQueryApplicationService`가 담당하고, 캐시/메트릭은 AOP로 분리한다.
3. 상세 캐시는 `product:public-detail:v1:id={productId}` key를 사용하고, `cache.public-product-detail.ttl`로 기본 TTL을 관리한다.
4. 상세 캐시는 `cache.public-product-detail.jitter-enabled`, `cache.public-product-detail.jitter-ratio`로 TTL jitter를 선택 적용할 수 있다.
5. `likes` 변경 시에는 `likes` 정렬 캐시 key만 최소 invalidation 한다.
6. 로컬 실험은 `docker/infra-compose.yml`로 infra를 올리면 seed까지 같이 들어가도록 맞춘다.

## 결정 이유

### 1. hot path만 먼저 잡아야 실험 결과가 해석 가능하다

모든 목록 조합을 한 번에 캐시하면 TTL과 dimension 실험이 뒤엉킨다. `latest`/`likes` top 30만 먼저 캐시해야 hit/miss와 stale trade-off를 읽을 수 있다.

### 3. `likes` 변경은 최소 범위 invalidation으로 시작하는 게 안전하다

좋아요 수 변경은 `likes` 정렬 순위를 바꿀 수 있지만 `latest` 정렬에는 직접 영향이 없다. 따라서 첫 단계에서는 `likes` key만 제거하는 편이 캐시 효율과 구현 복잡도 사이 균형이 좋다.

### 4. stampede는 먼저 관측하고 그다음 완화한다

현재 구조에는 single-flight나 stale serve가 없다. 그래서 먼저 `fill.inflight`, `fill.concurrent` 메트릭을 추가하고, TTL jitter까지 넣어 synchronized expiry를 관측 가능한 상태로 만들었다.

### 5. 로컬 실험 경로는 compose 하나로 닫혀 있어야 한다

실험 중 `docker/infra-compose.yml`는 MySQL seed가 빠져 있었고, CSV line ending도 `\r\n`로 맞춰져 있어 실제 데이터가 0건으로 들어갔다. 이를 수정해 `infra-compose up + mysql-seed`만으로 `categories 5`, `brands 30`, `products 300000`이 채워지도록 맞췄다.

## 실험 환경

- 애플리케이션: `apps/commerce-api`
- DB: MySQL 8.0 (`docker/infra-compose.yml`)
- 캐시: Redis master/replica (`docker/infra-compose.yml`)
- 관측: Prometheus + Grafana (`docker/monitoring-compose.yml`)
- 데이터: `categories 5`, `brands 30`, `products 300000`

### 실험에 사용한 명령

```bash
docker compose -f docker/infra-compose.yml up -d mysql redis-master redis-readonly kafka kafka-ui
docker compose -f docker/infra-compose.yml up mysql-seed
docker compose -f docker/monitoring-compose.yml up -d
SPRING_PROFILES_ACTIVE=local ./gradlew :apps:commerce-api:bootRun
```

## 실험 결과

### 1. infra compose seed 결과

- `categories_count = 5`
- `brands_count = 30`
- `products_count = 300000`

### 2. `latest` 캐시 hit 확인

같은 요청을 2번 호출한 뒤 `/actuator/prometheus`에서 아래를 확인했다.

- `loopers_product_public_list_cache_requests_total{sort="latest", result="miss"} = 1`
- `loopers_product_public_list_cache_requests_total{sort="latest", result="hit"} = 1`

### 3. `likes` 변경 후 최소 invalidation 확인

실험용 브랜드/회원/상품을 만들고, 같은 `likes` 목록 요청을 2번 호출한 뒤 좋아요를 등록했다.

- 좋아요 전 첫 응답: `likeCount = 0`
- 좋아요 전 두 번째 응답: `likeCount = 0`
- 좋아요 전 TTL: `30`
- 좋아요 직후 TTL: `-2` (key 제거)
- 좋아요 후 첫 응답: `likeCount = 1`
- 좋아요 후 두 번째 응답: `likeCount = 1`
- 좋아요 후 재적재 TTL: `30`

Prometheus에서도 다음을 확인했다.

- `loopers_product_public_list_cache_requests_total{sort="likes", result="miss"} = 3`
- `loopers_product_public_list_cache_requests_total{sort="likes", result="hit"} = 2`

첫 `miss` 1회는 전체 likes 캐시 실측 과정에서 발생했고, 이후 filtered 실험에서 `miss 1회 + hit 1회 + like 후 miss 1회 + hit 1회`가 추가됐다.

### 4. 동시 cursor 요청 bypass 확인 (의도된 규칙)

동시 요청 시 cursor 페이지가 캐시를 타지 않는지 별도 테스트를 추가했다.

- 테스트: `PublicProductListCacheAspectTest.cache_concurrentCursorRequests_bypassCacheByDesign`
- 요청 조건: `sort=likes`, `size=30`, `cursor != null`인 동일 조건 2개를 `CountDownLatch + CompletableFuture`로 동시 실행
- 관측 결과:
  - 두 요청 모두 `loopers_product_public_list_cache_requests_total{result="bypass",sort="likes"}`로 집계 (`count=2`)
  - `buildCacheKey`, `findByKey`, `save` 호출 0회
- 해석: 현재 캐시 eligibility는 `criteria.cursor() == null`일 때만 캐시 허용이므로, cursor 요청의 non-cache 동작은 결함이 아니라 설계 의도다.

## 결과

### 긍정적 결과

- 상위 30개 캐시만으로도 `latest`, `likes` hot path에 즉시 hit를 만들 수 있다.
- `likes` 변경 직후 최소 invalidation으로 stale 데이터를 걷어낼 수 있다.
- 상세 조회는 단일 상품 key 기반이라 TTL/무효화 전략을 더 직접적으로 설명할 수 있다.
- 상세 캐시는 jitter를 선택 적용할 수 있어 만료 시점 집중을 완화할 수 있다.

### 감수하는 비용

- 현재 `likes` invalidation은 `KEYS product:public-list:v1:sort=likes*` 기반이라 key 수가 커지면 비효율적일 수 있다.
- `GLOBAL_ONLY` 전략에서는 brand/category filter 요청도 동일 key를 공유할 수 있어, dimension 전략 실험이 필요하다.
- stampede는 아직 "관측 가능"한 수준이지, single-flight로 완화한 상태는 아니다.
- cursor 기반 다음 페이지 요청은 `criteria.cursor() == null` 조건에서 제외되므로, 동시 요청이어도 캐시 bypass가 정상이다.
- 상세 캐시는 상품 수정/삭제/좋아요 변경에 맞춰 개별 key 무효화를 수행하므로, 목록 캐시 실험과는 운영 목적이 다르다.

