# Commerce API 문서

Claude Code / AI 어시스턴트 작업 시 참고할 도메인별 문서입니다.

## 문서 위치

각 구현체(도메인, Application Layer) 폴더에 README.md가 있습니다.

| 영역 | 경로 |
|------|------|
| Product/Brand 도메인 | `src/main/java/com/loopers/domain/product/README.md` |
| Like 도메인 | `src/main/java/com/loopers/domain/like/README.md` |
| Order 도메인 | `src/main/java/com/loopers/domain/order/README.md` |
| Product Application | `src/main/java/com/loopers/application/product/README.md` |
| Like Application | `src/main/java/com/loopers/application/like/README.md` |
| Order Application | `src/main/java/com/loopers/application/order/README.md` |

## Cursor Rules

`.cursor/rules/`에 도메인별 규칙이 등록되어 있습니다. 해당 경로의 파일을 편집할 때 자동으로 적용됩니다.

- `domain-product.mdc` — Product, Brand 관련
- `domain-like.mdc` — Like 관련
- `domain-order.mdc` — Order 관련

## 전체 아키텍처

프로젝트 루트의 `CLAUDE.md`를 참고하세요.
