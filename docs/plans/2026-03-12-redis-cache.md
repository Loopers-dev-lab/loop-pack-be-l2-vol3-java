# Redis Cache 적용 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 상품 상세/목록 API에 Redis 캐시를 적용하여 DB 조회 부하를 줄이고 응답 속도를 개선한다.

**Architecture:** Spring Cache Abstraction + RedisTemplate 하이브리드 방식. 상품 상세는 `@Cacheable`로 선언적 캐시, 상품 목록은 RedisTemplate으로 복합 키 + 패턴 기반 무효화 처리. Redis 장애 시 DB fallback을 보장하는 CacheErrorHandler 구현.

**Tech Stack:** Spring Boot 3.4.4, Spring Cache, Redis 7.0 (Lettuce), Jackson JSON Serializer

---

## Trade-off 분석

### 결정 1: 캐시 적용 레이어 — Facade vs Service

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **Facade 레이어 (선택)** | 조합된 최종 결과를 캐싱 → Brand+Stock 조회도 캐시 히트 시 스킵 | 캐시 키에 여러 도메인 정보 포함, Facade 책임 증가 |
| Service 레이어 | 도메인별 독립 캐싱, 세밀한 제어 | 여러 서비스 결과를 조합하는 Facade에서 N+1 캐시 호출 가능 |

**근거:** 현재 API의 병목은 상품+브랜드+재고를 조합하는 `ProductFacade`에서 발생. Facade 레이어에서 최종 DTO(`ProductDetail`)를 캐싱하면 하위 3개 서비스 호출을 모두 스킵할 수 있다.

### 결정 2: 직렬화 — GenericJackson2Json vs Jackson2Json<T>

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **GenericJackson2Json (선택)** | 범용, 캐시마다 타입 설정 불필요 | JSON에 `@class` 타입 정보 포함 → 저장 공간 약간 증가 |
| Jackson2Json\<T\> | 타입별 최적화, 깔끔한 JSON | 캐시마다 별도 설정 필요, 보일러플레이트 |

**근거:** 캐시 대상이 `ProductDetail`(record)과 `Page<ProductDetail>` 두 종류. 범용 직렬화기로 충분하며, 타입별 설정의 복잡도가 이점 대비 크다.

### 결정 3: 목록 캐시 무효화 — 패턴 삭제 vs 전체 flush

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **패턴 삭제 (선택)** | 영향 범위 최소화, 다른 캐시 유지 | `KEYS` 명령 사용 시 성능 이슈 → `SCAN` 필요 |
| 전체 flush | 단순, 누락 없음 | 무관한 캐시까지 삭제, 캐시 히트율 급감 |

**근거:** `product:list:*` 패턴으로 목록 캐시만 삭제. `SCAN` 기반으로 구현하여 Redis blocking 방지.

### 결정 4: 캐시 미스 대응 — CacheErrorHandler vs try-catch

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **CacheErrorHandler (선택)** | Spring Cache 전체에 일관 적용, 코드 침투 없음 | RedisTemplate 직접 사용 부분은 별도 처리 필요 |
| 개별 try-catch | 세밀한 에러 처리 | 보일러플레이트, 누락 위험 |

**근거:** `@Cacheable` 사용하는 상품 상세는 CacheErrorHandler로 커버. RedisTemplate 직접 사용하는 상품 목록은 서비스 내 try-catch로 처리.

---

## 멘토링 기준 적합성 분석 (2026-03-12 검사)

> 멘토링 문서: `docs/mentoring` (2026-03-11, Alen 멘토)

### 1. Cache-Aside 패턴 — 멘토 강조 사항

**멘토 원문 (§2.5):**
> "Cache-Aside만으로도 대부분 충분. 사본이 차 있을 때는 다 캐시에서 조회되므로 많은 방어가 됨"

| 멘토 기준 | 플랜 설계 | 부합 |
|---|---|---|
| Cache-Aside 패턴 사용 | 상품 상세: `@Cacheable` (= Cache-Aside), 상품 목록: 수동 Cache-Aside | ✅ |
| DB를 SOT(Source of Truth)로 취급 | Write-Around: DB 먼저 → 캐시 evict/put | ✅ |
| 에러 시 잘못된 데이터 캐싱 방지 | `CacheErrorHandler` + try-catch 방어 | ✅ |

### 2. 갱신 전략 — Evict(하수) vs Put(고수)

