# 테스트코드 작성 (실행 없음)

대상 기능/파일: $ARGUMENTS

모델 고정: `openai/gpt-5.3-codex-spark`

이 프로젝트 규칙에 맞춰 테스트코드만 작성해주세요. 테스트 실행은 하지 않습니다.

## 반드시 따를 규칙
1. `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/testing.md` 우선 적용
2. 도메인별 레시피 적용:
   - `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/testing-recipes/README.md`
   - 관련 도메인 레시피(`user.md`, `order.md`, `product-like.md`) 선택 적용
3. 상태 검증 우선, 단위 테스트는 classist(mock/stub only)
4. 레이어 정책 준수:
   - Domain/Domain Service: 단위 테스트
   - Application Service/Controller: 통합 테스트 코드 형태

## 작성 절차
1. 기존 테스트 패턴과 네이밍을 먼저 분석
2. 대상 코드의 happy/unhappy/boundary 케이스 도출
3. 필수 회귀 항목 반영:
   - toString 마스킹
   - raw/encoded 분리
   - 저장 시점 중복키 예외
   - 이름 마스킹 1/2/3글자
4. 테스트 파일 생성/수정
5. 변경 요약 출력 (무엇을 왜 추가했는지)

## 출력
- 수정된 테스트 파일 경로 목록
- 각 테스트의 의도(1줄)
- 남은 테스트 갭(있으면)
