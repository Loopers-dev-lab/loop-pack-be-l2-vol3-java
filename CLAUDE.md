# Commerce API

## 프로젝트
- Java 21+ / Spring Boot 커머스 API (Gradle multi-module, 주 모듈: `apps/commerce-api`)
- 빌드: `./gradlew :apps:commerce-api:build`
- 테스트: `./gradlew :apps:commerce-api:test`
- 설계 문서: `.docs/` 디렉토리에 도메인 설계가 완료되어 있음. 구현 전 반드시 참조할 것

## 아키텍처 (Layered + DIP)
- 의존 방향: `Interfaces → Application → Domain ← Infrastructure`
- Domain 계층은 어떤 상위/하위 계층에도 의존하지 않는다 (순수 POJO)
- 패키지 구조 (`apps/commerce-api` 내 `com.loopers`):
  - `interfaces/api/{domain}` — Controller, Request/Response DTO(record), ApiSpec 인터페이스
  - `application/{domain}` — ApplicationService(유스케이스 조합, @Transactional 선언)
  - `domain/{domain}` — Entity, VO, Domain Service, Repository 인터페이스
  - `infrastructure/{domain}` — Repository 구현체(@Component), JpaRepository

## DDD 원칙
### Entity & Aggregate
- setter 금지. 의도를 드러내는 메서드 사용 (cancelOrder(), decreaseStock())
- 풍부한 도메인 모델: 비즈니스 로직을 Entity 내부에 캡슐화 (빈약한 도메인 모델 금지)
- Entity는 BaseEntity를 상속하며, 생성자에서 self-validating
- 단, 물리 삭제 엔티티(Like, CartItem 등)는 BaseEntity를 상속하지 않는다
- 트랜잭션은 하나의 Aggregate만 수정. 외부 접근은 Aggregate Root를 통해서만

### Value Object
- 불변(Immutable). 값 변경 시 새 객체 생성하여 교체
- 동등성은 필드 값 기반 (equals/hashCode). Java record 사용 권장

### Service
- Domain Service(`*DomainService`): 상태 없음, 순수 클래스로 작성. `DomainServiceConfig`에서 @Bean 등록 (@Service 사용 금지)
- Application Service(`*ApplicationService`): 도메인 조회 → 도메인 로직 실행 → 커밋. 조율자(Coordinator) 역할만. 엔티티(Aggregate Root)를 리턴하며, Info DTO를 사용하지 않는다
- 뷰 조합(관련 엔티티 조합, 필드 선택)은 Controller(Interfaces 계층)가 담당. Response DTO의 `from()` 팩토리가 엔티티를 직접 받아 변환

### Repository
- 인터페이스는 domain 패키지, 구현체는 infrastructure 패키지
- Aggregate Root를 반환. 프레임워크 타입(Pageable 등) 도메인에 노출 금지

## 코딩 컨벤션
- 예외: `CoreException(ErrorType, message)` 사용
- 유효성 검증 시 `Objects.requireNonNull()`, `Assert.state()`, `Assert.notNull()`, `Assert.hasText()` 등 유틸 함수를 적극 활용할 것. 단, 비즈니스 맥락의 사용자 향 에러 메시지가 필요한 경우는 CoreException 사용
- DTO: record 사용. Application 계층에 Info DTO를 두지 않으며, Response DTO는 Interfaces 계층에서 엔티티를 직접 변환
- Lombok: @RequiredArgsConstructor, @Getter 허용. @Setter 금지
- 응답: `ApiResponse.success(data)` 래핑
- Controller Request DTO에 Spring Validation(`@NotNull`, `@NotBlank`, `@Min`, `@Valid` 등)을 적극 활용하여 간단한 유효성 검증은 Interfaces 계층에서 처리한다
- `@OneToMany`, `@ManyToOne`등 물리적 매핑 금지. pk만 갖는 논리적 매핑만 허용한다. 

## 계층 제한
- 계층별 역할/책임/경계/협력은 다음의 링크를 참조하여 반드시 지키도록 한다. [Layered Rule](.docs/align.md)

## 테스트 (TDD)
- TDD 원칙: 테스트 먼저 작성 → 구현 → 리팩터링
- 테스트 비중: 도메인 단위 테스트 > Integration > E2E
- 단위 테스트: 외부 의존 없이 Fake/Stub 사용
- ArchUnit 테스트도 진행한다.
- JUnit 5 + AssertJ
- `@Nested` + `@DisplayName("...할 때, ")`로 계층 구조
- 메서드명: 영문 서술형 (예: `throwsBadRequest_whenLoginIdIsNull`)

## 작업 원칙
- 기존 코드가 CLAUDE.md 규칙과 불일치하면 반드시 리팩터링한다 (기존 User 포함)
- CLAUDE.md나 `.docs/` 설계 문서에 명시되지 않은 의사결정이 필요한 경우, 반드시 사용자에게 확인 후 진행한다
- 리팩터링 방향이 모호하거나 여러 선택지가 있는 경우에도 사용자에게 확인한다
