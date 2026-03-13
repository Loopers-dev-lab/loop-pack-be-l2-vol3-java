# Like 도메인

> Claude Code 작업 시 이 도메인의 설계 의도와 규칙을 참고하세요.

## 책임

- **Like**: 회원(Member)과 상품(Product) 간의 좋아요 관계.
- 별도 도메인으로 분리하여 Product가 좋아요 수를 직접 관리하지 않음.

## 설계 규칙

1. **(member_id, product_id) UNIQUE**  
   한 회원이 한 상품에 한 번만 좋아요 가능.

2. **좋아요 수 집계**  
   `LikeRepository.countByProductId()`, `countByProductIds()` — 조회 시점에 집계.

3. **Product와 분리**  
   Product 엔티티에 likeCount 필드 없음. Application Layer에서 조합.

## 주요 클래스

| 클래스 | 역할 |
|--------|------|
| Like | 좋아요 엔티티 (memberId, productId) |
| LikeRepository | 저장, 삭제, 존재 여부, 집계 |

## API 흐름

- **등록**: `LikeService.like()` → 중복 시 멱등, Product 존재 검증 후 저장
- **취소**: `LikeService.unlike()` → `deleteByMemberIdAndProductId()`

## 참조

- [CLAUDE.md](/CLAUDE.md) — 프로젝트 루트의 전체 아키텍처 규칙
