# Claude AI 개발 가이드

이 디렉토리는 Claude AI를 활용한 개발을 위한 프로젝트별 가이드라인과 컨텍스트를 포함합니다.

## 📁 디렉토리 구조

```
.claude/
├── README.md                           # 이 파일
├── project_context.md                  # 프로젝트 개요 및 기술 스택
└── skills/                             # 세부 스킬별 가이드라인
    ├── coding_standards.md             # 코딩 표준 및 컨벤션
    ├── testing_guidelines.md           # 테스트 전략 및 작성법
    ├── architecture_patterns.md        # 아키텍처 패턴 및 레이어 구조
    └── development_workflow.md         # 개발 워크플로우 (TDD)
```

## 📚 문서 가이드

### 1. project_context.md
**프로젝트의 전체적인 컨텍스트를 제공합니다.**

- 프로젝트 개요 및 핵심 특징
- 기술 스택 (Java 21, Spring Boot 3.4.4, etc.)
- 모듈 구조 (apps, modules, supports)
- 환경 설정 및 빌드 방법
- 주요 도메인 소개

**언제 참고하나요?**
- 프로젝트를 처음 접할 때
- 기술 스택을 확인할 때
- 빌드 및 실행 방법을 알고 싶을 때

### 2. skills/coding_standards.md
**코드 작성 시 준수해야 할 표준과 컨벤션을 정의합니다.**

- 네이밍 규칙 (Entity, Service, Repository, Controller, DTO, etc.)
- 타입 사용 규칙 (Value Object, DTO, Entity)
- 의존성 주입 방법
- 예외 처리 전략
- API 응답 구조
- JPA 관련 규칙
- 트랜잭션 관리
- Null Safety

**언제 참고하나요?**
- 새로운 클래스를 만들 때
- 네이밍이 고민될 때
- 예외 처리 방법을 결정할 때
- JPA Converter를 작성할 때

### 3. skills/testing_guidelines.md
**테스트 작성 전략과 구체적인 방법을 제시합니다.**

- 테스트 전략 (테스트 피라미드)
- 단위 테스트 작성법
- 통합 테스트 작성법
- E2E 테스트 작성법
- 테스트 데이터 생성
- Mock 사용법
- Assertion 라이브러리 활용

**언제 참고하나요?**
- 테스트 코드를 작성할 때
- Spy 설정이 필요할 때
- TestContainers를 사용할 때
- 테스트 격리가 필요할 때

### 4. skills/architecture_patterns.md
**프로젝트의 아키텍처 패턴과 레이어 구조를 설명합니다.**

- 레이어드 아키텍처 전체 구조
- Domain Layer (Model, Service, Repository, Value Object)
- Application Layer (Facade, Info)
- Interfaces Layer (Controller, ApiSpec, Dto)
- Infrastructure Layer (RepositoryImpl, JpaRepository, Converter)
- 패키지 구조
- 레이어 간 데이터 흐름
- 설계 원칙 (SOLID)

**언제 참고하나요?**
- 새로운 기능을 설계할 때
- 레이어 분리가 고민될 때
- 의존성 방향을 확인할 때
- 패키지 구조를 결정할 때

### 5. skills/development_workflow.md
**개발 프로세스와 TDD 워크플로우를 안내합니다.**

- 증강 코딩 원칙
- TDD 워크플로우 (Red > Green > Refactor)
- Red Phase: 실패하는 테스트 작성
- Green Phase: 테스트 통과 코드 작성
- Refactor Phase: 코드 품질 개선
- 주의사항 (Never Do, Recommendation, Priority)
- 코드 리뷰 체크리스트
- Git Workflow

**언제 참고하나요?**
- 새로운 기능 개발을 시작할 때
- TDD 프로세스를 따를 때
- 리팩토링을 진행할 때
- 코드 리뷰를 할 때

## 🎯 빠른 시작 가이드

### 처음 프로젝트를 접하는 경우
1. **project_context.md** 읽기 → 프로젝트 전체 이해
2. **architecture_patterns.md** 읽기 → 아키텍처 구조 파악
3. **coding_standards.md** 읽기 → 코딩 규칙 숙지

### 기능 개발을 시작하는 경우
1. **development_workflow.md** 읽기 → TDD 프로세스 확인
2. **testing_guidelines.md** 참고 → 테스트 작성
3. **coding_standards.md** 참고 → 코드 작성

### 특정 문제를 해결하는 경우
- 네이밍 고민 → **coding_standards.md** > 네이밍 규칙
- 테스트 작성 → **testing_guidelines.md** > 해당 테스트 레벨
- 레이어 분리 → **architecture_patterns.md** > 레이어별 책임
- 예외 처리 → **coding_standards.md** > 예외 처리

## 📖 추가 참고 문서

### 프로젝트 루트의 문서들
- **CLAUDE.md**: 전체 개발 가이드 (통합 문서)
- **README.md**: 프로젝트 소개 및 시작 가이드
- **.codeguide/loopers-1-week.md**: 1주차 구현 퀘스트

### 온라인 문서
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Spring Boot 공식 문서**: https://spring.io/projects/spring-boot
- **Spring Data JPA 공식 문서**: https://spring.io/projects/spring-data-jpa

## 💡 사용 팁

### Claude AI와 협업 시
1. **명확한 요구사항 전달**: 구현할 기능을 구체적으로 설명
2. **가이드라인 참조 요청**: "coding_standards.md를 참고해서 작성해줘"
3. **단계별 진행**: TDD 프로세스를 단계별로 진행
4. **중간 확인**: 각 단계마다 결과를 확인하고 피드백

### 예시 프롬프트
```
회원 포인트 조회 기능을 구현해줘.
- development_workflow.md의 TDD 프로세스를 따라서
- 먼저 실패하는 테스트를 작성하고
- coding_standards.md의 네이밍 규칙을 준수해서
- architecture_patterns.md의 레이어 구조에 맞게 작성해줘
```

## 🔄 문서 업데이트

### 언제 업데이트하나요?
- 새로운 패턴이나 규칙이 추가될 때
- 기존 규칙이 변경될 때
- 프로젝트 구조가 변경될 때
- 새로운 기술 스택이 도입될 때

### 어떻게 업데이트하나요?
1. 해당 문서 파일을 직접 수정
2. 변경 사항을 팀과 공유
3. CLAUDE.md도 함께 업데이트 (일관성 유지)

## 📝 문서 작성 원칙

### 명확성
- 구체적인 예시 코드 포함
- 추상적인 설명보다 실제 사례 중심

### 일관성
- 모든 문서에서 동일한 용어 사용
- 코드 스타일 통일

### 실용성
- 실제 프로젝트 코드 기반
- 바로 적용 가능한 가이드

### 최신성
- 프로젝트 변경 사항 즉시 반영
- 정기적인 리뷰 및 업데이트

## 🤝 기여 가이드

### 문서 개선 제안
1. 불명확한 부분 발견 시 이슈 등록
2. 개선 사항 제안
3. 예시 코드 추가 제안

### 새로운 가이드 추가
1. `skills/` 디렉토리에 새 파일 생성
2. 이 README.md에 문서 설명 추가
3. CLAUDE.md에도 관련 내용 추가

## 📞 문의

프로젝트 관련 문의사항이나 가이드라인에 대한 질문은 팀 채널을 통해 공유해주세요.

---

**Last Updated**: 2026-02-03
**Version**: 1.0.0
