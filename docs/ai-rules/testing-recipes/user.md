# User Domain Recipe

## Scope
- 회원가입, 인증, 내 정보 조회, 비밀번호 변경

## Mandatory Cases
- 회원가입 성공
- 중복 아이디 충돌(사전 중복 + 저장 시점 중복키 예외)
- 비밀번호 정책 검증 (길이/문자조합/생년월일 포함 금지)
- raw/encoded 경로 분리 검증
- 비밀번호 변경 성공/동일 비밀번호 실패/사용자 없음
- 이름 마스킹 경계값(1글자, 2글자, 3글자)
- 인증 실패(헤더 누락/비밀번호 불일치)

## Assertions
- 상태 검증 우선 (응답 코드, 저장 결과, 변경된 필드)
- 협력 호출 검증은 필요 최소(예: save 호출 유무)

## Data Guidance
- encoded password fixture는 BCrypt prefix 포함 (`$2a$`, `$2b$`, `$2y$`)
- 민감정보가 `toString()`에 노출되지 않는지 확인
