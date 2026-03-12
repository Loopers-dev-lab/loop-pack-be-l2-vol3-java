# 3. 인덱스 이후 캐시 적용

## 인덱스로도 해결되지 않은 문제

인덱스 적용 후 최신순(4.8초), 좋아요순(4.6초)은 여전히 느리다. filesort는 제거했고 인덱스도 타고 있지만, `deleted_at IS NULL` 조건이 거의 전체 데이터를 포함하기 때문에 테이블 본체 I/O가 발생한다.

이 지점에서 DB 쿼리 최적화는 한계에 도달했다. **DB를 아예 안 치는 방향**으로 전환한다.

---

## 왜 인덱스 먼저, 캐시 나중인가

캐시를 먼저 적용하면 위험하다.

```
인덱스 없이 캐시만 걸면:
  캐시 hit  → 즉시 응답 (좋다)
  캐시 miss → 23초 (위험)

인덱스 + 캐시:
  캐시 hit  → 즉시 응답
  캐시 miss → 5초 (인덱스가 안전망)
```

캐시 miss는 반드시 발생한다 — 서버 시작 직후, TTL 만료, 캐시 삭제 후. 이때 인덱스가 없으면 사용자가 23초를 기다려야 한다.

**인덱스는 안전망이고, 캐시는 그 위의 성능 최적화다.**

---

## 캐시 라이브러리 비교

| 캐시 | TTL | 최대 개수 제한 | 특징 |
|------|-----|--------------|------|
| ConcurrentMapCache | X | X | Spring 기본 제공, TTL 없음, 메모리 제한 없음 |
| **Caffeine** | O | O | Spring Boot 공식 권장, 고성능 로컬 캐시 |
| Ehcache | O | O | XML 설정 기반, 디스크 캐시 지원 |
| Redis | O | O | 외부 서버 필요, 멀티 서버 환경용 |

### Redis를 선택하지 않은 이유

Redis는 **멀티 인스턴스 환경**에서 캐시 일관성이 필요할 때 적합하다.

```
서버 A에서 상품 수정 → Redis 캐시 삭제 → 서버 B도 즉시 반영
```

현재 프로젝트는 **단일 인스턴스**이므로 외부 Redis 서버를 운영할 이유가 없다. 네트워크 호출 없이 JVM 메모리에서 바로 읽는 로컬 캐시가 더 빠르고 단순하다.

### Caffeine을 선택한 이유

ConcurrentMapCache는 TTL과 최대 개수 제한이 없어서 실무에서 쓰기 어렵다:

- **TTL 없음**: 캐시가 영원히 남아서 DB가 바뀌어도 옛날 데이터를 반환
- **크기 제한 없음**: 캐시가 무한히 쌓이면 OOM(OutOfMemoryError) 위험

Caffeine은 TTL + 최대 개수 제한을 지원하고, Spring Boot가 공식 권장하는 로컬 캐시다.

---

## TTL(Time To Live) 설정

### TTL이 필요한 이유

TTL이 없으면 캐시가 영원히 남는다.

```
1. 상품 목록 조회 → 캐시에 저장됨
2. 5분 후 상품 가격이 바뀜 → DB에는 반영됨
3. 상품 목록 재조회 → 캐시에서 옛날 가격을 반환 ← 문제
```

TTL을 5분으로 설정하면 최대 5분까지만 옛날 데이터를 허용한다.

### TTL 방식: `expireAfterWrite` vs `expireAfterAccess`

| 방식 | 만료 기준 | 적합한 경우 |
|------|----------|------------|
| `expireAfterWrite` | 캐시에 **저장된 시점**부터 N분 | 변경 가능한 데이터 (상품) |
| `expireAfterAccess` | **마지막 조회 시점**부터 N분 | 변경되지 않는 데이터 (설정값) |

상품 데이터는 가격, 재고 등이 변경될 수 있으므로 `expireAfterWrite`가 적합하다. `expireAfterAccess`는 계속 조회되면 캐시가 영원히 갱신되지 않아 옛날 데이터가 계속 반환된다.

---

## 캐시 무효화(`@CacheEvict`)

TTL만 있으면 상품을 수정해도 최대 5분간 옛날 데이터가 보인다. 관리자가 가격을 수정했는데 5분간 반영이 안 되면 문제다.

`@CacheEvict`로 상품 변경 시 즉시 캐시를 삭제한다.

```
둘 다 쓰는 이유:
  @CacheEvict → 변경 시 즉시 삭제
  TTL         → 혹시 빠뜨려도 5분 후 자동 만료 (안전망)
```

---

## 적용 코드

### CacheConfig

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("products", "products:brand");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)  // TTL: 5분
                .maximumSize(100)                       // 최대 100개 (OOM 방지)
        );
        return cacheManager;
    }
}
```

### 조회: `@Cacheable`

```java
@Cacheable(value = "products",
    key = "#pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort.toString()")
public Page<ProductInfo> getProducts(Pageable pageable) { ... }

@Cacheable(value = "products:brand",
    key = "#brandId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort.toString()")
public Page<ProductInfo> getProductsByBrandId(Long brandId, Pageable pageable) { ... }
```

### 변경: `@CacheEvict`

```java
@CacheEvict(value = {"products", "products:brand"}, allEntries = true)
@Transactional
public ProductInfo registerProduct(RegisterProductCommand command) { ... }

@CacheEvict(value = {"products", "products:brand"}, allEntries = true)
@Transactional
public ProductInfo updateProduct(Long id, UpdateProductCommand command) { ... }

@CacheEvict(value = {"products", "products:brand"}, allEntries = true)
@Transactional
public void deleteProduct(Long id) { ... }
```

`allEntries = true`로 해당 캐시의 모든 엔트리를 삭제한다. 상품이 변경되면 어떤 페이지의 캐시가 영향받을지 모르기 때문이다.

---

## 캐시 적용 후 최종 결과

| API | Before (인덱스 없음) | 인덱스만 | 인덱스 + 캐시 hit |
|-----|---------------------|---------|-----------------|
| 상품 최신순 | 23.86초 | 4.8초 | **0.003초** |
| 좋아요순 | 22.70초 | 4.6초 | **0.002초** |
| 브랜드별 가격순 | 22.81초 | 0.31초 | **0.002초** |

캐시 miss 시에는 인덱스 덕분에 5초 수준으로 응답한다. 캐시 hit 시에는 DB를 아예 안 가므로 0.003초.

**약 8,000배 개선.**

### 각 단계가 해결한 것

| 단계 | 해결한 문제 | 방법 |
|------|-----------|------|
| 인덱스 | Full Table Scan + filesort | 복합 인덱스 3개, 컬럼 순서 원칙 적용 |
| 캐시 | DB I/O 자체 | Caffeine 로컬 캐시, TTL 5분, @CacheEvict |
