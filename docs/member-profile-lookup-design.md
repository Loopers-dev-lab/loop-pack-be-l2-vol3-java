# 내 정보 조회 기능 설계

## 요청/응답 스펙

| 항목 | 값 |
|------|---|
| Method | GET |
| URL | `/api/v1/members/me` |
| 인증 | `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더 |
| 응답 코드 (성공) | 200 OK |
| 응답 코드 (인증 실패) | 401 Unauthorized |

### 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| loginId | String | 로그인 ID |
| name | String | 이름 |
| birthday | String | 생년월일 (yyyy-MM-dd) |
| email | String | 이메일 |

## 시퀀스 다이어그램

```mermaid
sequenceDiagram
    actor Client
    participant Controller as MemberV1Controller
    participant Facade as MemberFacade
    participant Service as MemberService
    participant Repository as MemberRepository
    participant RepoImpl as MemberRepositoryImpl
    participant Entity as MemberEntity (Persistence)
    participant JpaRepo as MemberJpaRepository
    participant PasswordEncoder as PasswordEncoder

    Client->>Controller: GET /api/v1/members/me<br/>X-Loopers-LoginId / X-Loopers-LoginPw

    Controller->>Facade: getMyInfo(loginId, password)

    Note over Facade: 1. 회원 조회
    Facade->>Service: authenticate(loginId, password)
    Service->>Repository: findByLoginId(loginId)
    Repository->>RepoImpl: findByLoginId(loginId)
    RepoImpl->>JpaRepo: findByLoginId(loginId)
    JpaRepo-->>RepoImpl: Optional~MemberEntity~
    Note over RepoImpl: Persistence → Domain 변환
    RepoImpl->>Entity: toDomain()
    Entity-->>RepoImpl: Member
    RepoImpl-->>Service: Optional~Member~

    alt 회원 미존재
        Service-->>Controller: CoreException (UNAUTHORIZED)
        Controller-->>Client: 401 Unauthorized
    end

    Note over Service: 2. 비밀번호 검증
    Service->>PasswordEncoder: matches(rawPassword, encodedPassword)
    PasswordEncoder-->>Service: boolean

    alt 비밀번호 불일치
        Service-->>Controller: CoreException (UNAUTHORIZED)
        Controller-->>Client: 401 Unauthorized
    end

    Service-->>Facade: Member

    Note over Facade: Domain → Info 변환
    Facade-->>Controller: MemberInfo
    Controller-->>Client: 200 OK (MyInfoResponse)
```

## 클래스 다이어그램

```mermaid
classDiagram
    direction TB

    %% === Interfaces Layer ===
    class MemberV1Controller {
        -MemberFacade memberFacade
        +getMyInfo(loginId, password) ApiResponse~MyInfoResponse~
    }

    class MemberV1ApiSpec {
        <<interface>>
        +getMyInfo(loginId, password) ApiResponse~MyInfoResponse~
    }

    class MyInfoResponse {
        <<record>>
        +String loginId
        +String name
        +String birthday
        +String email
        +from(MemberInfo) MyInfoResponse
    }

    %% === Application Layer ===
    class MemberFacade {
        -MemberService memberService
        +getMyInfo(loginId, password) MemberInfo
    }

    class MemberInfo {
        <<record>>
        +Long id
        +String loginId
        +String name
        +LocalDate birthday
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
    }

    class MemberRepository {
        <<interface>>
        +findByLoginId(String) Optional~Member~
    }

    class MemberService {
        -MemberRepository memberRepository
        -PasswordEncoder passwordEncoder
        +authenticate(loginId, password) Member
    }

    %% === Infrastructure Layer ===
    class MemberEntity {
        <<JPA Entity>>
        -String loginId
        -String password
        -String name
        -LocalDate birthday
        -String email
        +toDomain() Member
    }

    class MemberRepositoryImpl {
        -MemberJpaRepository memberJpaRepository
        +findByLoginId(String) Optional~Member~
    }

    class MemberJpaRepository {
        <<interface>>
        +findByLoginId(String) Optional~MemberEntity~
    }

    %% === Relationships ===
    MemberV1ApiSpec <|.. MemberV1Controller
    MemberV1Controller --> MemberFacade
    MemberV1Controller ..> MyInfoResponse
    MyInfoResponse ..> MemberInfo

    MemberFacade --> MemberService
    MemberFacade ..> MemberInfo
    MemberInfo ..> Member

    MemberService --> MemberRepository
    MemberService ..> Member

    MemberRepository <|.. MemberRepositoryImpl
    MemberRepositoryImpl --> MemberJpaRepository
    MemberRepositoryImpl ..> MemberEntity
    MemberRepositoryImpl ..> Member
```