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

```plantuml
@startuml Domain Architecture
skinparam packageStyle rectangle

package "Interface Layer" {
  [UserController]
  [AuthUserArgumentResolver]
}

package "Application Layer" {
  [UserFacade]
}

package "Domain Layer" {
  [UserService]
  [UserAuthService]
  [User]
  [PasswordEncoder] <<interface>>
}

package "Infrastructure Layer" {
  [UserEntity]
  [UserRepositoryImpl]
  [BCryptPasswordEncoder]
}

[UserController] --> [UserFacade]
[UserController] --> [UserService]
[UserFacade] --> [UserAuthService]
[UserFacade] --> [UserService]
[UserService] --> [UserRepository]
[UserAuthService] --> [UserRepository]
[UserRepositoryImpl] ..|> [UserRepository]
[BCryptPasswordEncoder] ..|> [PasswordEncoder]
@enduml
```

---

## 시퀀스 다이어그램

### 1. 회원가입

```plantuml
@startuml Register Sequence
actor Client
participant UserController
participant UserService
participant UserRepository
database DB

Client -> UserController: POST /api/v1/users
UserController -> UserService: register(command)
UserService -> UserService: VO 검증 (Password, Email 등)
UserService -> UserRepository: existsByUserId()
UserRepository -> DB: SELECT
DB --> UserRepository: false
UserService -> UserService: 비밀번호 암호화 (BCrypt)
UserService -> UserRepository: save(user)
UserRepository -> DB: INSERT
DB --> UserRepository: OK
UserService --> UserController: User
UserController --> Client: 201 Created
@enduml
```

### 2. 비밀번호 변경

```plantuml
@startuml Change Password Sequence
actor Client
participant UserController
participant UserFacade
participant UserAuthService
participant UserService
participant UserRepository
database DB

Client -> UserController: PATCH /api/v1/users/me/password
note right: Header: Authorization (Basic Auth)
UserController -> UserFacade: changePassword(request)
UserFacade -> UserAuthService: authenticate(command)
UserAuthService -> UserRepository: findByUserId()
UserRepository -> DB: SELECT
DB --> UserRepository: UserEntity
UserAuthService -> UserAuthService: 비밀번호 검증
UserAuthService --> UserFacade: User

UserFacade -> UserService: changePassword(command)
UserService -> UserService: 신규 비밀번호 검증
note right: - 8~16자\n- 대/소문자, 숫자, 특수문자\n- 생년월일 미포함\n- 기존 비밀번호와 다름
UserService -> UserService: BCrypt 암호화
UserService -> UserRepository: save(user)
UserRepository -> DB: UPDATE
DB --> UserRepository: OK
UserService --> UserFacade: void
UserFacade --> UserController: void
UserController --> Client: 200 OK
@enduml
```

---

## 도메인 모델

```plantuml
@startuml Domain Model
skinparam classAttributeIconSize 0

class User <<record>> {
  - id: UserId
  - password: Password
  - name: Name
  - email: Email
  - birthDate: BirthDate
  --
  + getMaskedName(): String
}

class UserId <<VO>> {
  - value: String
  --
  검증: 4~20자, 영문소문자+숫자
}

class Password <<VO>> {
  - value: String
  --
  검증: 8~16자
  대/소문자, 숫자, 특수문자 필수
  --
  + containsDate(date): boolean
  + isEncoded(): boolean
  + ofEncoded(String): Password
}

class Email <<VO>> {
  - value: String
  --
  검증: 이메일 형식
}

class Name <<VO>> {
  - value: String
  --
  검증: 2~10자, 한글만
}

class BirthDate <<VO>> {
  - value: LocalDate
  --
  검증: 과거 날짜만
}

User *-- UserId
User *-- Password
User *-- Name
User *-- Email
User *-- BirthDate
@enduml
```

---

## ERD

```plantuml
@startuml ERD
entity "users" as users {
  * id : BIGINT <<PK, AUTO_INCREMENT>>
  --
  * user_id : VARCHAR(20) <<UNIQUE>>
  * password : VARCHAR(255)
  * name : VARCHAR(10)
  * email : VARCHAR(255)
  * birth_date : DATE
  * created_at : DATETIME
  * updated_at : DATETIME
}
@enduml
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