**멘토 원문 (§2.3):**
> "변경 시 캐시 삭제(Evict) - 하수", "변경 시 캐시 덮어쓰기(Put) - 고수 ⭐"

| 대상 | 현재 전략 | 멘토 권장 | 갭 |
|---|---|---|---|
| 상품 상세 수정 | `@CacheEvict` (evict) | Put(덮어쓰기) 권장 | ⚠️ |
| 상품 삭제 | `@CacheEvict` (evict) | evict OK | ✅ |
| 좋아요 토글 | evict (상세 + 목록) | put 가능하나 복잡 | ⚠️ |

**evict vs put 트레이드오프:**

| 비교 | Evict (현재) | Put (멘토 권장) |
|---|---|---|
| 구현 복잡도 | 낮음 | 높음 (수정 후 ProductDetail 재조립 필요) |
| 캐시 미스 | 삭제 후 다음 조회 시 DB 접근 | 없음 (즉시 새 값 적재) |
| 정합성 위험 | 낮음 (항상 DB에서 최신 조회) | 조립 로직 버그 시 캐시-DB 불일치 |
| 적합 상황 | 조회 QPS 낮을 때 | 조회 QPS 높아 miss 1회도 아까울 때 |

**현재 판단:** 학습 프로젝트에서 Evict로 충분. 멘토도 "Cache-Aside만으로 대부분 충분" 강조.
→ 수정 후 즉시 재조회 패턴이 빈번하면 Put으로 전환 검토.

### 3. TTL 설정 — 멘토 기준 대조

**멘토 원문 (Q11):**
> "모니터링보다 도메인 특성/중요도 기준"

| 데이터 | 플랜 TTL | 멘토 예시 | 판단 |
|---|---|---|---|
| 상품 상세 | 10분 | "자주 바뀜" → 30초 | ⚠️ 과도할 수 있음 |
| 상품 목록 | 3분 | - | ✅ 적정 |
| 기본값 | 5분 | - | ✅ |

→ 멘토의 30초는 대규모 트래픽 기준. 부트캠프에서는 변경 빈도 낮아 5~10분도 무방.
→ **Hit Rate 모니터링 후 조정 권장.**

### 4. 캐시 스탬피드 방어 — 미구현

**멘토 원문 (§2.4):** Lock + Timeout + Retry, 확률적 갱신, Pre-warming

**현재 플랜:** 스탬피드 방어 없음.

**판단:** 멘토 원문 (§2.5): "트래픽이 더 많은 서비스에서만 추가 고민 필요"
→ 부트캠프에서 스탬피드 발생 가능성 극히 낮음. 미구현 유지.

### 5. 계층형 캐시 (L1 + L2) — 미적용

**멘토 원문 (§2.2):** L1 로컬(짧은 TTL) + L2 글로벌(긴 TTL) 2단계

**현재 플랜:** Redis 단일 레이어.

→ 단일 서버 환경에서 L1의 이점 제한적. 스케일아웃 시 도입 검토.

### 6. 캐시 키 설계 — 멘토 Q10 대조

**멘토 권장:** 검색 결과(ID 배열) + 상품 정보(객체) 2레이어 분리

**현재 플랜:** `Page<ProductDetail>` 통째로 저장 (단일 레이어)

| 비교 | 멘토 권장 (2레이어) | 현재 (단일) |
|---|---|---|
| 메모리 효율 | 높음 (중복 없음) | 낮음 (동일 상품 중복 저장) |
| 무효화 정밀도 | 상품 1개 → 해당 캐시만 | 상품 1개 → 모든 목록 삭제 |
| 구현 복잡도 | 높음 (2단계 조회) | 낮음 |

→ 상품 수 적고 TTL 짧아 단일 레이어로 충분. 상품 > 1만 시 전환 검토.

---

## Task 1: RedisCacheManager + CacheConfig 설정

**Files:**
- Create: `modules/redis/src/main/java/com/loopers/config/redis/RedisCacheConfig.java`
- Test: `apps/commerce-api/src/test/java/com/loopers/config/RedisCacheConfigTest.java`

**Step 1: 실패 테스트 작성**

```java
@SpringBootTest
@Import(RedisTestContainersConfig.class)
class RedisCacheConfigTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void CacheManager_빈이_등록되어야_한다() {
        assertThat(cacheManager).isNotNull();
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    void 기본_TTL이_설정되어야_한다() {
        RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;
        RedisCacheConfiguration config = redisCacheManager.getCacheConfigurations().get("productDetail");
        assertThat(config).isNotNull();
    }
}
```

