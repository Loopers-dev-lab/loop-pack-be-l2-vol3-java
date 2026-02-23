# 성능 최적화 기준

## 모델 선택

| 작업 유형 | 모델 | 호출 방법 |
|----------|------|----------|
| 최상단 설계 (아키텍처, 핵심 의사결정 등 복잡하지만 작성량이 적은 작업) | `claude-opus-4-6` | `Task(model="opus", ...)` |
| 세부 계획 (요구사항 명세 도출, 관련 문서 작성 등) 및 디버깅 | `claude-sonnet-4-6` | `Task(model="sonnet", ...)` |
| 코드 작성 | Codex | `mcp__codex__codex(prompt: "...")` |
| 단순 반복/대량 생성 | Gemini | `mcp__gemini__gemini_cli(prompt: "...")` |

### 호출 예시

```
# 아키텍처 설계
Task(subagent_type="Plan", model="opus", prompt="아키텍처 설계")

# 디버깅 / 세부 계획
Task(subagent_type="general-purpose", model="sonnet", prompt="디버깅")

# 코드 작성 (Codex MCP) - Java/Spring 스타일 주입 필수
mcp__codex__codex(
  prompt: "구현 내용",
  developer-instructions: """
    - final 우선, null 반환 금지 → Optional/빈 컬렉션 반환
    - Optional은 반환 타입으로만 사용 (파라미터/필드 금지)
    - 레이어: Facade -> Application Service -> Domain Service
    - @Transactional은 Application Service에만, Facade/Domain Service 금지
    - Domain Service는 순수 규칙만 담당 (저장/외부 I/O 금지)
    - 코드 뎁스 1 제한, unused import 제거
    - System.out.println 금지
  """,
  sandbox: "workspace-write"
)

# 단순 반복 / 대량 생성 (Gemini MCP)
mcp__gemini__gemini_cli(prompt: "작업 내용", model: "gemini-2.5-pro")
```

## 코드 성능
- N+1 쿼리 방지 (ORM 사용 시 특히 주의)
- 불필요한 리렌더링 방지 (React: useMemo, useCallback 적절히 사용)
- 대용량 데이터는 페이지네이션/스트리밍 처리
- 이미지는 lazy loading 적용
- 번들 사이즈 최적화 (코드 스플리팅, 트리 쉐이킹)

## 컨텍스트 최적화
- 동시에 80개 미만의 도구만 유지
- MCP 서버는 프로젝트별 5~6개만 활성화
- 사용하지 않는 MCP 서버는 `disabledMcpServers`에 명시적 비활성화
