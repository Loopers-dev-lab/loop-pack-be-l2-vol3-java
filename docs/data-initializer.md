# 성능 테스트용 더미 데이터 구성

## 상품 공통 스펙

| 항목 | 값 |
|------|----|
| price | 1,000 ~ 100,000원 (브랜드 무관, 무작위) |
| stockQuantity | 100 |
| visibility | VISIBLE |

## 브랜드 그룹

| 그룹 | 브랜드 | 브랜드당 상품 수 | 소계 |
|------|--------|----------------|------|
| TOP  | 브랜드-1 ~ 브랜드-10  | 10,000 | 100,000 |
| REST | 브랜드-11 ~ 브랜드-50 | 2,500  | 100,000 |
| **합계** | | | **200,000** |

## likeCount 분포

TOP 브랜드 상품은 상대적으로 높은 likeCount를 갖도록 그룹별 범위를 다르게 설정했다.
각 상품의 likeCount는 해당 범위 내에서 무작위로 설정된다.

| 그룹 | likeCount 범위 |
|------|----------------|
| TOP  | 500 ~ 3,000    |
| REST | 0 ~ 2,000      |

## 검증 쿼리

```sql
-- 전체 상품 수 확인
SELECT COUNT(*) FROM products;

-- 브랜드별 상품 수 확인 (TOP 10개 → 10,000 / REST 40개 → 2,500)
SELECT brand_id, COUNT(*) FROM products GROUP BY brand_id ORDER BY brand_id;

-- likeCount 분포 확인
SELECT brand_id, MIN(like_count), MAX(like_count), AVG(like_count)
FROM products
GROUP BY brand_id
ORDER BY brand_id;
```

## 브랜드별 상품 수 조정 (선택)

초기 적재 후 특정 브랜드에 상품을 몰아주고 싶다면 직접 SQL로 조정한다.

```sql
-- 예: 브랜드-2, 3, 4의 상품을 브랜드-1로 이전
UPDATE products SET brand_id = 1 WHERE brand_id IN (2, 3, 4);
```
