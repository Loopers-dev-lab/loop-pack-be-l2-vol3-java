# User 도메인 구현 문서

## 개요

사용자 회원가입, 인증, 비밀번호 변경 기능을 구현했습니다.
Layered Architecture + DDD 기반으로 설계했습니다.

---

## API 명세

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/v1/users | 회원가입 |
| GET | /api/v1/users/me | 내 정보 조회 |
| PATCH | /api/v1/users/me/password | 비밀번호 변경 |

---

## 아키텍처

```mermaid
graph TB
    subgraph Interface["Interface Layer"]
        UC[UserController]
        AR[AuthUserArgumentResolver]
    end

    subgraph Application["Application Layer"]
        UF[UserFacade]
    end

    subgraph Domain["Domain Layer"]
        US[UserService]
        UAS[UserAuthService]
        U[User]
        PE[PasswordEncoder]
    end

    subgraph Infrastructure["Infrastructure Layer"]
        UE[UserEntity]
        URI[UserRepositoryImpl]
        BCE[BCryptPasswordEncoder]
    end

    UC --> UF
    UC --> US
    UF --> UAS
    UF --> US
    US --> UR[UserRepository]
    UAS --> UR
    URI -.->|implements| UR
    BCE -.->|implements| PE
```

---

## 시퀀스 다이어그램

### 1. 회원가입

```mermaid
sequenceDiagram
    actor Client
    participant UC as UserController
    participant US as UserService
    participant UR as UserRepository
    participant DB as Database

    Client->>UC: POST /api/v1/users
    UC->>US: register(command)
    US->>US: VO 검증 (Password, Email 등)
    US->>UR: existsByUserId()
    UR->>DB: SELECT
    DB-->>UR: false
    US->>US: 비밀번호 암호화 (BCrypt)
    US->>UR: save(user)
    UR->>DB: INSERT
    DB-->>UR: OK
    US-->>UC: User
    UC-->>Client: 201 Created
```

### 2. 비밀번호 변경

```mermaid
sequenceDiagram
    actor Client
    participant UC as UserController
    participant UF as UserFacade
    participant UAS as UserAuthService
    participant US as UserService
    participant UR as UserRepository
    participant DB as Database

    Client->>UC: PATCH /api/v1/users/me/password
    Note right of Client: Header: Authorization
    UC->>UF: changePassword(request)
    UF->>UAS: authenticate(command)
    UAS->>UR: findByUserId()
    UR->>DB: SELECT
    DB-->>UR: UserEntity
    UAS->>UAS: 비밀번호 검증
    UAS-->>UF: User

    UF->>US: changePassword(command)
    US->>US: 신규 비밀번호 검증
    Note right of US: - 8~16자<br/>- 대/소문자, 숫자, 특수문자<br/>- 생년월일 미포함<br/>- 기존 비밀번호와 다름
    US->>US: BCrypt 암호화
    US->>UR: save(user)
    UR->>DB: UPDATE
    DB-->>UR: OK
    US-->>UF: void
    UF-->>UC: void
    UC-->>Client: 200 OK
```

---

## 도메인 모델

```mermaid
classDiagram
    class User {
        <<record>>
        -UserId id
        -Password password
        -Name name
        -Email email
        -BirthDate birthDate
        +getMaskedName() String
    }

    class UserId {
        <<VO>>
        -String value
        검증: 4~20자, 영문소문자+숫자
    }

    class Password {
        <<VO>>
        -String value
        검증: 8~16자
        대/소문자, 숫자, 특수문자 필수
        +containsDate(date) boolean
        +isEncoded() boolean
        +ofEncoded(String) Password
    }

    class Email {
        <<VO>>
        -String value
        검증: 이메일 형식
    }

    class Name {
        <<VO>>
        -String value
        검증: 2~10자, 한글만
    }

    class BirthDate {
        <<VO>>
        -LocalDate value
        검증: 과거 날짜만
    }

    User *-- UserId
    User *-- Password
    User *-- Name
    User *-- Email
    User *-- BirthDate
```

---

## ERD

```mermaid
erDiagram
    users {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR(20) user_id UK
        VARCHAR(255) password
        VARCHAR(10) name
        VARCHAR(255) email
        DATE birth_date
        DATETIME created_at
        DATETIME updated_at
    }
```

---

## 비밀번호 정책

| 규칙 | 조건 |
|------|------|
| 길이 | 8~16자 |
| 대문자 | 1개 이상 필수 |
| 소문자 | 1개 이상 필수 |
| 숫자 | 1개 이상 필수 |
| 특수문자 | 1개 이상 필수 |
| 생년월일 | 포함 불가 (yyMMdd, MMdd, ddMM) |
| 변경 시 | 기존 비밀번호와 동일 불가 |

---

## 패키지 구조

```
com.loopers
├── application/user
│   ├── UserFacade.java
│   ├── UserFacadeDto.java
│   └── command/
│       ├── AuthenticateCommand.java
│       ├── ChangePasswordCommand.java
│       └── RegisterCommand.java
├── domain/user
│   ├── User.java
│   ├── UserService.java
│   ├── UserAuthService.java
│   ├── UserRepository.java
│   ├── PasswordEncoder.java
│   ├── vo/
│   │   ├── UserId.java
│   │   ├── Password.java
│   │   ├── Email.java
│   │   ├── Name.java
│   │   └── BirthDate.java
│   └── exception/
│       └── UserValidationException.java
├── infrastructure/user
│   ├── UserEntity.java
│   ├── UserJpaRepository.java
│   └── UserRepositoryImpl.java
└── interfaces/api/user
    ├── UserController.java
    ├── UserApiSpec.java
    └── UserDto.java
```

---

## 설계 포인트

1. **Value Object 분리**: Password, Email 등 검증 로직을 VO에 캡슐화
2. **도메인 서비스 분리**: UserService(CUD), UserAuthService(인증) 책임 분리
3. **Facade 패턴**: 비밀번호 변경 시 인증 + 변경을 조합
4. **Infrastructure 분리**: JPA Entity와 Domain 모델 분리 (toDomain/from 변환)
