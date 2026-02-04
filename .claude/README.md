# Claude AI 협업 가이드

이 디렉토리는 Claude Code와의 협업을 위한 규칙과 컨텍스트를 포함합니다.

## 핵심 원칙

### Claude 역할 제한
- **허용**: 제안, 대안 제시, 승인된 범위 내 구현
- **금지**: 임의 설계 결정, 요구사항 확장, 테스트 삭제/약화

### TDD Workflow (필수)
1. 🔴 **Red**: 실패하는 테스트 먼저 작성 (로직 구현 금지)
2. 🟢 **Green**: 테스트 통과하는 최소 코드만 작성
3. 🔵 **Refactor**: 동작 변경 없이 품질 개선 (새 기능 추가 금지)

## 참고 문서

| 문서 | 내용 |
|------|------|
| **CLAUDE.md** | 전체 개발 가이드 (기술 스택, 아키텍처, 컨벤션, 테스트 전략) |
| **.codeguide/loopers-1-week.md** | Week 1 구현 요구사항 |

## Week 1 범위 (Member 도메인)

- 회원가입 (`POST /api/v1/members/register`)
- 내 정보 조회 (`GET /api/v1/members/me`)
- 비밀번호 수정 (`PATCH /api/v1/members/me/password`)

## Never Do

- ❌ 테스트 삭제, @Disabled, assertion 약화
- ❌ 승인 없이 요구사항 확장/범위 초과
- ❌ Mock 데이터로만 동작하는 구현
- ❌ System.out.println 코드

## Priority

1. 실제 동작하는 코드
2. null-safety, thread-safety
3. 테스트 가능한 구조
4. 기존 패턴과 일관성 유지

---

**Last Updated**: 2026-02-04