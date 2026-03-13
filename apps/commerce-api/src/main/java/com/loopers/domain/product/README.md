# Product / Brand 도메인

> Claude Code 작업 시 이 도메인의 설계 의도와 규칙을 참고하세요.

## 책임

- **Product**: 상품 정보, 재고 관리. `decreaseStock()`으로 주문 시 재고 차감.
- **Brand**: 브랜드 정보. Product가 `brandId`로 참조.

## 설계 규칙

1. **Product는 likeCount를 직접 가지지 않음**  
   좋아요 수는 Like 도메인에서 집계하며, 조회 시점에 Application Layer에서 조합.

2. **재고 차감은 도메인 레벨에서 처리**  
   `Product.decreaseStock(quantity)` 내부에서 음수 방지.  
   재고 부족 시 `CoreException(ErrorType.INSUFFICIENT_STOCK)` 발생.

3. **행위 메서드 사용**  
   Setter 금지. `decreaseStock()` 등 의도가 드러나는 메서드 사용.

4. **정렬 조건 (SortCondition)**  
   `latest`, `price_asc`, `likes_desc` — Infrastructure에서 구현.

## 주요 클래스

| 클래스 | 역할 |
|--------|------|
| Product | 상품 엔티티, `decreaseStock()` |
| Brand | 브랜드 엔티티 |
| ProductRepository | 상품 저장/조회 인터페이스 |
| BrandRepository | 브랜드 저장/조회 인터페이스 |
| SortCondition | 정렬 조건 enum |

## 참조

- [CLAUDE.md](/CLAUDE.md) — 프로젝트 루트의 전체 아키텍처 규칙
