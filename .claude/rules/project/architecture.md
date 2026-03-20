# 아키텍처

## 패키지 구조 (4-Layered_Architecture)
```
com.loopers/
├── interfaces/          # REST 컨트롤러, Request DTO, Response DTO (V1Dto)
├── application/         # Facade, ApplicationService(xxxService), Command, Info
├── domain/              # Entity, Domain Service, Repository 인터페이스, VO
├── infrastructure/      # Repository 구현체, 외부 어댑터
└── support/             # 횡단 관심사 (에러, 유틸, 글로벌 핸들러)
```

## 의존성 방향
```
interfaces → application → domain ← infrastructure
```
- domain은 infrastructure를 알지 않음
- 외부 의존성은 인터페이스로 추상화
- **도메인 간 의존 규칙**: ApplicationService는 자기 도메인만 접근, 크로스 도메인 협력은 반드시 Facade에서

## 핵심 패턴

- **Rich Domain Model**: 비즈니스 로직과 도메인 불변식은 Entity에
- **ApplicationService**: 자기 도메인의 유스케이스 캡슐화 (Repository 접근 + Entity/Domain Service 위임)
- **Facade**: 여러 도메인의 ApplicationService 간 오케스트레이션, 트랜잭션 경계
- **Repository 패턴**: 인터페이스는 `domain/`, 구현체는 `infrastructure/`
- **DTO**: Java record 사용 (불변 보장)
- **Soft Delete**: `deletedAt` 필드 사용

## Base Entity

- 수정/삭제가 있는 엔티티: `BaseEntity` 상속 (`createdAt`, `updatedAt`, `deletedAt`)
- 생성만 있는 엔티티 (좋아요, 로그 등): `createdAt`만 포함
- 요구사항에 삭제가 없더라도 수정이 있으면 `BaseEntity` 사용 권장 (삭제 요구는 나중에 추가될 확률 높음)

## 계층별 역할

### Controller (interfaces)
- HTTP 요청/응답 변환 + 입력 검증 + Facade 호출
- API별 enum은 Response DTO 내부에 inner enum으로 정의
- 입출력 데이터 흐름은 `conventions/dto.md` 참고
- 검증 규칙은 `conventions/validation.md` 참고

### Facade (application)
- 여러 도메인의 ApplicationService 호출 오케스트레이션
- 트랜잭션 경계 (메서드 레벨 선언)
- 명령 입력: `Command` (Controller에서 변환), 조회 입력: 개별 파라미터
- **접근 제어 판단** — 유스케이스별 권한 검증 (상세: `conventions/validation.md`)
- Domain Entity → Info DTO 변환
- 다른 도메인의 **ApplicationService만** 호출 (Repository 직접 호출 금지)
- Controller는 **항상 Facade만 호출** (일관성 유지, Entity가 Controller에 노출되지 않음)
- 단일 도메인이어도 Facade 유지 (DTO 변환 책임 + 크로스 도메인 확장 대비)

### ApplicationService (application) — 유스케이스

- **각 비즈니스 메서드가 조회부터 실행까지 완결적으로 소유한다**
  - 조회를 private 헬퍼로 공유하지 않는다
  - 각 메서드의 조회 방식이 독립적으로 변경될 수 있다
    (예: 수정은 `findByIdForUpdate`, 삭제는 `findById`)

#### 명령을 위한 조회

- 쿼리에는 **식별 조건만** 사용한다 (PK, FK, 복합키, 유니크 관계)
- **상태 조건(deletedAt, status 등)은 쿼리에 포함하지 않는다** — 상태 검증은 Entity 책임
- 동시성 제어가 필요한 경우 락 조회 허용 (`findByIdForUpdate` 등)
- 연산에 필요한 DB 조회(중복 확인, 존재 여부 등)는 메서드 내부에서 처리

> **식별 조건 vs 상태 조건**: "이 조건을 빼면 다른 Entity가 조회되는가?"
> - 다른 Entity가 나온다 → 식별 조건 → 쿼리에 포함
> - 같은 Entity인데 더 많이 나온다 → 상태 조건 → Entity가 판단

#### 조회를 위한 조회 (QueryService)

- 표현을 위한 조회 전용 서비스
- 상태 필터링, 정렬, 페이징 등 **쿼리 조건에 자유롭게 포함 가능**
- 예: `getActiveBrands`, `findProductsByStatus`, `searchByKeyword`
- 조회가 단순한 도메인은 ApplicationService에 포함해도 무방, 복잡해지면 분리

#### 제약

- 자기 도메인의 Repository + Domain Service만 사용
- **다른 도메인의 ApplicationService 직접 호출 금지** (크로스 도메인은 Facade 책임)
- **private 메서드 금지**
- **자가호출 금지**
- 네이밍: `xxxService` (예: `BrandService`)

### Domain Service (domain) — 필요할 때만 생성
- 단일 Entity로 해결 안 되는 비즈니스 로직
- 여러 Entity/VO 조합이 필요한 계산, 검증, 결정
- **Repository 의존 없음** (순수 비즈니스 로직만)
- 네이밍: `-Calculator`, `-Validator`, `-Resolver`, `-Policy`

### Entity (domain) — 비즈니스 로직의 중심
- 정적 팩토리 메서드로 생성 (`create()`)
- 자기 데이터의 검증, 상태 전이, 계산
- setter 대신 의미 있는 메서드명 (`changeToFailed()`, `deductStock()`)
- 생성자에서 필수 불변 조건(invariant) 검증
- **불변식은 예외로 방어, 불변식이 아닌 조건은 사실만 제공** — 판단은 호출자 책임 (상세: `conventions/validation.md`)

### Repository (domain → infrastructure)
- 인터페이스는 `domain/` 패키지에 정의
- 구현체는 `infrastructure/` 패키지에 배치

### 트랜잭션 전략
- 클래스 레벨 `@Transactional` 선언 금지 — 모든 메서드에 개별 선언
    - 명령: `@Transactional`, 조회: `@Transactional(readOnly = true)`
    - Facade가 있으면 ApplicationService의 트랜잭션은 기존 트랜잭션에 참여 (REQUIRED)
