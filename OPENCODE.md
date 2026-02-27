# OpenCode Project Rules

- Controller 조합/오케스트레이션 로직 금지 (Controller는 요청/응답 변환만 수행)
- 여러 도메인 데이터 결합/매핑은 Facade 또는 Service에서 처리
- Application Service private 메서드 금지
- Domain Service private 메서드 금지
- Application/Domain Service 내부 메서드 간 직접 호출 금지

- 상세 규칙은 `AGENTS.md`, `docs/ai-rules/coding-style.md`를 우선 참조
