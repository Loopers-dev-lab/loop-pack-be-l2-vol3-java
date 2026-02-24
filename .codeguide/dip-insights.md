# DIP & 아키텍처 인사이트

> 3주차 학습 과정에서 수집한 실무적 인사이트 정리

---

## 1. DIP 정석 vs 실무 타협

### DIP 정석 구조 (이미지: [Kev] DIP정석)

```
[Domain Layer]                    [Infrastructure Layer]
Order (순수 도메인 객체)            OrderEntity (@Entity, JPA)
OrderRepository (interface)  ◀──  OrderJpaRepositoryImpl (구현체)
```

- Domain Entity와 JPA Entity를 **완전 분리**
- Infrastructure에서 Entity 간 변환 처리

### 실무 타협 (이미지: [Kev] 장바구니 도메인 구현 사례)

**장바구니 사례**: Cart(Domain) / CartEntity(JPA) / CartRedisEntity(Redis)

- 다중 저장소(JPA + Redis) 지원 시 분리가 의미 있음
- CartRepository 인터페이스 하나로 JPA/Redis 모두 지원 가능

### 수강생 채팅에서 나온 분리의 비용

| 비용 | 내용 |
|------|------|
| 보일러플레이트 | Entity ↔ Domain 변환 로직(매퍼) 필요 |
| 더티체킹 포기 | JPA의 강력한 기능 못 씀, 명시적 save() 필요 |
| 클래스 폭발 | Order, OrderEntity 둘 다 관리 |
| 기능 제약 | JPA가 지원하는 편의 기능 활용 불가 |

---

## 2. DDD 저자의 실무 타협 (최범균, 도메인 주도 개발 시작하기)

### 명언 1: 변경이 거의 없는 상황에서 미리 대비하는 것은 과하다

> "DIP를 적용하는 주된 이유는 저수준 구현이 변경되더라도 고수준이 영향을 받지 않도록 하기 위함이다.
> 하지만, 리포지터리와 도메인 모델의 구현 기술은 거의 바뀌지 않는다.
> JPA로 구현한 리포지터리를 마이바티스나 다른 기술로 변경한 적이 없고,
> RDBMS를 사용하다 몽고DB로 변경한 적도 없다.
> 변경이 거의 없는 상황에서 변경을 미리 대비하는 것은 과하다고 생각한다."

### 명언 2: 복잡도를 높이지 않으면서 구조적 유연함 유지

> "JPA 전용 애너테이션을 사용하긴 했지만 도메인 모델을 단위 테스트하는 데 문제는 없다.
> 리포지터리도 마찬가지다. 스프링 데이터 JPA가 제공하는 Repository 인터페이스를 상속하고 있지만
> 리포지터리 자체는 인터페이스이고 테스트 가능성을 해치지 않는다.
> DIP를 완벽하게 지키면 좋겠지만 개발 편의성과 실용성을 가져가면서 구조적인 유연함은 어느정도 유지했다.
> 복잡도를 높이지 않으면서 기술에 따른 구현 제약이 낮다면 합리적인 선택이라고 생각한다."

---

## 3. 우리 과제에 적용할 인사이트

### 타협하는 부분 (실용성 우선)

| 항목 | 정석 | 우리의 타협 | 이유 |
|------|------|-----------|------|
| Domain Entity | 순수 POJO | @Entity 사용 | 더티체킹 활용, 보일러플레이트 감소 |
| VO | JPA 무관 | @Embeddable 사용 | 테스트에 영향 없음 |

### 지키는 부분 (구조적 유연함)

| 항목 | 적용 방식 | 이유 |
|------|----------|------|
| Repository Interface | Domain Layer에 정의 | 테스트 가능성 확보 (Fake 구현체 교체) |
| Repository 구현체 | Infrastructure Layer에 위치 | 의존 방향: Domain ← Infrastructure |
| Application Layer | Facade로 도메인 조합 | 유스케이스 조율과 비즈니스 로직 분리 |

### 판단 기준 (한 줄 요약)

```
"테스트 가능성을 해치지 않는 범위에서 타협한다"
```

- @Entity 사용해도 단위 테스트 가능? → ✅ 타협 OK
- Repository를 Infrastructure에서 직접 사용하면 테스트 어려움? → ❌ Interface 분리 필요
