# ArchUnit - Architecture Testing

## 개요

ArchUnit을 사용하여 Clean Architecture 계층 규칙을 자동으로 검증합니다.

## 검증 규칙

### 1. 계층 간 의존성 규칙

#### Domain 계층
- ✅ 다른 계층에 의존하지 않음
- ✅ 순수한 비즈니스 로직만 포함
- ✅ Spring Framework에 의존하지 않음 (Service 제외)

#### Application 계층
- ✅ Domain에만 의존
- ✅ Infrastructure, Interfaces에 의존하지 않음

#### Infrastructure 계층
- ✅ Domain에만 의존
- ✅ Application, Interfaces에 의존하지 않음

#### Interfaces 계층
- ✅ 모든 계층에 의존 가능 (최상위 계층)
- ✅ 다른 앱(batch, streamer)에는 의존하지 않음

### 2. 명명 규칙

| 계층 | 클래스 타입 | 명명 규칙 |
|------|------------|----------|
| Interfaces | Controller | `*V1Controller` |
| Application | Facade | `*Facade` |
| Application | Info (DTO) | `*Info` |
| Domain | Service | `*Service` |
| Domain | Repository (Interface) | `*Repository` |
| Infrastructure | Repository (Impl) | `*RepositoryImpl` |
| Infrastructure | JPA Repository | `*JpaRepository` |

### 3. 어노테이션 규칙

| 계층 | 클래스 타입 | 필수 어노테이션 |
|------|------------|----------------|
| Interfaces | Controller | `@RestController` |
| Domain | Service | `@Service` |
| Domain | Repository (Interface) | 어노테이션 없음 |
| Infrastructure | Repository (Impl) | `@Repository` |

### 4. 패키지 규칙

- ✅ Entity는 `domain` 패키지에만 존재
- ✅ Domain 패키지는 Spring Framework에 의존하지 않음 (Service 제외)

### 5. DIP (Dependency Inversion Principle) 규칙

#### 핵심 원칙
- ✅ 고수준 모듈(Domain)은 저수준 모듈(Infrastructure)에 의존하지 않음
- ✅ 둘 다 추상화(Interface)에 의존
- ✅ 추상화는 구체적인 것에 의존하지 않음

#### 검증 규칙
- ✅ Domain은 인터페이스에만 의존 (구현체 의존 금지)
- ✅ Domain Repository는 인터페이스여야 함
- ✅ Infrastructure는 Domain 인터페이스를 구현
- ✅ Application은 Domain 인터페이스에만 의존 (구현체 의존 금지)
- ✅ Domain Service는 구체 클래스가 아닌 인터페이스에 의존
- ✅ Infrastructure 구현체는 Domain에서 직접 참조되지 않음

#### DIP 위배 예시 (금지)

```java
// ❌ 나쁜 예: Domain이 Infrastructure 구현체에 직접 의존
@Service
public class UserService {
    private final UserRepositoryImpl userRepository; // 구현체 의존!
}

// ❌ 나쁜 예: Domain이 JpaRepository에 직접 의존
@Service
public class UserService {
    private final UserJpaRepository userJpaRepository; // JPA 구현체 의존!
}
```

#### DIP 준수 예시 (권장)

```java
// ✅ 좋은 예: Domain이 인터페이스에 의존
@Service
public class UserService {
    private final UserRepository userRepository; // 인터페이스 의존!
}

// ✅ 좋은 예: Infrastructure가 Domain 인터페이스를 구현
@Repository
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;
}
```

## 테스트 실행

```bash
# 전체 ArchUnit 테스트 실행
./gradlew :apps:commerce-api:test --tests "com.loopers.architecture.*"

# 특정 테스트만 실행
./gradlew :apps:commerce-api:test --tests "com.loopers.architecture.CleanArchitectureTest"
```

## 테스트 구조

```
CleanArchitectureTest
├── 계층 간 의존성 규칙
│   ├── Domain 계층은 다른 계층에 의존하지 않는다
│   ├── Application 계층은 Domain에만 의존한다
│   ├── Infrastructure 계층은 Domain에만 의존한다
│   ├── Interfaces 계층은 모든 계층에 의존할 수 있다
│   └── 계층 구조가 올바르게 정의되어 있다
├── 명명 규칙
│   ├── Controller는 V1Controller로 끝나야 한다
│   ├── Facade는 Facade로 끝나야 한다
│   ├── Service는 Service로 끝나야 한다
│   └── Repository 구현체는 RepositoryImpl로 끝나야 한다
├── 어노테이션 규칙
│   ├── Controller는 @RestController를 가져야 한다
│   ├── Service는 @Service를 가져야 한다
│   ├── Repository 인터페이스는 어노테이션이 없어야 한다
│   └── Repository 구현체는 @Repository를 가져야 한다
├── 패키지 규칙
│   ├── Domain 패키지는 Spring Framework에 의존하지 않는다
│   └── Entity는 domain 패키지에만 존재한다
└── DIP (Dependency Inversion Principle) 규칙
    ├── Domain은 인터페이스에만 의존해야 한다 (구현체 의존 금지)
    ├── Domain Repository는 인터페이스여야 한다
    ├── Infrastructure는 Domain 인터페이스를 구현해야 한다
    ├── Application은 Domain 인터페이스에만 의존해야 한다
    ├── Domain Service는 구체 클래스가 아닌 인터페이스에 의존해야 한다
    └── Infrastructure 구현체는 Domain에서 직접 참조되지 않아야 한다
```

## 규칙 위반 시

테스트가 실패하면 다음과 같은 메시지를 확인할 수 있습니다:

```
Architecture Violation [Priority: MEDIUM] - Rule 'classes that reside in a package '..domain..' 
should not depend on classes that reside in any package ['..interfaces..', '..application..', 
'..infrastructure..']' was violated (1 times):
Method <com.loopers.domain.user.UserService.someMethod()> calls method 
<com.loopers.application.user.UserFacade.someMethod()> in (UserService.java:42)
```

## 새로운 규칙 추가

새로운 아키텍처 규칙을 추가하려면 `CleanArchitectureTest.java`에 `@Nested` 클래스를 추가하세요:

```java
@Nested
@DisplayName("새로운 규칙 카테고리")
class NewRuleCategory {
    
    @Test
    @DisplayName("새로운 규칙 설명")
    void newRule() {
        ArchRule rule = classes()
                .that().resideInAPackage("..somepackage..")
                .should().doSomething();
        
        rule.check(importedClasses);
    }
}
```

## 참고 자료

- [ArchUnit 공식 문서](https://www.archunit.org/)
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
