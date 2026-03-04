# 구현 워크플로우

## 원칙
- 기능 구현 전 해당 기능의 requirements, spec, design이 있으면 반드시 먼저 읽는다
- spec의 AC와 테스트는 1:1 매핑한다
- 구현 순서: domain → infrastructure → application → interfaces
- 테스트 순서: 단위 → 통합 → E2E
- 컴파일 + 테스트 통과 후 AC 매핑 보고로 마무리한다
- 검증과 커밋은 사용자가 수행한다

## 참고 스킬
- spec 기반 기능 구현 → implement
