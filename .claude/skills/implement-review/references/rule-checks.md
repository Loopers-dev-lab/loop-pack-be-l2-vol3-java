# 축 3: 규칙 준수 체크리스트 (R1~R9)

rules/ 파일 기준으로 아키텍처와 코딩 규칙 준수를 확인합니다.

---

## R1: Controller→Facade만

### 참조 규칙
`project/architecture.md` — "Controller는 항상 Facade만 호출"

### 확인 방법
Controller 클래스의 생성자 주입 또는 `@Autowired` 필드를 확인한다.
- Facade만 주입되어야 한다
- Service, Repository 직접 주입은 위반

### 위반 패턴
```java
@RestController
public class ProductController {
    private final ProductService productService;  // Service 직접 주입
}
```

### 올바른 패턴
```java
@RestController
public class ProductController {
    private final ProductFacade productFacade;  // Facade만 주입
}
```

---

## R2: Service private 금지

### 참조 규칙
`project/architecture.md` — "private 메서드 금지, 자가호출 금지"

### 확인 방법
ApplicationService 클래스(`xxxService`)에 `private` 접근제어자 메서드가 없는지 확인한다.

### 위반 패턴
```java
@Service
public class ProductService {
    public Product register(...) {
        validateDuplicate(name);  // private 헬퍼 호출
        ...
    }

    private void validateDuplicate(String name) { ... }  // 위반
}
```

### 올바른 패턴
```java
@Service
public class ProductService {
    @Transactional
    public Product register(...) {
        // 조회부터 실행까지 메서드 내에서 완결
        if (productRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "...");
        }
        ...
    }
}
```

---

## R3: DTO는 record

### 참조 규칙
`project/architecture.md` — "DTO: Java record 사용 (불변 보장)"

### 확인 방법
Request, Response, Info DTO 파일에서 `class` 대신 `record`로 선언되었는지 확인한다.

### 대상 파일
- `interfaces/` 패키지의 `*Dto.java` 내부 inner type
- `application/` 패키지의 `*Info.java`

### 위반 패턴
```java
public class ProductV1Dto {
    public static class RegisterRequest {  // class 사용
        private String name;
        // getter, setter...
    }
}
```

### 올바른 패턴
```java
public class ProductV1Dto {
    public record RegisterRequest(  // record 사용
        @NotBlank @Size(min = 1, max = 50) String name
    ) {}
}
```

---

## R4: 트랜잭션 전략

### 참조 규칙
`project/architecture.md` — 트랜잭션 전략

### 확인 방법

**ApplicationService**:
1. 클래스 레벨에 `@Transactional(readOnly = true)`가 있는지 확인한다
2. 명령 메서드(등록, 수정, 삭제)에 `@Transactional`(readOnly 없음)이 있는지 확인한다

**Facade**:
1. 클래스 레벨에 `@Transactional(readOnly = true)`가 있는지 확인한다
2. 명령 메서드에 `@Transactional`(readOnly 없음)이 있는지 확인한다

### 위반 패턴
```java
@Service
public class ProductService {  // 클래스 레벨 @Transactional 누락

    @Transactional
    public Product register(...) { ... }

    public Product getById(Long id) { ... }  // readOnly 없음
}
```

### 올바른 패턴
```java
@Service
@Transactional(readOnly = true)  // 클래스 레벨 기본
public class ProductService {

    @Transactional  // 명령 메서드 오버라이드
    public Product register(...) { ... }

    // 조회 메서드는 클래스 레벨 readOnly 상속
    public Product getById(Long id) { ... }
}
```

---

## R5: Command→Query 순서

### 참조 규칙
`conventions/code-ordering.md` — 계층별 적용

### 확인 방법
다음 파일에서 `// Command`와 `// Query` 주석이 있고, Command가 Query보다 먼저 나오는지 확인한다:
- Controller
- ApiSpec (인터페이스)
- Facade
- ApplicationService
- Repository (인터페이스)
- RepositoryImpl
- DTO 클래스 (`// Command` → `// Query` → `// Response`)

### 위반 패턴
```java
public class ProductController {
    // Query
    @GetMapping("/{id}")
    public ResponseEntity<...> getById(...) { ... }

    // Command
    @PostMapping
    public ResponseEntity<...> register(...) { ... }  // Query 뒤에 위치
}
```

### 올바른 패턴
```java
public class ProductController {
    // Command
    @PostMapping
    public ResponseEntity<...> register(...) { ... }

    // Query
    @GetMapping("/{id}")
    public ResponseEntity<...> getById(...) { ... }
}
```

