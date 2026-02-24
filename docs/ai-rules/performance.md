# 성능 최적화 기준

## 모델 선택

| 작업 유형 | 기본 모델 ID | 사용 기준 |
|----------|--------------|----------|
| 기본 코드 / 테스트코드 / 리팩터링 수행 등 간단한 작업 | `gpt-5.3-codex-spark` | 기본값 (토큰 비용 절약 우선) |
| 복잡 디버깅 / 설계 의사 결정 | `GPT-5.1-Codex-Max` | 난이도 높거나 판단 비용이 큰 경우만 |

### 실행 규칙
- 기본은 항상 `gpt-5.3-codex-spark`로 시작한다.
- 작업 중 복잡도가 높아질 때만 `GPT-5.1-Codex-Max`로 승급한다.
- 승급 작업 완료 후 후속 구현/리팩터링은 다시 `gpt-5.3-codex-spark`로 복귀한다.

### 호출 예시 (Codex / OhMyOpenCode / OpenCode 인식용)

```md
# Codex MCP - 기본
mcp__codex__codex(
  prompt: "구현 내용",
  model: "gpt-5.3-codex-spark"
)

# Codex MCP - 복잡 디버깅 / 설계
mcp__codex__codex(
  prompt: "복잡 이슈 분석",
  model: "gpt-5.1-codex-max"
)

# OpenCode / OhMyOpenCode 설정 예시
model = "gpt-5.3-codex-spark"
# 필요 시 일시 승급
# model = "gpt-5.1-codex-max"
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
