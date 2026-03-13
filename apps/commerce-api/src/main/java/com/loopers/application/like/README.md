# Like Application Layer

> Claude Code 작업 시 이 영역의 설계 의도와 규칙을 참고하세요.

## 책임

- **LikeService**: 좋아요 등록/취소. 트랜잭션 관리, 도메인에 위임.

## 설계 규칙

1. **Service = 트랜잭션 + 흐름 제어**  
   비즈니스 로직은 Like, Product 도메인에 위임.

2. **멱등성**  
   `like()` — 이미 존재하면 무시. `unlike()` — 없어도 예외 없음.

3. **Product 존재 검증**  
   좋아요 등록 전 `productRepository.findById()`로 상품 존재 확인.

## 주요 클래스

| 클래스 | 역할 |
|--------|------|
| LikeService | like(), unlike() |

## 참조

- [domain/like README](../../domain/like/README.md) — Like 도메인 규칙
- [CLAUDE.md](/CLAUDE.md) — 전체 아키텍처 규칙
