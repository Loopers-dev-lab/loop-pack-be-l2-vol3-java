# Product Application Layer

> Claude Code 작업 시 이 영역의 설계 의도와 규칙을 참고하세요.

## 책임

- **ProductFacade**: 상태 변경 없이 Product + Brand + Like를 조회·조합하여 반환.
- **ProductDetailInfo, ProductListInfo**: Application DTO (API DTO와 분리).

## 설계 규칙

1. **Facade = 조합 전용**  
   비즈니스 로직 없음. Domain Repository에서 조회 후 DTO로 변환.

2. **상품 상세 조합**  
   `getProductDetail(productId)` → Product + Brand + likeCount 조합.

3. **상품 목록 조합**  
   `getProductList(sort)` → ProductRepository.findAll(sort) + Brand 맵 + Like 집계 맵 → ProductListInfo 리스트.

4. **정렬**  
   `latest`, `price_asc`, `likes_desc` — ProductRepository에 위임.

## 주요 클래스

| 클래스 | 역할 |
|--------|------|
| ProductFacade | getProductDetail, getProductList |
| ProductDetailInfo | 상세 조회용 DTO |
| ProductListInfo | 목록 조회용 DTO |

## 참조

- [domain/product README](../../domain/product/README.md) — Product 도메인 규칙
- [CLAUDE.md](/CLAUDE.md) — 전체 아키텍처 규칙
