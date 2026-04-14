# Ranking API 구현 실행 계획

## 현재 상태

- **완료**: (1) Kafka Consumer → Redis ZSET 적재 (commerce-streamer)
- **완료**: Phase 1 - `RankingRepository` (READ) + `RedisRankingRepository` in commerce-api
  - `apps/commerce-api/src/main/java/com/loopers/domain/ranking/RankingRepository.java`
  - `apps/commerce-api/src/main/java/com/loopers/infrastructure/ranking/RedisRankingRepository.java`
  - `apps/commerce-api/src/test/java/com/loopers/infrastructure/ranking/RedisRankingRepositoryTest.java` ✅ 통과
- **미완료**: Phase 2~4

---

## 구현할 것: `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1`

상품 상세 조회 rank 포함은 **이번 범위 제외**.

---

## Phase 2: RankingFacade + RankingInfo (Red → Green)

### 2-1. Red: `RankingFacadeTest` 작성
파일: `apps/commerce-api/src/test/java/com/loopers/application/ranking/RankingFacadeTest.java`

테스트 케이스:
- `getPage()` - ZSET 순서(점수 높은 순)로 정렬된 랭킹 목록 반환
- `getPage()` - ZSET 비어있으면 빈 목록 반환
- `getPage()` - totalPages 가 count/size 기반으로 계산됨

주의: `RankingFacadeTest`에서 `Product`는 mock 사용 (`product.getId()` 반환값 필요).
`Product.of()`로 생성된 객체는 JPA save 없이는 `getId() == null` 임.

### 2-2. Green: RankingInfo + RankingFacade 작성

**`RankingInfo.java`**
```
apps/commerce-api/src/main/java/com/loopers/application/ranking/RankingInfo.java
```
```java
record RankingInfo(Long rank, Long productId, String name, String description,
                   String brand, int price, long likeCount)
```

**`RankingFacade.java`**
```
apps/commerce-api/src/main/java/com/loopers/application/ranking/RankingFacade.java
```

로직:
1. `(page-1)*size` → offset 계산 (1-based page)
2. `rankingRepository.findProductIdsByRank(date, offset, size)` → productId 목록 (ZREVRANGE 순서 = 랭킹 순서)
3. `rankingRepository.countByDate(date)` → totalPages = ceil(count / size)
4. `productRepository.findAllByIdIn(productIds)` → Product 목록 (IN 쿼리)
5. `brandRepository.findAllByIdIn(brandIds)` → Brand 목록 (IN 쿼리)
6. productId 목록 순서 기준으로 Product 정렬 (Map으로 index 매핑)
7. rank = offset + index + 1 (1-based)
8. `PageResponse<RankingInfo>` 반환

의존: `RankingRepository`, `ProductRepository`, `BrandRepository`

---

## Phase 3: API Layer (Green)

**`RankingDto.java`**
```
apps/commerce-api/src/main/java/com/loopers/interfaces/api/ranking/RankingDto.java
```
```java
public class RankingDto {
    public record Response(Long rank, Long productId, String name, String description,
                           String brand, int price, long likeCount) {
        public static Response from(RankingInfo info) { ... }
    }
}
```

**`RankingController.java`**
```
apps/commerce-api/src/main/java/com/loopers/interfaces/api/ranking/RankingController.java
```
```java
@GetMapping("/api/v1/rankings")
public ApiResponse<PageResponse<RankingDto.Response>> getRankings(
    @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size
) {
    LocalDate targetDate = date != null ? date : LocalDate.now(ZoneOffset.UTC);
    PageResponse<RankingInfo> infos = rankingFacade.getPage(targetDate, page, size);
    return ApiResponse.success(infos.map(RankingDto.Response::from));
}
```

---

## Phase 4: E2E 테스트 (Red → Green)

파일: `apps/commerce-api/src/test/java/com/loopers/interfaces/api/ranking/RankingApiE2ETest.java`

테스트 케이스:
1. ZSET + DB 데이터 있을 때 `GET /api/v1/rankings` → 랭킹 순서로 상품정보 포함 응답
2. ZSET 비어있을 때 → 빈 content, totalPages=0 응답
3. `date` 파라미터 생략 시 → 오늘 날짜 기준 동작

패턴 참조: `ProductApiE2ETest` (`@Import(RedisTestContainersConfig.class)`, `@SpringBootTest(RANDOM_PORT)`)

---

## 키 설계 참조

| 항목 | 값 |
|---|---|
| ZSET Key | `ranking:all:yyyyMMdd` |
| member | productId (String) |
| 정렬 방식 | ZREVRANGE (score 높은 순) |
| 페이지 | 1-based (`page=1` → offset=0) |
| totalPages | `ceil(ZCARD / size)` |

---

## 관련 파일 경로

| 역할 | 경로 |
|---|---|
| RankingRepository (READ 인터페이스) | `apps/commerce-api/src/main/java/com/loopers/domain/ranking/RankingRepository.java` |
| RedisRankingRepository (READ 구현) | `apps/commerce-api/src/main/java/com/loopers/infrastructure/ranking/RedisRankingRepository.java` |
| ProductRepository | `apps/commerce-api/src/main/java/com/loopers/domain/product/ProductRepository.java` |
| BrandRepository | `apps/commerce-api/src/main/java/com/loopers/domain/brand/BrandRepository.java` |
| ApiResponse | `apps/commerce-api/src/main/java/com/loopers/interfaces/api/ApiResponse.java` |
| PageResponse | `apps/commerce-api/src/main/java/com/loopers/support/page/PageResponse.java` |
| RedisTestContainersConfig | `modules/redis/src/testFixtures/java/com/loopers/testcontainers/RedisTestContainersConfig.java` |
| RedisCleanUp | `modules/redis/src/testFixtures/java/com/loopers/utils/RedisCleanUp.java` |
| 기존 WRITE RankingRepository | `apps/commerce-streamer/src/main/java/com/loopers/domain/ranking/RankingRepository.java` |
