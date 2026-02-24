# 테스트 통과 구현코드 작성

대상 테스트/기능: $ARGUMENTS

모델 고정: `openai/gpt-5.3-codex-spark`

이미 존재하는 테스트 또는 방금 작성한 테스트를 통과하도록 구현코드를 작성해주세요.

## 반드시 따를 규칙
1. 아키텍처/코딩 규칙 적용:
   - `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/AGENTS.md`
   - `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/coding-style.md`
2. 테스트 정책 적용:
   - `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/testing.md`
3. 오버엔지니어링 금지, 최소 변경으로 통과
4. 민감정보 마스킹/인증 단일화/Locale.ROOT/중복키 409 변환 규칙 준수

## 작업 절차
1. 실패 테스트 기준으로 필요한 구현 범위만 식별
2. 구현 코드 수정
3. 필요한 경우 테스트 fixture만 최소 보정
4. 관련 테스트만 우선 실행
5. 통과 확인 후 변경 요약

## 출력
- 수정 파일 목록
- 테스트 통과 결과 요약
- 남은 리스크/추가 필요 테스트
