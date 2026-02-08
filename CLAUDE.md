# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Loopers에서 제공하는 Spring Boot + Java 21 기반 멀티모듈 커머스 템플릿 프로젝트입니다.

## 빌드 및 실행 명령어

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :apps:commerce-api:build

# 테스트 실행
./gradlew test

# 단일 테스트 실행
./gradlew :apps:commerce-api:test --tests "ExampleServiceIntegrationTest"

# 애플리케이션 실행
./gradlew :apps:commerce-api:bootRun

# 로컬 인프라 실행 (MySQL, Redis, Kafka)
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 환경 실행 (Prometheus, Grafana)
docker-compose -f ./docker/monitoring-compose.yml up
```

## 멀티모듈 구조

```
Root
├── apps/                    # 실행 가능한 SpringBootApplication
│   ├── commerce-api         # REST API 서버 (Web + JPA + Redis)
│   ├── commerce-batch       # Spring Batch 배치 처리
│   └── commerce-streamer    # Kafka Consumer 스트리머
├── modules/                 # 재사용 가능한 설정 모듈
│   ├── jpa                  # JPA + QueryDSL 설정, BaseEntity 제공
│   ├── redis                # Redis 설정
│   └── kafka                # Kafka 설정
└── supports/                # 부가 기능 모듈
    ├── jackson              # JSON 직렬화 설정
    ├── logging              # 로깅 설정
    └── monitoring           # 메트릭/모니터링 설정
```

## 아키텍처 패턴

### 레이어 구조 (commerce-api)

```
interfaces/      → Controller, ApiSpec(Swagger), DTO
application/     → Facade (유스케이스 조합), Info (응답 DTO)
domain/          → Service, Model(Entity), Repository(인터페이스)
infrastructure/  → Repository 구현체, JpaRepository
support/         → ErrorType, CoreException
```

### 주요 컨벤션

- **Controller**: `{도메인}V{버전}Controller` 형식, `ApiSpec` 인터페이스 구현
- **Facade**: 여러 Service를 조합하는 유스케이스 계층
- **Repository**: 도메인에 인터페이스 정의, infrastructure에 구현체
- **BaseEntity**: 모든 엔티티가 상속 (id, createdAt, updatedAt, deletedAt 자동 관리)
- **API 응답**: `ApiResponse<T>` record로 통일 (meta + data 구조)
- **예외 처리**: `CoreException` + `ErrorType` enum 사용

### 설계 규칙

#### 1. VO(Value Object) 자기 검증 원칙
- 도메인 값은 반드시 VO(`record`)로 감싸고, 생성 시점에 스스로 유효성을 검증한다
- 외부 유틸리티 클래스(`ValidatorUtil` 등)에 검증 로직을 두지 않는다
- VO가 존재하는 것 자체가 유효한 값임을 보장한다
```java
// GOOD: VO가 스스로 검증
public record Email(String value) {
    public Email {
        if (!value.matches(EMAIL_PATTERN)) throw new CoreException(...);
    }
}

// BAD: 외부 유틸에서 검증
MemberValidatorUtil.validateEmail(emailString);
```

#### 2. 의존성 방향 원칙
- 도메인 계층은 상위 계층(application, interfaces)에 의존하지 않는다
- Service는 application DTO가 아닌 도메인 VO/원시 타입을 파라미터로 받는다
- DTO → VO 변환은 Facade(application 계층)에서 수행한다
```
interfaces → application → domain ← infrastructure
                 (Facade에서 DTO→VO 변환)
```

#### 3. Repository 단일 책임 원칙
- Repository 구현체는 데이터 접근(CRUD)만 담당한다
- 분기 로직, 비즈니스 판단을 Repository에 넣지 않는다
- 조회/저장/수정/삭제 각각 별도 메서드로 분리한다
```java
// GOOD: 단순 저장
void save(MemberModel member) {
    memberJpaRepository.save(MemberEntity.toEntity(member));
}

// BAD: Repository에 비즈니스 분기
void save(MemberModel member) {
    if (member.getId() != null) { updatePassword(...); }
    else { memberJpaRepository.save(...); }
}
```

#### 4. 레이어 간 중복 금지 원칙
- 동일한 작업(인증, 검증 등)이 여러 레이어에서 반복되지 않아야 한다
- Facade에서 인증했으면 Service에서 다시 인증하지 않는다
- 각 레이어는 자신만의 고유 책임을 가진다

| 레이어 | 책임 |
|--------|------|
| Facade | 유스케이스 흐름 조합, DTO↔VO 변환 |
| Service | 도메인 비즈니스 로직, 트랜잭션 관리 |
| Repository | 데이터 접근(CRUD)만 |

#### 5. VO 독립성 원칙
- VO 간에 직접 의존하지 않는다
- 다른 VO의 값이 필요하면 원시 타입(`String`, `int` 등)으로 전달받는다
- VO 간 연결은 상위 계층(Facade, Service)에서 조합한다
```java
// GOOD: 원시 타입으로 받음
Password.validateRawPassword(rawPassword, birthDate.toFormattedString());

// BAD: VO가 다른 VO에 의존
Password.validateRawPassword(rawPassword, birthDate); // Password가 BirthDate를 알게 됨
```

### 테스트

- **테스트 프로필**: `spring.profiles.active=test` 자동 적용
- **Testcontainers**: MySQL 테스트컨테이너 자동 설정 (`MySqlTestContainersConfig`)
- **DatabaseCleanUp**: 테스트 간 테이블 truncate 유틸리티 (`@AfterEach`에서 사용)
- **테스트 패턴**: DisplayName + Nested 클래스로 구조화

## 환경 설정

- **프로필**: local, test, dev, qa, prd
- **Java 버전**: 21
- **Spring Boot**: 3.4.4
- **로컬 인프라**: MySQL 8.0 (3306), Redis Master/Replica (6379/6380), Kafka (19092)
- **Swagger UI**: http://localhost:8080/swagger-ui.html (prd에서 비활성화)
- **Grafana**: http://localhost:3000 (admin/admin)

## 문서 작성 규칙

- 문서에 프로세스, 흐름, 워크플로우가 포함될 경우 반드시 Mermaid 다이어그램으로 시각화할 것
- 지원하는 다이어그램 유형:
  - `flowchart`: 프로세스 흐름, 의사결정 트리
  - `sequenceDiagram`: API 호출, 시스템 간 통신
  - `stateDiagram`: 상태 변화
  - `classDiagram`: 클래스 구조, 관계
  - `erDiagram`: 엔티티 관계도
- 다이어그램은 마크다운 코드 블록으로 삽입: \`\`\`mermaid ... \`\`\`
