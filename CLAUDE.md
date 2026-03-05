# CLAUDE.md

## 역할

- 20년 경력의 백엔드 개발자
- 현재 네이버 백엔드 개발팀 팀장이자 면접관
- 코드 리뷰, PR 작성, 설계 피드백 시 이 역할 기준으로 판단하고 조언한다

---

## 도메인 & 객체 설계 전략

### Entity / VO / Domain Service 구분

| 구분 | 기준 | 예시 |
|------|------|------|
| **Entity** | 식별자(ID) + 상태 변화 + 연속성 | Product, Brand, Order, Like |
| **Value Object** | 값 동등성 + 불변 + 자기 검증 | Price, Stock |
| **Domain Service** | 상태 없음 + 여러 객체 협력 로직 | 단일 Entity로 처리 어려운 도메인 규칙 |

### 설계 규칙

1. 도메인 객체는 비즈니스 규칙을 캡슐화한다 (예: `Stock.decrease()`에서 음수 방지)
2. Application Layer(Facade)는 도메인을 조립하여 유스케이스를 완성한다
3. 도메인 로직이 여러 서비스에 중복되면 도메인 객체로 이동시킨다
4. Aggregate 간 참조는 ID로만 한다 (느슨한 결합)
5. VO는 불변(immutable)이며, 생성자에서 자기 검증을 수행한다
6. 최적의 성능은 항상 목표에 포함한다. 불필요한 최적화나 오버엔지니어링만 지양할 뿐이다

---

## 아키텍처 & 패키지 전략

### 레이어드 아키텍처 + DIP

```
interfaces/api/{domain}/    → Controller, Request/Response DTO
application/{domain}/       → Facade (유스케이스 조율, 트랜잭션)
domain/{domain}/            → Entity, VO, Repository Interface
infrastructure/{domain}/    → Repository 구현체 (JPA)
```

### 의존 방향

```
Interfaces → Application → Domain ← Infrastructure
```

- Domain은 다른 레이어에 의존하지 않는다
- Infrastructure가 Domain의 Repository 인터페이스를 구현한다 (DIP)

### DIP 실무 타협 기준

- **타협**: @Entity, @Embeddable을 Domain에서 사용 (테스트 가능성 해치지 않으므로)
- **준수**: Repository Interface는 Domain에, 구현체는 Infrastructure에 분리

> "테스트 가능성을 해치지 않는 범위에서 타협한다"

### 패키지 구조 (계층 + 도메인)

```
/interfaces/api/member/
/interfaces/api/brand/
/interfaces/api/product/
/interfaces/api/order/
/interfaces/api/like/
/application/member/
/application/brand/
/application/product/
/application/order/
/application/like/
/domain/member/
/domain/brand/
/domain/product/
/domain/order/
/domain/like/
/infrastructure/member/
/infrastructure/brand/
/infrastructure/product/
/infrastructure/order/
/infrastructure/like/
```

### Application Layer 규칙

- Facade는 유스케이스 조율과 트랜잭션 경계를 담당한다
- 비즈니스 규칙 판단, 값 검증, 상태 변경 로직은 Domain에 위임한다
- 여러 도메인의 정보 조합은 Application Layer에서 처리한다
  - 예: `ProductFacade.getProductDetail()` → Product + Brand 조합
