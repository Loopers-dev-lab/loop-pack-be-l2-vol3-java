# ADR: 브랜드/카테고리 캐시 전략

## 맥락

브랜드와 카테고리는 상품 조회, 상품 생성/수정, 관리자 조회 등 여러 유스케이스에서 반복적으로 참조된다.

이 데이터는 변경 빈도보다 조회 빈도가 높고, 개별 조회와 전체 목록 조회가 모두 존재한다. 따라서 Redis를 이용해 읽기 비용을 줄이되, 잘못된 목록 캐시가 오래 남지 않도록 일관성 전략을 분명히 할 필요가 있다.

## 결정

다음 전략을 채택한다.

1. 브랜드/카테고리 캐시는 TTL 없는 Redis read-through 전략을 사용한다.
2. 개별 조회는 `brand:{id}`, `category:{id}` 키를 사용하고, 전체 목록 조회는 `brand:all`, `category:all` 키를 사용한다.
3. 개별 데이터 변경 시에는 해당 개별 키를 overwrite 또는 delete 하고, 전체 목록 키는 삭제한다.
4. 캐시 동기화는 가능하면 트랜잭션 `afterCommit` 시점에 수행한다.
5. Redis 장애나 실험 상황에서는 `loopers.cache.brand-category.enabled=false`로 DB pass-through 구현체로 우회할 수 있게 한다.
6. 읽기와 쓰기 모두 `RedisConfig.REDIS_TEMPLATE_MASTER`를 사용한다.

## 결정 이유

### 1. TTL보다 수동 무효화가 더 적합하다

브랜드/카테고리는 자주 바뀌지 않지만, 잘못된 값이 남아 있으면 상품 조회/생성 전반에 영향을 준다. 이 경우 TTL 만료를 기다리기보다 변경 시점에 직접 키를 갱신/삭제하는 편이 더 명확하다.

### 2. 개별 키와 목록 키의 일관성 요구가 다르다

개별 조회는 정확도가 더 중요하므로 해당 키를 직접 overwrite/delete 한다. 반면 전체 목록은 구성원이 바뀔 수 있어 부분 갱신보다 목록 키를 삭제하고 다음 조회 때 재생성하는 편이 단순하고 안전하다.

### 3. 커밋 이후 동기화가 정합성에 유리하다

트랜잭션이 롤백되었는데 Redis만 먼저 갱신되면 데이터 불일치가 생긴다. 그래서 가능한 경우 `afterCommit`에 sync를 등록해 DB 반영이 확정된 뒤 캐시를 바꾼다.

### 4. 토글 가능한 구조가 실험과 장애 대응에 유리하다

Redis 캐시를 완전히 끄고 DB pass-through로 돌릴 수 있어, 캐시 효과 비교 실험이나 장애 상황 대응이 쉽다.

## 현재 구현

### 키 구조

#### 브랜드

- 개별: `brand:{id}`
- 전체 목록: `brand:all`

근거:
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/brand/redis/BrandCacheRepositoryImpl.java`

#### 카테고리

- 개별: `category:{id}`
- 전체 목록: `category:all`

근거:
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/category/redis/CategoryCacheRepositoryImpl.java`

### TTL

브랜드/카테고리 캐시는 TTL을 사용하지 않는다.

근거:
- `BrandCacheRepositoryImpl`의 `redisTemplate.opsForValue().set(...)` 호출에는 만료 시간이 없다
- `CategoryCacheRepositoryImpl`도 동일하다
- `apps/commerce-api/src/main/resources/application.yml`에 brand/category TTL 설정이 없다

### 조회 전략

#### findById

1. Redis 개별 키 조회
2. miss면 DB 조회
3. DB에서 찾으면 Redis 개별 키 저장

#### findAll

1. `brand:all` 또는 `category:all` 조회
2. miss면 DB 전체 조회
3. 목록 키와 개별 키들을 함께 저장

### 무효화 전략

#### 저장/수정 시

- 개별 키 overwrite
- 전체 목록 키 삭제

#### 삭제 시

- 개별 키 삭제
- 전체 목록 키 삭제

즉,

- 개별 조회 정합성은 개별 키 직접 갱신으로 맞추고
- 목록 정합성은 목록 키를 날린 뒤 다음 조회에서 재생성하는 방식이다.

### 동기화 시점

가능하면 트랜잭션 `afterCommit`에서 sync 한다.

#### 브랜드

- `apps/commerce-api/src/main/java/com/loopers/infrastructure/brand/redis/BrandCacheSyncer.java`

#### 카테고리

- `apps/commerce-api/src/main/java/com/loopers/infrastructure/category/redis/CategoryCacheSyncer.java`

### 토글/우회 전략

- 설정: `loopers.cache.brand-category.enabled`
- 기본값: `true`

비활성화하면 DB pass-through 구현체를 사용한다.

근거:
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/brand/db/BrandDbPassThroughCacheRepositoryImpl.java`
- `apps/commerce-api/src/main/java/com/loopers/infrastructure/category/db/CategoryDbPassThroughCacheRepositoryImpl.java`

## 주의점

1. 이 캐시는 TTL 기반 자동 만료가 아니라 수동 무효화 기반이다.
2. 읽기와 쓰기 모두 master 템플릿을 사용하므로 replica 읽기 분산은 하지 않는다.
3. 목록 캐시는 일부 항목만 부분 갱신하지 않고 통째로 제거 후 재생성한다.

## 한 줄 요약

브랜드/카테고리 캐시는 TTL 없는 Redis read-through + afterCommit 수동 무효화 전략이며, 개별 키는 overwrite/delete로 맞추고 목록 키는 삭제 후 재생성한다.
