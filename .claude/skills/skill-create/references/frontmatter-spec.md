# 프론트매터 스펙

SKILL.md 상단 `---` 마커 사이에 작성하는 YAML 메타데이터입니다.

## 필수 필드

### name
- **규칙**: 케밥케이스만 허용 (소문자 + 하이픈)
- **최대**: 64자
- **금지**: 공백, 언더스코어, 대문자, "claude"/"anthropic" 접두사
- **폴더명과 일치 필수**

```yaml
# 올바름
name: code-review
name: sprint-planner
name: mcp-linear-sync

# 잘못됨
name: Code Review        # 대문자, 공백
name: code_review        # 언더스코어
name: claude-helper      # "claude" 접두사
```

### description
- **최대**: 1024자
- **금지**: XML 태그 (`<` `>`)
- **필수 포함**: 무엇을 하는지(WHAT) + 언제 사용하는지(WHEN)
- 사용자가 실제로 말할 트리거 문구를 포함

## 선택 필드

### disable-model-invocation
- `true` 설정 시 Claude가 자동으로 이 스킬을 호출하지 않음
- `/skill-name`으로 사용자만 수동 호출 가능
- **사용 사례**: deploy, commit 등 부작용이 있는 작업

### user-invocable
- `false` 설정 시 `/` 메뉴에서 숨김
- Claude만 자동으로 호출 가능
- **사용 사례**: 배경 지식, 컨텍스트 제공용 스킬

### allowed-tools
- 스킬 활성화 시 Claude가 권한 요청 없이 사용 가능한 도구 목록
- 예: `Read, Grep, Glob` (읽기 전용), `Bash(python:*)` (Python 실행)

### context
- `fork` 설정 시 격리된 서브에이전트에서 실행
- 대화 기록에 접근 불가
- **주의**: 명시적 작업 지침이 없으면 의미 없는 출력 반환

### agent
- `context: fork` 시 사용할 서브에이전트 유형
- 기본 제공: `Explore`, `Plan`, `general-purpose`
- `.claude/agents/`의 커스텀 에이전트도 지정 가능
- 생략 시 `general-purpose` 사용

### model
- 스킬 활성화 시 사용할 모델 지정

### argument-hint
- 자동완성 시 표시되는 인수 힌트
- 예: `[issue-number]`, `[filename] [format]`

### license
- 오픈소스 공개 시 사용
- 예: `MIT`, `Apache-2.0`

### metadata
- 커스텀 키-값 쌍
```yaml
metadata:
  author: your-name
  version: 1.0.0
  mcp-server: linear
```

## 보안 제한

프론트매터는 Claude의 시스템 프롬프트에 노출됩니다.

- XML 꺾쇠 괄호 (`<` `>`) 금지 → 명령어 주입 방지
- "claude"/"anthropic" 이름 사용 금지 → 예약어
- YAML 내 코드 실행 불가 → 안전한 YAML 파싱 적용

## 검증 체크리스트

- [ ] `---` 구분자로 시작하고 끝나는가?
- [ ] name이 케밥케이스인가?
- [ ] name이 폴더명과 일치하는가?
- [ ] name에 "claude"/"anthropic"이 없는가?
- [ ] description이 1024자 미만인가?
- [ ] description에 WHAT + WHEN이 있는가?
- [ ] XML 태그가 없는가?
- [ ] 따옴표가 올바르게 닫혔는가?
