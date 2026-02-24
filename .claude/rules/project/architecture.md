# 아키텍처

## 패키지 구조 (DDD)
```
com.loopers/
├── interfaces/          # REST 컨트롤러, Request/Response DTO
├── application/         # Facade (오케스트레이션, 트랜잭션 경계), Info DTO
├── domain/              # Entity, Service, Domain Service, Repository 인터페이스, VO
├── infrastructure/      # Repository 구현체, 외부 어댑터
└── support/             # 횡단 관심사 (에러, 유틸, 글로벌 핸들러)
```

## 의존성 방향
```
interfaces → application → domain ← infrastructure
```
- domain은 infrastructure를 알지 않음
- 외부 의존성은 인터페이스로 추상화
- **도메인 간 의존 규칙**: Service는 자기 도메인만 접근, 크로스 도메인 협력은 반드시 Facade에서

## 핵심 패턴

- **Rich Domain Model**: 비즈니스 로직과 도메인 불변식은 Entity에
- **Service**: 자기 도메인의 연산 캡슐화 (Repository 접근 + Entity/Domain Service 위임)
- **Facade**: 여러 도메인 Service 간 오케스트레이션, 트랜잭션 경계
- **Repository 패턴**: 인터페이스는 `domain/`, 구현체는 `infrastructure/`
- **DTO**: Java record 사용 (불변 보장)
- **Soft Delete**: `deletedAt` 필드 사용

## Base Entity

- 수정/삭제가 있는 엔티티: `BaseEntity` 상속 (`createdAt`, `updatedAt`, `deletedAt`)
- 생성만 있는 엔티티 (좋아요, 로그 등): `createdAt`만 포함
- 요구사항에 삭제가 없더라도 수정이 있으면 `BaseEntity` 사용 권장 (삭제 요구는 나중에 추가될 확률 높음)

## 계층별 역할

### Controller (interfaces)
- HTTP 요청/응답 변환만 담당
- Request에서 원시값 추출하여 Facade에 전달
- try-catch 금지 (글로벌 핸들러가 처리)
- `@Valid`로 Request DTO 입력값 형식 검증
- API별 enum은 Request/Response DTO 내부에 inner enum으로 정의

### Facade (application)
- 여러 도메인 Service 호출 오케스트레이션
- 트랜잭션 경계 (`@Transactional`)
- Domain Entity → Info DTO 변환
- 다른 도메인의 **Service만** 호출 (Repository 직접 호출 금지)

### Service (domain) — 명령 흐름
- 자기 도메인의 연산 캡슐화
- **조회 메서드**: 대상 식별만 수행
  - 쿼리에는 식별 조건만 사용한다
  - 식별 조건: 조건을 제거하면 다른 Entity가 조회되는 것 (PK, FK, 복합키, 유니크 관계 등)
  - 예: `findById`, `findByUserIdAndProductId`, `findAllByIdIn`
  - 동시성 제어가 필요한 경우 락 조회 허용 (`findByIdForUpdate` 등)
  - **상태 조건(deletedAt, status 등)은 쿼리에 포함하지 않는다** — 상태 검증은 Entity 책임
- **명령 메서드**: Entity를 받아서 도메인 연산 수행 (판단+실행)
  - 연산에 필요한 DB 조회(중복 확인, 존재 여부 등)는 Service 내부에서 처리
  - 단, 대상 식별 자체가 연산과 불가분인 경우 Service 내부에서 조회 포함
    (예: unlike — 존재 여부 확인이 곧 연산의 전제조건)
- 자기 도메인의 Repository + Domain Service만 사용
- **다른 도메인의 Service 직접 호출 금지** (크로스 도메인은 Facade 책임)
- Facade에 도메인 내부 구조(Optional, 상태값, Entity 컬렉션) 노출 최소화

#### 식별 조건 vs 상태 조건 판별 기준

> **"이 조건을 빼면 다른 Entity가 조회되는가?"**

- 빼면 다른 Entity가 나온다 → **식별 조건** → 쿼리에 포함
- 빼도 같은 Entity인데 더 많이 나온다 → **상태 조건** → Entity가 판단

### QueryService (domain) — 조회 흐름

- 표현을 위한 조회 전용 서비스
- 상태 필터링, 정렬, 페이징 등 쿼리 조건에 포함 가능
- 예: `getActiveBrand`, `getActiveProducts`, `findOrdersByStatus`
- 조회가 단순한 도메인은 Service에 조회 메서드로 포함해도 무방
- 조회가 복잡해지는 시점(정렬, 페이징, 검색 조건, DTO 프로젝션)에서 분리

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

### Repository (domain → infrastructure)
- 인터페이스는 `domain/` 패키지에 정의
- 구현체는 `infrastructure/` 패키지에 배치

### 트랜잭션 전략
- **Service**: 클래스 레벨 `@Transactional(readOnly = true)` 기본 적용
    - 명령 메서드는 메서드 레벨 `@Transactional`로 오버라이드
- **Facade**: 클래스 레벨 `@Transactional(readOnly = true)` 기본 적용
    - 명령 메서드는 메서드 레벨 `@Transactional`로 오버라이드
    - Facade가 있으면 Service의 트랜잭션은 기존 트랜잭션에 참여 (REQUIRED)