---

## R6: Entity 멤버 순서

### 참조 규칙
`conventions/code-ordering.md` — Entity 멤버 순서

### 확인 방법
Entity 클래스의 멤버가 다음 순서를 지키는지 확인한다:

1. 상수 (`static final`)
2. 필드 (`@Column` 등)
3. 생성자 (protected 기본 → private)
4. 정적 팩토리 메서드 (`create()`)
5. 명령 메서드 (`update()`, `softDelete()`)
6. 조회 메서드 (`isDeleted()`, `isOrderable()`)
7. 검증 메서드 (public) (`validateNotDeleted()`)
8. private 메서드 (`validateName()`)

### 위반 패턴
```java
@Entity
public class Product {
    private String name;

    public static Product create(...) { ... }  // 팩토리가 생성자보다 먼저

    protected Product() {}  // 생성자가 팩토리 뒤에

    private void validateName() { ... }  // private이 명령보다 먼저

    public void update(...) { ... }
}
```

### 올바른 패턴
```java
@Entity
public class Product {
    // 필드
    private String name;

    // 생성자
    protected Product() {}
    private Product(String name) { ... }

    // 정적 팩토리
    public static Product create(...) { ... }

    // 명령
    public void update(...) { ... }

    // private
    private void validateName() { ... }
}
```

---

## R7: CoreException 사용

### 참조 규칙
`conventions/validation.md` — "비즈니스 예외는 CoreException으로 통일"

### 확인 방법
Entity와 ApplicationService에서 비즈니스 검증 실패 시 `CoreException(ErrorType, message)` 형태로 예외를 던지는지 확인한다.

### 위반 패턴
```java
// Entity에서 일반 예외 사용
if (name.length() > NAME_MAX_LENGTH) {
    throw new IllegalArgumentException("이름은 50자 이하여야 합니다");
}
```

### 올바른 패턴
```java
// CoreException 사용
if (name.length() > NAME_MAX_LENGTH) {
    throw new CoreException(ErrorType.BAD_REQUEST, "이름은 50자 이하여야 합니다");
}
```

### 예외
- Entity 생성자에서의 null 체크 등 프레임워크 수준 검증은 `IllegalArgumentException` 허용 가능
- 이 경우에도 `CoreException`이 선호됨

---

## R8: Repository 위치

### 참조 규칙
`project/architecture.md` — "인터페이스는 domain/에, 구현체는 infrastructure/에"

### 확인 방법
1. Repository 인터페이스가 `domain/` 패키지에 위치하는지 확인한다
2. Repository 구현체(`RepositoryImpl`)가 `infrastructure/` 패키지에 위치하는지 확인한다
3. JpaRepository가 `infrastructure/` 패키지에 위치하는지 확인한다

### 위반 패턴
```
infrastructure/
├── ProductRepository.java          # 인터페이스가 infrastructure에
├── ProductRepositoryImpl.java
└── ProductJpaRepository.java
```

### 올바른 패턴
```
domain/
└── ProductRepository.java          # 인터페이스는 domain에

infrastructure/
├── ProductRepositoryImpl.java      # 구현체는 infrastructure에
└── ProductJpaRepository.java
```

---

## R9: Facade Info DTO 변환

### 참조 규칙
`project/architecture.md` — "Entity → Info DTO 변환 담당", "Entity가 Controller에 노출되지 않음"

### 확인 방법
1. Facade가 Entity를 직접 반환하지 않고 Info DTO로 변환하는지 확인한다
2. Controller에서 Entity 타입을 직접 참조하지 않는지 확인한다
3. Info DTO가 `application/` 패키지에 위치하는지 확인한다

### 위반 패턴
```java
// Facade에서 Entity 직접 반환
public class ProductFacade {
    public Product register(...) {
        return productService.register(...);  // Entity 직접 반환
    }
}

// Controller에서 Entity 참조
public class ProductController {
    public ResponseEntity<Product> register(...) { ... }  // Entity 노출
}
```

### 올바른 패턴
```java
// Facade에서 Info DTO 변환
public class ProductFacade {
    public ProductInfo register(...) {
        Product product = productService.register(...);
        return ProductInfo.from(product);  // Info DTO로 변환
    }
}

// Controller에서 Info → Response 변환
public class ProductController {
    public ResponseEntity<ProductResponse> register(...) {
        ProductInfo info = productFacade.register(...);
        return ResponseEntity.ok(ProductResponse.from(info));
    }
}
```
