# 문서 관리

## 구조
```
docs/
├── requirements/                   # 요구사항 정의서 (Epic 단위, 팀 공통 언어)
│   └── {epic-name}.md
├── specs/                          # 기능 명세서 (Feature 단위, 구현 규격)
│   └── {epic-name}/
│       └── {NNN-feature-name}.md
└── design/                         # 설계 문서 (Feature 단위, 다이어그램)
    └── {epic-name}/
        ├── erd.md                          # Epic 단위 (테이블 전체)
        ├── class-diagram.md
        └── {NNN-feature-name}/
            └── sequence.md             # Feature 단위 (흐름만)
```

## 흐름
requirements → specs → design → 구현

## 규칙
- 기능 구현 전 해당 기능의 specs가 있으면 먼저 읽고 AC 기반으로 구현할 것
- specs의 AC와 테스트는 1:1 매핑

## 참고 스킬
- 요구사항 정의서 작성 → requirements-writer
- 기능 명세서 작성 → spec-writer
- 설계 문서 작성 → design-writer