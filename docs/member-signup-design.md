# 회원가입 기능 설계

## 검증 규칙

| 필드 | 규칙 |
|------|------|
| loginId | NOT NULL, NOT BLANK |
| password | 8~16자, 영문 대소문자 + 숫자 + 특수문자만 허용, 생년월일(yyyyMMdd) 포함 불가 |
| name | NOT NULL, NOT BLANK, 한글 2~20자 |
| birthday | NOT NULL, `yyyy-MM-dd` 형식, 미래 날짜 불가 |
| email | NOT NULL, 이메일 형식 (RFC 5322) |

## 시퀀스 다이어그램

```mermaid
sequenceDiagram
    actor Client
    participant Controller as MemberV1Controller
    participant Facade as MemberFacade
    participant Service as MemberService
    participant Member as Member (Domain)
    participant Repository as MemberRepository
    participant RepoImpl as MemberRepositoryImpl
    participant Entity as MemberEntity (Persistence)
    participant JpaRepo as MemberJpaRepository
    participant PasswordEncoder as PasswordEncoder

    Client->>Controller: POST /api/v1/members (SignUpRequest)
    Controller->>Facade: signUp(request)
    Facade->>Service: signUp(loginId, password, name, birthday, email)

    Note over Service: 1. loginId 중복 검사
    Service->>Repository: existsByLoginId(loginId)
    Repository->>RepoImpl: existsByLoginId(loginId)
    RepoImpl->>JpaRepo: existsByLoginId(loginId)
    JpaRepo-->>RepoImpl: boolean
    RepoImpl-->>Service: boolean

    alt loginId 중복
        Service-->>Controller: CoreException (CONFLICT)
        Controller-->>Client: 409 Conflict
    end

    Note over Service: 2. email 중복 검사
    Service->>Repository: existsByEmail(email)
    Repository->>RepoImpl: existsByEmail(email)
    RepoImpl->>JpaRepo: existsByEmail(email)
    JpaRepo-->>RepoImpl: boolean
    RepoImpl-->>Service: boolean

    alt email 중복
        Service-->>Controller: CoreException (CONFLICT)
        Controller-->>Client: 409 Conflict
    end

    Note over Service: 3. 도메인 객체 생성 (필드 검증은 생성자에서 수행)
    Service->>Member: new Member(loginId, rawPassword, name, birthday, email)
    Note over Member: 이름 형식 검증 (한글 2~20자)<br/>생년월일 형식 검증 (미래 날짜 불가)<br/>이메일 형식 검증<br/>비밀번호 규칙 검증<br/>(8~16자, 문자종류, 생년월일 포함 여부)

    alt 검증 실패
        Member-->>Service: CoreException (BAD_REQUEST)
        Service-->>Controller: CoreException (BAD_REQUEST)
        Controller-->>Client: 400 Bad Request
    end

    Note over Service: 4. 비밀번호 암호화
    Service->>PasswordEncoder: encode(rawPassword)
    PasswordEncoder-->>Service: encodedPassword
    Service->>Member: encryptPassword(encodedPassword)

    Note over Service: 5. 저장
    Service->>Repository: save(member)
    Repository->>RepoImpl: save(member)
    Note over RepoImpl: Domain → Persistence 변환
    RepoImpl->>Entity: MemberEntity.from(member)
    RepoImpl->>JpaRepo: save(memberEntity)
    JpaRepo-->>RepoImpl: MemberEntity
    Note over RepoImpl: Persistence → Domain 변환
    RepoImpl->>Entity: toDomain()
    RepoImpl-->>Service: Member

    Service-->>Facade: Member
    Note over Facade: Domain → Info 변환
    Facade-->>Controller: MemberInfo
    Controller-->>Client: 201 Created (SignUpResponse)
```

## 클래스 다이어그램

