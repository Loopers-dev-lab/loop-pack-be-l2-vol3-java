# Architecture Patterns

## Hexagonal Architecture 계층 구조

```
apps/commerce-api/src/main/java/com/loopers/
├── interfaces/api/               # Interface Layer (Driving Adapters)
│   ├── ApiResponse.java          # 표준 응답 래퍼 (meta + data)
│   ├── ApiControllerAdvice.java  # 전역 예외 핸들러
│   └── {domain}/
│       ├── {Domain}ApiSpec.java  # Swagger 인터페이스 (API 계약)
│       ├── {Domain}Controller.java # REST 컨트롤러 (implements ApiSpec)
│       ├── {Domain}Request.java  # 중첩 record 요청 DTO
│       └── {Domain}Response.java # 중첩 record 응답 DTO
├── application/                  # Application Layer
│   └── {domain}/
│       └── {Domain}Info.java     # Application DTO (Entity -> 외부 계층 변환)
├── domain/                       # Domain Layer (Core)
│   └── {domain}/
│       ├── {Domain}.java         # Entity (extends BaseEntity, static create() 팩토리)
│       ├── {Domain}Service.java  # 도메인 서비스 (@Component)
│       ├── {Domain}Repository.java # 리포지토리 포트 (interface)
│       └── vo/
│           └── {ValueObject}.java # @Embeddable, 생성자 검증
├── infrastructure/               # Infrastructure Layer (Driven Adapters)
│   └── {domain}/
│       ├── {Domain}RepositoryImpl.java  # 포트 구현 (@Repository)
│       └── {Domain}JpaRepository.java   # Spring Data JPA 인터페이스
└── support/
    └── error/
        ├── ErrorType.java               # 에러 타입 인터페이스
        ├── CoreException.java           # 비즈니스 예외
        ├── CommonErrorType.java         # 공통 에러 열거형
        └── {Domain}ErrorType.java       # 도메인별 에러 열거형
```

## 계층별 책임

### Interface Layer (interfaces/api/)
- HTTP 관심사: 라우팅, 상태코드, 파라미터 바인딩
- Request DTO -> 도메인 파라미터 변환
- Application DTO(Info) -> Response DTO 변환
- ApiResponse 래핑
- ApiSpec 인터페이스로 Swagger 어노테이션 분리

### Application Layer (application/)
- Entity -> Info DTO 변환 (마스킹 등 포함)
- 여러 도메인 서비스 오케스트레이션이 필요하면 Facade 도입

### Domain Layer (domain/)
- 비즈니스 로직의 핵심
- Entity: BaseEntity 상속, static create() 팩토리, JPA protected 기본 생성자
- Value Object: @Embeddable, 생성자에서 자체 검증
- Service: @Component, 도메인 핵심 로직, Repository 포트 의존
- Repository: interface (포트). 인프라에 의존하지 않음

### Infrastructure Layer (infrastructure/)
- 도메인 포트(Repository interface)의 구현체
- Spring Data JPA에 위임
- @Repository 어노테이션으로 예외 변환

## Facade 도입 기준

| 조건 | Facade 필요 여부 |
|------|-----------------|
| 단일 Service 호출 | 불필요 (Controller -> Service 직접) |
| 2개 이상 Service 조합 | 필요 |
| 트랜잭션 경계가 Service와 다를 때 | 필요 |

## 새 도메인 추가 시 생성 파일 체크리스트

1. `domain/{domain}/{Domain}.java` - Entity
2. `domain/{domain}/{Domain}Service.java` - 도메인 서비스
3. `domain/{domain}/{Domain}Repository.java` - 리포지토리 포트
4. `domain/{domain}/vo/` - Value Objects
5. `infrastructure/{domain}/{Domain}RepositoryImpl.java` - 포트 구현
6. `infrastructure/{domain}/{Domain}JpaRepository.java` - JPA 인터페이스
7. `application/{domain}/{Domain}Info.java` - Application DTO
8. `interfaces/api/{domain}/{Domain}Controller.java` - 컨트롤러
9. `interfaces/api/{domain}/{Domain}ApiSpec.java` - Swagger 스펙
10. `interfaces/api/{domain}/{Domain}Request.java` - 요청 DTO
11. `interfaces/api/{domain}/{Domain}Response.java` - 응답 DTO
12. `support/error/{Domain}ErrorType.java` - 에러 타입