**Step 2: 테스트 실행 → FAIL 확인**

Run: `./gradlew :apps:commerce-api:test --tests "RedisCacheConfigTest"`
Expected: FAIL — `CacheManager` Bean 없음

**Step 3: 최소 구현**

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(LettuceConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
            objectMapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL
        );

        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
            .entryTtl(Duration.ofMinutes(5))
            .disableCachingNullValues();

        RedisCacheConfiguration productDetailConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));
        RedisCacheConfiguration productListConfig = defaultConfig.entryTtl(Duration.ofMinutes(3));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("productDetail", productDetailConfig)
            .withCacheConfiguration("productList", productListConfig)
            .build();
    }
}
```

**Step 4: 테스트 실행 → PASS 확인**

Run: `./gradlew :apps:commerce-api:test --tests "RedisCacheConfigTest"`
Expected: PASS

---

## Task 2: CacheErrorHandler 구현

**Files:**
- Create: `modules/redis/src/main/java/com/loopers/config/redis/CustomCacheErrorHandler.java`
- Modify: `modules/redis/src/main/java/com/loopers/config/redis/RedisCacheConfig.java` — `CachingConfigurer` 구현 추가

**Step 1: 실패 테스트 작성**

```java
class CustomCacheErrorHandlerTest {

    private final CustomCacheErrorHandler handler = new CustomCacheErrorHandler();

    @Test
    void 캐시_조회_실패_시_예외를_삼키고_로그만_남긴다() {
        // given
        RuntimeException exception = new RuntimeException("Redis connection refused");

        // when & then — 예외가 전파되지 않아야 함
        assertDoesNotThrow(() ->
            handler.handleCacheGetError(exception, null, "testKey")
        );
    }