```mermaid
classDiagram
    direction TB

    %% === Interfaces Layer ===
    class MemberV1Controller {
        -MemberFacade memberFacade
        +signUp(SignUpRequest) ApiResponse~SignUpResponse~
    }

    class MemberV1ApiSpec {
        <<interface>>
        +signUp(SignUpRequest) ApiResponse~SignUpResponse~
    }

    class MemberV1Dto {
        <<class>>
    }

    class SignUpRequest {
        <<record>>
        +String loginId
        +String password
        +String name
        +String birthday
        +String email
    }

    class SignUpResponse {
        <<record>>
        +Long id
        +String loginId
        +String name
        +String email
        +from(MemberInfo) SignUpResponse
    }

    %% === Application Layer ===
    class MemberFacade {
        -MemberService memberService
        +signUp(SignUpRequest) MemberInfo
    }

    class MemberInfo {
        <<record>>
        +Long id
        +String loginId
        +String name
        +String email
        +from(Member) MemberInfo
    }

    %% === Domain Layer ===
    class Member {
        <<Domain Entity>>
        -Long id
        -String loginId
        -String password
        -String name
        -LocalDate birthday
        -String email
        +Member(loginId, rawPassword, name, birthday, email)
        +encryptPassword(encodedPassword)
        -validateLoginId(loginId)
        -validatePassword(rawPassword, birthday)
        -validateName(name)
        -validateBirthday(birthday)
        -validateEmail(email)
    }

    class MemberRepository {
        <<interface>>
        +save(Member) Member
        +existsByLoginId(String) boolean
        +existsByEmail(String) boolean
    }

    class MemberService {
        -MemberRepository memberRepository
        -PasswordEncoder passwordEncoder
        +signUp(loginId, password, name, birthday, email) Member
    }

    %% === Infrastructure Layer ===
    class MemberEntity {
        <<JPA Entity>>
        -String loginId
        -String password
        -String name
        -LocalDate birthday
        -String email
        +from(Member)$ MemberEntity
        +toDomain() Member
    }

    class BaseEntity {
        <<abstract>>
        -Long id
        -ZonedDateTime createdAt
        -ZonedDateTime updatedAt
        -ZonedDateTime deletedAt
    }

    class MemberRepositoryImpl {
        -MemberJpaRepository memberJpaRepository
        +save(Member) Member
        +existsByLoginId(String) boolean
        +existsByEmail(String) boolean
    }

    class MemberJpaRepository {
        <<interface>>
        +existsByLoginId(String) boolean
        +existsByEmail(String) boolean
    }

    class JpaRepository~T~ {
        <<interface>>
    }

    %% === Relationships ===
    MemberV1ApiSpec <|.. MemberV1Controller
    MemberV1Controller --> MemberFacade
    MemberV1Controller ..> MemberV1Dto
    MemberV1Dto *-- SignUpRequest
    MemberV1Dto *-- SignUpResponse
    SignUpResponse ..> MemberInfo

    MemberFacade --> MemberService
    MemberFacade ..> MemberInfo
    MemberInfo ..> Member

    MemberService --> MemberRepository
    MemberService ..> Member

    MemberRepository <|.. MemberRepositoryImpl
    MemberRepositoryImpl --> MemberJpaRepository
    MemberRepositoryImpl ..> MemberEntity
    MemberRepositoryImpl ..> Member
    BaseEntity <|-- MemberEntity
    JpaRepository~T~ <|-- MemberJpaRepository
```

## 패키지 구조

```
com.loopers
├── interfaces/api/member/
│   ├── MemberV1Controller        ← REST 엔드포인트
│   ├── MemberV1Dto               ← SignUpRequest, SignUpResponse (record)
│   └── MemberV1ApiSpec           ← Swagger 스펙 인터페이스
├── application/member/
│   ├── MemberFacade              ← 유즈케이스 오케스트레이션
│   └── MemberInfo                ← 응답 변환용 record
├── domain/member/
│   ├── Member                    ← 도메인 엔티티 (순수 Java 객체, 검증 로직 포함)
│   ├── MemberRepository          ← 도메인 레포지토리 인터페이스
│   └── MemberService             ← 비즈니스 로직 (중복 검사, 암호화 위임)
└── infrastructure/member/
    ├── MemberEntity              ← JPA 영속성 엔티티 (BaseEntity 상속)
    ├── MemberRepositoryImpl      ← 레포지토리 구현체 (Domain ↔ Entity 변환)
    └── MemberJpaRepository       ← Spring Data JPA 인터페이스
```

## Domain Entity vs Persistence Entity 분리

| 구분 | Member (Domain) | MemberEntity (Persistence) |
|------|-----------------|---------------------------|
| 위치 | `domain/member/` | `infrastructure/member/` |
| 역할 | 비즈니스 검증 로직 | DB 영속화 |
| 상속 | 없음 (순수 Java 객체) | `BaseEntity` 상속 |
| JPA 어노테이션 | 없음 | `@Entity`, `@Table`, `@Column` |
| 변환 | - | `from(Member)`, `toDomain()` |
