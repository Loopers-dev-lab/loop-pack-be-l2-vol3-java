---
name: layered-architecture
description: 레이어드 아키텍처 가이드 (CQRS 포함). 새 도메인 패키지 생성, 레이어 간 데이터 흐름 설계, 기존 코드의 레이어 구조 이해 시 사용한다.
---

# Layered Architecture

## 1. 레이어 흐름도

```
Client Request
  → Controller (interfaces/)
    → Facade (application/facade/)
      → Service (application/service/)
        → Domain Service (domain/service/)
        → Domain Model (domain/model/)
      → Repository Interface (application/repository/)
        → RepositoryImpl (infrastructure/repository/)
          → JPA Repository (infrastructure/jpa/)
            → Entity (infrastructure/entity/) ↔ Domain Model
  → Controller
→ Client Response
```

## 2. 레이어별 책임

| 레이어 | 클래스 패턴 | 어노테이션 | 역할 |
|--------|-------------|-----------|------|
| Controller | `{Domain}Controller` | `@RestController` | 요청 수신, 응답 반환, Facade 호출 |
| Facade (Command) | `{Domain}CommandFacade` | `@Service`, `@Transactional` | 명령 유스케이스 오케스트레이션, 트랜잭션 경계 |
| Facade (Query) | `{Domain}QueryFacade` | `@Service`, `@Transactional(readOnly=true)` | 조회 유스케이스 오케스트레이션 |
| Service | `{Domain}CommandService` | `@Service`, `@Transactional` | 단일 도메인 비즈니스 로직 실행 |
| Domain Service | `{Domain}XxxValidator` 등 | 순수 클래스 (`@Bean` 등록) | 리포지토리 의존 비즈니스 불변식 검증 |
| Repository (I) | `{Domain}CommandRepository` | 인터페이스 | 명령 계약 (save, delete) |
| Repository (I) | `{Domain}QueryRepository` | 인터페이스 | 조회 계약 (find, exists) |
| RepositoryImpl | `{Domain}Command/QueryRepositoryImpl` | `@Repository` | Entity ↔ Domain 변환 후 JPA 호출 |
| Entity | `{Domain}Entity` | `@Entity` | DB 매핑, `from(Domain)` + `toDomain()` |

## 3. 핵심 규칙

### 3.1 호출 순서

```
Controller → Facade → Service → Repository
```

- **계층 건너뛰기 절대 금지** (예: Controller → Service 직접 호출 불가)
- Controller는 반드시 Facade를 통해서만 비즈니스 로직에 접근

### 3.2 비즈니스 vs 서비스 로직 분리

| 구분 | 위치 | 예시 |
|------|------|------|
| 비즈니스 로직 | Domain Model, Domain Service | 유효성 검증, 도메인 계산, 상태 전이, 불변식 검증 |
| 서비스 로직 | Facade, Service | 유스케이스 오케스트레이션, 트랜잭션 관리, 외부 시스템 연동 |

### 3.3 CQRS 분리

- Command: 상태 변경 (save, update, delete)
- Query: 상태 조회 (find, exists, count)
- Repository 인터페이스부터 Command/Query로 분리

## 4. 데이터 변환 흐름 (DTO 패턴)

```
Request → toInDto() → [Facade] → Domain → OutDto.from(domain) → [Controller] → Response.from(outDto)
```

### 4.1 DTO 유형별 규칙

| DTO | 위치 | 변환 메서드 | 역할 |
|-----|------|-----------|------|
| Request | `interfaces/controller/request/` | `toInDto()` | 외부 입력 수신, Jakarta Validation 적용 |
| InDto | `application/dto/in/` | 없음 (불변 record) | 레이어 간 입력 전달 |
| OutDto | `application/dto/out/` | `from(Domain)` static 팩토리 | 도메인 → 출력 변환 |
| Response | `interfaces/controller/response/` | `from(OutDto)` static 팩토리 | 최종 응답 (마스킹 가능) |

### 4.2 DTO 규칙

- 모든 DTO는 불변 record로 구현
- Request에 `@NotBlank`, `@NotNull` 등 Jakarta Validation 적용
- Response에 민감정보(password 등) 포함 **금지**
- Response에서 데이터 마스킹 가능 (예: 이름 마지막 글자)

## 5. Entity 업데이트 패턴

### 5.1 신규 생성

```
Entity.from(domain) → jpaRepository.save(entity) → entity.toDomain()
```

### 5.2 기존 수정

```
jpaRepository.findById(id) → existingEntity.updateXxx(...) → JPA dirty checking → entity.toDomain()
```

- `Entity.from(domain)`은 항상 **새 엔티티(id 없음)**를 생성
- 업데이트 시 `Entity.from(domain)` 사용 **절대 금지** — 기존 엔티티를 조회하여 수정

## 6. 패키지 구조

```
{domain}/
├── application/
│   ├── service/       # 애플리케이션 서비스
│   ├── facade/        # 퍼사드 서비스
│   ├── repository/    # 리포지토리 인터페이스 (CQRS)
│   └── dto/
│       ├── in/        # 입력 DTO
│       └── out/       # 출력 DTO
├── domain/
│   ├── model/         # 도메인 모델 + enum/ + vo/
│   ├── event/         # 도메인 이벤트
│   └── service/       # 도메인 서비스
├── infrastructure/
│   ├── jpa/           # JPA 레포지토리
│   ├── repository/    # 애플리케이션 레포지토리 구현체
│   └── entity/        # JPA 엔티티
├── interfaces/
│   ├── controller/    # REST 컨트롤러 + request/ + response/
│   └── event/         # 이벤트 리스너
└── support/
    ├── common/        # 공통 유틸리티 + error/
    └── config/        # 도메인별 설정
```

## 7. 금지 사항

- Controller에서 Repository 직접 호출 금지
- Facade/Service에서 입력값 정규화 금지 (도메인 모델 책임)
- Entity 업데이트 시 `Entity.from()` 사용 금지
- 비즈니스 로직을 Controller/Facade에 작성 금지
- 도메인 모델에 프레임워크 어노테이션 사용 금지
