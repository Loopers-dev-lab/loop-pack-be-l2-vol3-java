# ADR: 유저 상품 목록 조회 페이징 방식 비교

- 상태: Proposed
- 날짜: 2026-03-13
- 작성자: OpenCode
- 관련 모듈: `apps/commerce-api`, `docker/mysql`

## 맥락

인덱스 실험 결과, 유저 상품 목록 조회에서 `brand + likes DESC`는 우선순위 0인 대표 쿼리로 확인됐다.

```sql
SELECT *
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = ?
ORDER BY p.like_count DESC, p.id DESC
```

이 경로에 대해 offset paging과 cursor/seek paging 중 어떤 방식이 더 적합한지 확인할 필요가 있었다.

## 실험 범위

- 포함
  - `brand + likes DESC` 단일 정렬 기준
  - offset paging vs cursor/seek paging 비교
  - 첫 페이지 / 중간 페이지 / 마지막 근처 페이지 비교
- 제외
  - 다른 정렬(latest, price) 비교
  - API 구현 변경 후 end-to-end 성능 측정

## 고정 조건

```sql
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
ORDER BY p.like_count DESC, p.id DESC
```

## 비교 페이지

1. 첫 페이지
2. 중간 페이지
3. 마지막 근처 페이지

실험에 사용한 offset 값은 아래와 같다.

- 첫 페이지: `OFFSET 0 LIMIT 20`
- 중간 페이지: `OFFSET 1000 LIMIT 20`
- 마지막 근처 페이지: `OFFSET 10000 LIMIT 20`

실험 대상 브랜드의 활성 상품 수는 `11,825`건이다.

## cursor 기준값

- middle cursor: `like_count = 719`, `id = 181129`
- last-ish cursor: `like_count = 66`, `id = 55335`

## 측정 방식

- DB: Podman `loopers-mysql`, MySQL 8.0
- 각 케이스마다 `EXPLAIN ANALYZE` 5회 실행
- 1회차는 워밍업으로 제외
- 2~5회차 median 기준으로 비교

## 측정 SQL

### offset paging

```sql
EXPLAIN ANALYZE
SELECT *
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 0;

EXPLAIN ANALYZE
SELECT *
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 1000;

EXPLAIN ANALYZE
SELECT *
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20 OFFSET 10000;
```

### cursor paging

첫 페이지:

```sql
EXPLAIN ANALYZE
SELECT *
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20;
```

중간/마지막 근처 페이지 기준 row 추출:

```sql
SELECT p.like_count, p.id
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
ORDER BY p.like_count DESC, p.id DESC
LIMIT 1 OFFSET 999;

SELECT p.like_count, p.id
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
ORDER BY p.like_count DESC, p.id DESC
LIMIT 1 OFFSET 9999;
```

중간 페이지:

```sql
EXPLAIN ANALYZE
SELECT *
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
  AND (
    p.like_count < 719
    OR (p.like_count = 719 AND p.id < 181129)
  )
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20;
```

마지막 근처 페이지:

```sql
EXPLAIN ANALYZE
SELECT *
FROM products p
WHERE p.deleted_at IS NULL
  AND p.brand_reference_id = UUID_TO_BIN('7155ede0-55b8-57eb-85ae-120633a7c73c')
  AND (
    p.like_count < 66
    OR (p.like_count = 66 AND p.id < 55335)
  )
ORDER BY p.like_count DESC, p.id DESC
LIMIT 20;
```

## 결과

| 방식 | 페이지 위치 | access | filesort | median actual time(ms) | min(ms) | max(ms) | rows |
|---|---|---|---|---:|---:|---:|---:|
| offset | first | index-lookup | Y | 11.40 | 10.80 | 11.60 | 20 |
| offset | middle | index-lookup | Y | 12.60 | 12.20 | 12.90 | 20 |
| offset | last-ish | index-lookup | Y | 14.05 | 13.50 | 14.40 | 20 |
| cursor | first | index-lookup | Y | 11.05 | 10.80 | 11.80 | 20 |
| cursor | middle | index-lookup | Y | 11.80 | 11.60 | 12.20 | 20 |
| cursor | last-ish | other | Y | 2.33 | 2.15 | 2.56 | 20 |

## 해석

1. 첫 페이지에서는 offset과 cursor 차이가 거의 없다.
   - `11.40ms` vs `11.05ms`
2. 중간 페이지에서는 cursor가 소폭 더 빠르다.
   - `12.60ms` vs `11.80ms`
3. 마지막 근처 페이지에서는 cursor가 확실히 빠르다.
   - `14.05ms` vs `2.33ms`
4. 현재 정렬이 `like_count DESC, id DESC`라 filesort는 남지만, 깊은 페이지로 갈수록 cursor predicate가 후보 row를 강하게 줄여 이점이 커진다.

## 최종 결정

유저 상품 목록 조회 중 `brand + likes DESC` 경로는 cursor paging을 채택한다.

### 이유

- 첫 페이지 성능은 offset과 거의 차이 없다.
- 중간 이후 페이지부터 cursor가 점진적으로 유리하다.
- 마지막 근처 페이지에서는 cursor 이점이 명확하다.
- 무한 스크롤이나 연속 조회 UX와도 잘 맞는다.

## 후속 작업

1. public product list API에서 `likes` 정렬 시 cursor paging을 지원한다.
2. offset 응답 메타데이터와 cursor 응답 메타데이터를 분리한다.
3. 클라이언트가 `nextCursor`를 이용해 후속 페이지를 요청할 수 있게 계약을 정리한다.
