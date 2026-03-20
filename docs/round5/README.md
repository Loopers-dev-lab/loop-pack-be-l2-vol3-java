## 테스트 데이터 설계

이 프로젝트의 데이터는 패션 이커머스 트래픽 패턴을 재현하도록 설계되었다. 균등 분포와 무의미한 이름(`Brand_001`, `P_0_13`)으로 생성하면 프로덕션과 다른 실행 계획이 나와 인덱스 효과와 쿼리 병목을 제대로 측정할 수 없다.

### 분포 설계 근거

사용된 분포 용어는 다음과 같다.

- **Power-law** — 소수의 항목이 대부분의 값을 차지하는 분포. 예) 상위 20개 브랜드가 브랜드당 2,000~3,000개 상품을 보유
- **Zipf** — Power-law의 극단적인 형태. 순위가 낮아질수록 빈도가 급격히 감소한다. 예) 1위 상품이 2위보다 2배, 3위보다 3배 많은 좋아요를 받음
- **로그정규분포** — 저가 쪽에 더 많이 분포하는 가격 분포. 카테고리별 min/max 범위 내에서 100원 단위로 생성

| 테이블 | 건수 | 분포 | 선택 이유 |
|---|---|---|---|
| brands | 100 (활성 90, 삭제 10) | 티어별 편중 | S/A/B 티어로 인기도 구분 → 캐시 히트율 차이 측정 |
| products | 100,000 (활성 ~85K, 삭제 ~15K) | Power-law | 인기 브랜드 2,000~3,000개, 소규모 100~300개 → brandId 필터 인덱스 효과 극대화 |
| users | 10,000 | 균등 | 유저 간 편차보다 상품 간 인기 편차가 핵심 변수 |
| likes | ~416,000 | Zipf | 상위 1% 상품에 ~70% 집중 → `ORDER BY like_count DESC` 정렬 및 캐시 히트율 차이 측정 가능 |
| orders | 100,000 | Power-law | 인기 상품(좋아요 상위)에 집중 → 특정 상품 주문 이력 페이지네이션 병목 재현 |
| order_items | ~200,000 | 주문당 1~3개 | — |

### 패션 데이터

**브랜드 100개** — 한국 패션 이커머스 실제 브랜드명 사용:
- 글로벌 스포츠 (나이키, 아디다스, 뉴발란스 등), 글로벌 SPA (자라, 유니클로 등)
- 국내 스트릿 (커버낫, 디스이즈네버댓 등), 국내 베이직 (무신사스탠다드, 탑텐 등)
- 디자이너, 아웃도어, 캐주얼, 슈즈, 여성 브랜드 등

**상품 카테고리 7개:**

| 카테고리 | 비중 | 가격대 (KRW) | 재고 범위 | 품절률 |
|---------|------|-------------|----------|--------|
| 상의 | 25% | 19,900~89,900 | 100~500 | 5% |
| 하의 | 18% | 29,900~129,900 | 100~500 | 5% |
| 아우터 | 12% | 69,900~399,900 | 30~200 | 8% |
| 원피스/세트 | 8% | 39,900~199,900 | 30~200 | 6% |
| 슈즈 | 15% | 59,900~299,900 | 20~150 | 10% |
| 가방 | 12% | 39,900~249,900 | 30~200 | 6% |
| 악세서리 | 10% | 9,900~149,900 | 200~1,000 | 3% |

**상품명**: `[수식어] + [아이템 타입] + [컬러]` (예: "오버핏 크루넥 맨투맨 차콜")

### 소프트 딜리트

`WHERE deleted_at IS NULL` 필터링의 실제 효과를 테스트하기 위해 데이터 생성 시 소프트 딜리트를 적용한다.

| 대상 | 삭제 비율 | 방식 |
|------|----------|------|
| brands | 10% (10/100) | 하위 10개 브랜드 소프트 딜리트 |
| products | ~15% (~15K/100K) | 2-wave 삭제 (아래 참고) |

**2-wave 삭제:**
- **Wave 1** (좋아요 생성 전): 삭제 브랜드 소속 상품 연쇄 삭제 (~10K) + 오래된 시즌 단종 (3K) → 좋아요/주문 없는 완전 퇴장 상품
- **Wave 2** (좋아요 생성 후): 최근 단종 (2K) → like_count > 0이지만 삭제된 상품 → 현실적 시나리오 재현

### 생성 흐름

```
Phase 1:   브랜드 100개 생성 (패션 이름)
Phase 1.5: 브랜드 10개 소프트 딜리트 (10%)
Phase 2:   상품 100K개 생성 (카테고리별 이름/가격/재고, 브랜드별 편중 분포)
Phase 2.5: 상품 소프트 딜리트 Wave 1 (~13K)
Phase 3:   유저 10K 생성
Phase 4:   좋아요 ~500K 생성 (Zipf 분포, 활성 상품만 대상)
Phase 4.5: 상품 소프트 딜리트 Wave 2 (2K)
Phase 5:   주문 100K 생성 (Power-law 분포, 활성 상품만 대상)
Phase 6:   like_count 동기화 (likes 테이블 COUNT → products.like_count)
```

### 설정 (application.yml)

건수는 아래 값을 변경해 조정할 수 있다. 기본값은 비활성화(`enabled: false`)이다.
```yaml
app:
  data-generator:
    enabled: false
    brand-count: 100
    product-count: 100000
    user-count: 10000
    like-count: 500000
    order-count: 100000
```

### 실행

두 가지 방법으로 데이터를 생성할 수 있다.

**방법 1: 서버 기동 시 자동 생성**
```bash
./gradlew :apps:commerce-api:bootRun --args='--app.data-generator.enabled=true'
```

**방법 2: 서버 기동 후 API 호출**
```bash
# 데이터 생성 요청 (비동기, 즉시 반환)
curl -X POST -H "X-Loopers-Ldap: loopers.admin" http://localhost:8080/api-admin/v1/data-generator/bulk-init
```
```bash
# 생성 현황 확인
curl -H "X-Loopers-Ldap: loopers.admin" http://localhost:8080/api-admin/v1/data-generator/stats
```

### 검증

```sql
-- 전체 vs 활성 비율
SELECT COUNT(*) FROM brands;                          -- 100
SELECT COUNT(*) FROM brands WHERE deleted_at IS NULL; -- 90
SELECT COUNT(*) FROM products;                         -- 100,000
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL; -- ~85,000
SELECT COUNT(*) FROM products WHERE deleted_at IS NOT NULL AND like_count > 0; -- ~2,000 (Wave 2)

-- 브랜드별 상품 수 분포 확인
SELECT b.name, COUNT(p.id) as cnt
FROM brands b JOIN products p ON b.id = p.brand_id
WHERE b.deleted_at IS NULL AND p.deleted_at IS NULL
GROUP BY b.id ORDER BY cnt DESC LIMIT 5;
```