    @Test
    void 캐시_저장_실패_시_예외를_삼키고_로그만_남긴다() {
        RuntimeException exception = new RuntimeException("Redis connection refused");

        assertDoesNotThrow(() ->
            handler.handleCachePutError(exception, null, "testKey", "testValue")
        );
    }
}
```

**Step 2: 테스트 실행 → FAIL 확인**

Run: `./gradlew :apps:commerce-api:test --tests "CustomCacheErrorHandlerTest"`

**Step 3: 최소 구현**

```java
@Slf4j
public class CustomCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        log.warn("[Cache GET 실패] key={}, error={}", key, e.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        log.warn("[Cache PUT 실패] key={}, error={}", key, e.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        log.warn("[Cache EVICT 실패] key={}, error={}", key, e.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        log.warn("[Cache CLEAR 실패] error={}", e.getMessage());
    }
}
```

`RedisCacheConfig`에 `CachingConfigurer` 구현 추가:

```java
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {
    // ... 기존 cacheManager 메서드 ...

    @Override
    public CacheErrorHandler errorHandler() {
        return new CustomCacheErrorHandler();
    }
}
```

**Step 4: 테스트 실행 → PASS 확인**

Run: `./gradlew :apps:commerce-api:test --tests "CustomCacheErrorHandlerTest"`

---

## Task 3: 상품 상세 API — @Cacheable 적용

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/product/ProductFacade.java` — `getProduct()` 캐시 적용
- Test: `apps/commerce-api/src/test/java/com/loopers/application/product/ProductFacadeCacheTest.java`

**Step 1: 실패 테스트 작성**

```java
@SpringBootTest
@Import(RedisTestContainersConfig.class)
class ProductFacadeCacheTest {

    @Autowired private ProductFacade productFacade;
    @Autowired private CacheManager cacheManager;
    // ... 필요한 의존성 주입 + 데이터 셋업

    @AfterEach
    void tearDown() {
        cacheManager.getCache("productDetail").clear();
    }

    @Test
    void 상품_상세_조회_시_캐시에_저장된다() {
        // given — 상품 데이터 준비
        // when
        productFacade.getProduct(productId);
        // then
        Cache.ValueWrapper cached = cacheManager.getCache("productDetail").get(productId);
        assertThat(cached).isNotNull();
    }

    @Test
    void 캐시_히트_시_DB를_조회하지_않는다() {
        // given — 첫 조회로 캐시 적재
        productFacade.getProduct(productId);

        // when — 두 번째 조회
        ProductDetail result = productFacade.getProduct(productId);

        // then — 결과 정상 + (검증: 쿼리 로그 또는 spy로 서비스 호출 횟수 확인)
        assertThat(result).isNotNull();
    }
}
```

**Step 2: 테스트 실행 → FAIL 확인**

Run: `./gradlew :apps:commerce-api:test --tests "ProductFacadeCacheTest"`

**Step 3: Facade에 @Cacheable 적용**

```java
@Cacheable(cacheNames = "productDetail", key = "#productId")
@Transactional(readOnly = true)
public ProductDetail getProduct(Long productId) {
    // 기존 로직 그대로
}
```

**Step 4: 테스트 실행 → PASS 확인**

Run: `./gradlew :apps:commerce-api:test --tests "ProductFacadeCacheTest"`

---

## Task 4: 상품 상세 캐시 무효화 — @CacheEvict

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/product/ProductFacade.java` — `update()`, `delete()` 캐시 무효화

**Step 1: 실패 테스트 작성**

```java
@Test
void 상품_수정_시_상세_캐시가_삭제된다() {
    // given — 조회로 캐시 적재
    productFacade.getProduct(productId);
    assertThat(cacheManager.getCache("productDetail").get(productId)).isNotNull();

    // when — 상품 수정
    productFacade.update(productId, "변경된 이름", "변경된 설명", new Money(99999));

    // then
    assertThat(cacheManager.getCache("productDetail").get(productId)).isNull();
}

@Test
void 상품_삭제_시_상세_캐시가_삭제된다() {
    // given
    productFacade.getProduct(productId);
    // when
    productFacade.delete(productId);
    // then
    assertThat(cacheManager.getCache("productDetail").get(productId)).isNull();
}
```

**Step 2: 테스트 실행 → FAIL 확인**

**Step 3: 최소 구현**

```java
@CacheEvict(cacheNames = "productDetail", key = "#productId")
@Transactional
public ProductDetail update(Long productId, String name, String description, Money price) {
    // 기존 로직
}

@CacheEvict(cacheNames = "productDetail", key = "#productId")
public void delete(Long productId) {
    // 기존 로직
}
```

**Step 4: 테스트 실행 → PASS 확인**

---

## Task 5: 상품 목록 API — RedisTemplate 캐시 적용

**Files:**
- Create: `apps/commerce-api/src/main/java/com/loopers/application/product/ProductCacheService.java`
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/product/ProductFacade.java` — `getProducts()` 캐시 연동
- Test: `apps/commerce-api/src/test/java/com/loopers/application/product/ProductCacheServiceTest.java`

**Step 1: 실패 테스트 작성**

```java
@SpringBootTest
@Import(RedisTestContainersConfig.class)
class ProductCacheServiceTest {

    @Autowired private ProductCacheService productCacheService;
    @Autowired private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        // SCAN으로 product:list:* 패턴 키 삭제
    }

    @Test
    void 목록_캐시_저장_및_조회() {
        // given
        String key = "product:list:brand:1:sort:LIKES_DESC:page:0:size:20";
        // ... Page<ProductDetail> 준비

        // when
        productCacheService.putProductList(key, page);
        Optional<Page<ProductDetail>> cached = productCacheService.getProductList(key);

        // then
        assertThat(cached).isPresent();
        assertThat(cached.get().getContent()).hasSize(expectedSize);
    }

    @Test
    void 패턴_기반_목록_캐시_삭제() {
        // given — 여러 키 저장
        productCacheService.putProductList("product:list:brand:1:sort:LIKES_DESC:page:0:size:20", page1);
        productCacheService.putProductList("product:list:brand:2:sort:PRICE_ASC:page:0:size:20", page2);

        // when
        productCacheService.evictProductListAll();

        // then — 모든 목록 캐시 삭제됨
        assertThat(productCacheService.getProductList("product:list:brand:1:sort:LIKES_DESC:page:0:size:20")).isEmpty();
        assertThat(productCacheService.getProductList("product:list:brand:2:sort:PRICE_ASC:page:0:size:20")).isEmpty();
    }
}
```

**Step 2: 테스트 실행 → FAIL 확인**

**Step 3: 최소 구현**

```java
@RequiredArgsConstructor
@Component
public class ProductCacheService {

    private static final String LIST_PREFIX = "product:list:";
    private static final Duration LIST_TTL = Duration.ofMinutes(3);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public String buildListKey(Long brandId, ProductSortType sortType, int page, int size) {
        return LIST_PREFIX + "brand:" + brandId + ":sort:" + sortType + ":page:" + page + ":size:" + size;
    }

    public void putProductList(String key, Page<ProductDetail> page) {
        try {
            String json = objectMapper.writeValueAsString(page);
            redisTemplate.opsForValue().set(key, json, LIST_TTL);
        } catch (Exception e) {
            log.warn("[목록 캐시 저장 실패] key={}, error={}", key, e.getMessage());
        }
    }

    public Optional<Page<ProductDetail>> getProductList(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            // 역직렬화 처리
            return Optional.of(/* deserialized page */);
        } catch (Exception e) {
            log.warn("[목록 캐시 조회 실패] key={}, error={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void evictProductListAll() {
        try {
            Set<String> keys = scanKeys(LIST_PREFIX + "*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[목록 캐시 삭제 실패] error={}", e.getMessage());
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
```

**Step 4: Facade에 캐시 서비스 연동**

```java
// ProductFacade.getProducts() 수정
@Transactional(readOnly = true)
public Page<ProductDetail> getProducts(Long brandId, ProductSortType sortType, Pageable pageable) {
    String cacheKey = productCacheService.buildListKey(brandId, sortType, pageable.getPageNumber(), pageable.getPageSize());

    Optional<Page<ProductDetail>> cached = productCacheService.getProductList(cacheKey);
    if (cached.isPresent()) return cached.get();

    Page<ProductDetail> result = /* 기존 DB 조회 로직 */;

    productCacheService.putProductList(cacheKey, result);
    return result;
}
```

**Step 5: 테스트 실행 → PASS 확인**

---

## Task 6: 좋아요 토글 시 캐시 무효화

**Files:**
- Modify: `apps/commerce-api/src/main/java/com/loopers/application/like/LikeTransactionService.java` — 좋아요 변경 시 캐시 무효화
- Test: 기존 좋아요 테스트에 캐시 무효화 검증 추가

**Step 1: 실패 테스트 작성**

```java
@Test
void 좋아요_등록_시_해당_상품_상세_캐시와_목록_캐시가_삭제된다() {
    // given — 상품 조회로 캐시 적재
    productFacade.getProduct(productId);

    // when — 좋아요
    likeTransactionService.doLike(userId, productId);

    // then
    assertThat(cacheManager.getCache("productDetail").get(productId)).isNull();
    // 목록 캐시도 비어야 함
}
```

**Step 2: 테스트 실행 → FAIL 확인**

**Step 3: 최소 구현**

`LikeTransactionService`에 캐시 무효화 추가:

```java
@Transactional
public void doLike(Long userId, Long productId) {
    // ... 기존 로직 ...
    if (result.countChanged()) {
        productService.incrementLikeCount(productId);
        evictProductCaches(productId);
    }
}

@Transactional
public void doUnlike(Long userId, Long productId) {
    // ... 기존 로직 ...
    productService.decrementLikeCount(activeLike.get().productId());
    evictProductCaches(productId);
}

private void evictProductCaches(Long productId) {
    cacheManager.getCache("productDetail").evict(productId);
    productCacheService.evictProductListAll();
}
```

**Step 4: 테스트 실행 → PASS 확인**

---

## Task 7: 통합 E2E 테스트 + 성능 비교

**Files:**
- Modify: `apps/commerce-api/src/test/java/com/loopers/interfaces/api/product/ProductV1ApiE2ETest.java` — 캐시 동작 E2E 검증
- Create: `.http/cache-test.http` — 수동 성능 비교용

**Step 1: E2E 테스트 추가**

```java
@Test
void 상품_상세_두번_조회_시_캐시에서_응답한다() {
    // given — 상품 생성
    // when — 같은 상품 2회 조회
    // then — 두 번 모두 200 OK, 같은 결과
}

@Test
void 상품_수정_후_조회하면_변경된_데이터가_반환된다() {
    // given — 상품 생성 + 조회(캐시 적재)
    // when — 상품 수정 + 재조회
    // then — 변경된 이름/설명이 반환
}
```

**Step 2: .http 파일 작성**

```http
### 캐시 미스 — 첫 번째 조회
GET http://localhost:8080/api/v1/products/1

### 캐시 히트 — 두 번째 조회 (응답시간 비교)
GET http://localhost:8080/api/v1/products/1

### 목록 조회 — 캐시 미스
GET http://localhost:8080/api/v1/products?brandId=1&sort=LIKES_DESC&page=0&size=20

### 목록 조회 — 캐시 히트
GET http://localhost:8080/api/v1/products?brandId=1&sort=LIKES_DESC&page=0&size=20
```

**Step 3: 전체 테스트 실행**

Run: `./gradlew :apps:commerce-api:test`
Expected: 기존 테스트 + 캐시 테스트 모두 PASS

---

## 전체 실행 순서 요약

| Task | 내용 | 핵심 파일 | 상태 |
|------|------|----------|------|
| 1 | RedisCacheManager + Config | `RedisCacheConfig.java` | ✅ 완료 |
| 2 | CacheErrorHandler | `CustomCacheErrorHandler.java` | ✅ 완료 |
| 3 | 상품 상세 @Cacheable | `ProductFacade.getProduct()` | ✅ 완료 |
| 4 | 상품 상세 @CacheEvict | `ProductFacade.update()/delete()` | ✅ 완료 |
| 5 | 상품 목록 RedisTemplate 캐시 | `ProductListCacheService.java` | ✅ 완료 |
| 6 | 좋아요 토글 캐시 무효화 | `LikeTransactionService.java` | ✅ 완료 |
| 7 | E2E 테스트 + 성능 비교 | E2E 테스트 + `.http/cache-test.http` | ✅ 완료 |

---

## 멘토링 기준 대조 — 현재 트레이드오프 현황

> 기준: `docs/mentoring` (Round-5 멘토링, Alen 멘토, 2026-03-11)

### 현재 구현이 멘토 기준에 부합하는 항목

| 멘토 기준 | 현재 구현 | 근거 |
|----------|----------|------|
| 캐시 전략 먼저 설계 (코드 아님) | ✅ research.md → plan.md → 구현 | 멘토: "캐시는 항상 먼저 설계함" (Q1) |
| SOT = DB, 캐시 = 파생 데이터 | ✅ DB 먼저 업데이트 후 캐시 처리 | 멘토: Write-Around 패턴 권장 (Q4) |
| 에러 시 잘못된 데이터 캐싱 금지 | ✅ `CacheErrorHandler` + `disableCachingNullValues` + try-catch | 멘토: 빈 배열 캐싱 사고 사례 (Q12) |
| Redis 장애 시 서비스 지속 | ✅ `CustomCacheErrorHandler`가 예외 삼김 → DB fallback | 멘토: 캐시는 보조, DB가 SOT |
| Cache-Aside 패턴 | ✅ 캐시 미스 → DB 조회 → 캐시 저장 | 멘토: "Cache-Aside만으로도 대부분 충분" (2.5절) |

### 현재 구현이 멘토 기준에 미달하는 항목 (의도적 트레이드오프)

#### TO-1. 갱신 전략 — Evict(현재) vs Put(멘토 권장)

| | Evict (현재) | Put (멘토 권장) |
|--|-------------|----------------|
| **방식** | DB 업데이트 후 캐시 삭제 | DB 업데이트 후 캐시에 새 값 덮어쓰기 |
| **장점** | 단순, 정합성 보장 | DB 재조회 불필요, 캐시 히트율 유지 |
| **단점** | 다음 조회가 DB로 감 (캐시 미스 1회) | 캐시에 넣는 값 구성 로직 필요, 복잡도 증가 |
| **멘토 평가** | "하수" (2.3절) | "고수" (2.3절) |

**현재 선택 근거**: 상품 수정/삭제 빈도가 조회 대비 극히 낮음. Evict 후 1회 캐시 미스의 비용이 Put 구현 복잡도 대비 낮다고 판단.

**개선 시점**: 수정 API 호출 직후 조회 급증 패턴이 관측될 때 → `@CachePut`으로 전환.

#### TO-2. 목록 캐시 구조 — 전체 객체(현재) vs ID 배열 계층 분리(멘토 권장)

| | 전체 객체 캐싱 (현재) | ID 배열 + 상품 정보 분리 (멘토 권장) |
|--|---------------------|--------------------------------------|
| **방식** | `List<ProductDetail>` 전체를 JSON으로 저장 | Layer 1: 검색 결과 ID 배열 (TTL 30초), Layer 2: 상품 정보 (TTL 1시간) |
| **장점** | 단순, 한 번의 Redis 호출로 완결 | 메모리 효율, 상품 정보 재사용, 세밀한 TTL |
| **단점** | 중복 저장 (같은 상품이 여러 목록에), 메모리 낭비 | 2번 Redis 호출 (ID 조회 → 상품 조회), 구현 복잡도 높음 |
| **멘토 평가** | 언급 없음 (암묵적 비권장) | "정보의 위계에 맞게 캐시 레이어를 나눔" (Q10) |

**현재 선택 근거**: 상품 수가 10만 수준, 목록 페이지 크기 20건. 중복 저장의 메모리 비용보다 구현 단순성이 현 단계에서 우선.

**개선 시점**: 캐시 메모리 사용량이 Redis 70% 초과할 때 → 계층 분리 전환.

#### TO-3. TTL 설정 — 현재 vs 멘토 기준

| 캐시 | 현재 TTL | 멘토 기준 | 차이 |
|------|---------|----------|------|
| 상품 상세 | 10분 | 30초 (자주 바뀜) ~ 1시간 (안 바뀜) | 멘토 기준 "상품 정보는 자주 바뀜 → 30초" (Q11)와 불일치 |
| 상품 목록 | 3분 | 30초 (DB 방어) | 6배 차이 |

**현재 선택 근거**: 현재 트래픽이 낮은 학습 프로젝트. stale data 허용 범위가 넓어 긴 TTL로 캐시 히트율 극대화.

**개선 시점**: 실 트래픽 투입 시 도메인 특성에 맞게 TTL 조정. 모니터링(Hit Rate) 기반으로 결정.

#### TO-4. 캐시 스탬피드 방어 — 미구현

| 방어 방법 | 구현 여부 | 멘토 평가 |
|----------|----------|----------|
| Lock + Timeout + Retry | ❌ 미구현 | 기본 방어 (Q7) |
| 확률적 갱신 | ❌ 미구현 | 보완 방어 (Q7) |
| Pre-warming | ❌ 미구현 | 멘토 선호 ⭐ (Q7) |

**현재 선택 근거**: 멘토 언급 — "Cache-Aside만으로도 대부분 충분" (2.5절). 현재 트래픽 수준에서 스탬피드 발생 가능성 극히 낮음.

**개선 시점**: Read QPS가 높아져 동시 캐시 미스가 관측될 때 → Lock 패턴 우선 적용.

#### TO-5. 캐시 히트율 모니터링 — 미구현

**멘토 기준**: "Hit Rate 낮으면 → 캐시 제거 고려" (Q11)

**현재**: 모니터링 없음. 캐시가 실제로 유효한지 데이터 기반 판단 불가.

**개선 시점**: Prometheus + Grafana 연동 시 `cache.gets`, `cache.puts`, `cache.evictions` 메트릭 노출.

#### TO-6. 페이지별 캐싱 범위 — 전체 페이지(현재) vs 1페이지만(멘토 권장)

**멘토**: "2페이지 이상 접근이 거의 없음 → 1페이지만 캐싱" (Q10)

**현재**: 모든 페이지를 동일하게 캐싱.

**현재 선택 근거**: 페이지별 접근 패턴 데이터 없음. 데이터 확보 후 결정.

**개선 시점**: 페이지별 접근 로그 분석 후 2페이지 이상 캐싱 제거 검토.

### 개선 우선순위 (멘토 기준 중요도)

| 순위 | 항목 | 이유 |
|------|------|------|
| 1 | TO-1. Evict → Put 전환 | 멘토가 명시적으로 "하수 vs 고수" 구분 |
| 2 | TO-3. TTL 조정 (10분 → 도메인 기반) | 멘토: 도메인 특성/중요도 기준 |
| 3 | TO-2. 목록 캐시 계층 분리 | 멘토가 Q10에서 구체적 구조 제시 |
| 4 | TO-5. 히트율 모니터링 | 멘토: "Hit Rate 낮으면 캐시 제거 고려" |
| 5 | TO-4. 스탬피드 방어 | 멘토: "Cache-Aside만으로도 충분" → 후순위 |
| 6 | TO-6. 1페이지만 캐싱 | 접근 패턴 데이터 필요 → 가장 후순위 |
