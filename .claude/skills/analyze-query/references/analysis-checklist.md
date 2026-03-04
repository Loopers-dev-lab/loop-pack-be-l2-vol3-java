# 분석 체크리스트 (4축 × 16항목)

각 항목의 상세 확인 방법과 코드 패턴을 정의합니다.

---

## 축 1: Transaction Boundary (TB1~TB3)

트랜잭션의 시작 지점과 범위를 파악하는 기초 분석입니다. 이 축의 결과물은 나머지 3개 축의 입력이 됩니다.

### TB1: 시작 지점

#### 확인 방법
1. Controller → Facade → Service 호출 체인을 따라간다
2. `@Transactional`이 최초로 선언된 계층을 식별한다
3. 프로젝트 규칙: Facade가 트랜잭션 시작 지점 (architecture.md 참조)

#### 판정
- **OK**: Facade 또는 Service에서 시작 (프로젝트 규칙 준수)
- **ISSUE**: Controller에서 시작 → OT1로 연결
- **N/A**: @Transactional 선언 없음 (조회 전용 등)

#### 코드 패턴
```java
// OK — Facade에서 시작
@Component
public class OrderFacade {
    @Transactional
    public OrderInfo placeOrder(OrderCommand.Place command) { ... }
}

// ISSUE — Controller에서 시작
@RestController
@Transactional  // 여기서 시작하면 안 됨
public class OrderController { ... }
```

---

### TB2: 작업 분류

#### 확인 방법
트랜잭션 내부에서 수행되는 모든 작업을 세 가지로 분류한다:

| 분류 | 기준 | 예시 |
|------|------|------|
| 쓰기 | Entity 상태 변경, Repository save/delete | `order.create()`, `productRepository.save()` |
| 조회 | Repository find/exists, Entity 조회 메서드 | `productService.getById()`, `existsByName()` |
| 외부호출 | HTTP 클라이언트, 메시지 발행, 파일 I/O | `paymentClient.request()`, `kafkaTemplate.send()` |

#### 판정
- **OK**: 작업이 명확히 분류되고 적절한 트랜잭션에 포함
- **WARN**: 조회가 쓰기 트랜잭션에 다수 포함 → OT2로 연결
- **ISSUE**: 외부호출이 트랜잭션에 포함 → OT3로 연결

---

### TB3: 범위 시각화

#### 출력 형식
각 유스케이스(public 메서드)별로 호출 트리를 작성한다.

```
placeOrder() — @Transactional (OrderFacade)
  ├─ userService.getById(userId)              [조회]
  ├─ productService.deductStocks(items)       [쓰기] 🔒 비관적 락
  ├─ couponService.use(couponId)              [쓰기] 🔒 비관적 락
  ├─ Order.create(...)                        [쓰기]
  └─ orderService.create(order)               [쓰기]
```

#### 표기 규칙
- `[조회]` `[쓰기]` `[외부호출]` — TB2 분류 표시
- `🔒 비관적 락` / `🔒 낙관적 락` — 락이 있는 경우 표시
- `@Transactional` / `@Transactional(readOnly = true)` — 선언 표시
- 타 도메인 호출은 `{도메인}Service.{메서드}()` 형태로 표시

#### 판정
- TB3은 시각화 자체이므로 판정 없이 항상 출력한다 (OK 처리)

---

## 축 2: 불필요한 범위 (OT1~OT5)

트랜잭션이 불필요하게 크게 잡혀 있는 안티패턴을 식별합니다.

### OT1: Controller @Transactional

#### 확인 방법
Controller 클래스와 메서드에 `@Transactional` 어노테이션이 있는지 확인한다.

#### 판정
- **OK**: Controller에 @Transactional 없음
- **ISSUE**: Controller에 @Transactional 존재

#### 위험
- HTTP 직렬화/역직렬화, 인증 처리까지 트랜잭션에 포함
- DB 커넥션 점유 시간 증가

---

### OT2: 읽기/쓰기 혼합

#### 확인 방법
1. TB3 트리에서 `[쓰기]` 작업의 위치를 확인한다
2. `[쓰기]` 전후로 불필요한 `[조회]`가 많은지 판단한다

#### 판정 기준
- **OK**: 조회가 쓰기의 전제조건으로만 사용됨 (검증용 조회)
- **WARN**: 쓰기 이후에 표현용 조회가 트랜잭션 내에 포함됨
- **N/A**: 조회 전용 메서드 (readOnly = true)

#### 프로젝트 맥락
이 프로젝트에서 Facade는 명령 메서드 내에서 검증용 조회 → 쓰기 → Info 변환을 수행한다.
검증/쓰기에 필요한 조회는 정상 패턴이므로 OK이다.
쓰기와 무관한 부가 조회(추천 상품 등)가 포함되면 WARN이다.

#### 코드 패턴
```java
// OK — 검증용 조회 + 쓰기
@Transactional
public OrderInfo placeOrder(OrderCommand.Place command) {
    User user = userService.getById(command.userId());       // 검증용 조회
    List<Product> products = productService.deductStocks(...); // 쓰기
    Order order = orderService.create(...);                    // 쓰기
    return OrderInfo.from(order);
}

// WARN — 쓰기 후 무관한 조회
@Transactional
public OrderInfo placeOrder(OrderCommand.Place command) {
    Order order = orderService.create(...);                    // 쓰기
    List<Product> recommended = productService.getRecommended(); // 표현용 조회
    return OrderInfo.from(order, recommended);
}
```

---

### OT3: 외부 시스템 호출 포함

#### 확인 방법
트랜잭션 메서드 내에서 다음 패턴을 탐색한다:
- `RestTemplate`, `WebClient`, `FeignClient` 호출
- `KafkaTemplate.send()`, `RabbitTemplate.convertAndSend()` 등 메시지 발행
- 파일 I/O, 외부 캐시 조작

#### 판정
- **OK**: 외부 호출 없음
- **ISSUE**: 외부 호출이 트랜잭션 내부에 포함
- **N/A**: 해당 유스케이스에 외부 연동 없음

#### 위험
- 외부 시스템 응답 지연 → DB 커넥션 장시간 점유
- 외부 호출 실패 → 롤백되지만 부작용(이미 보낸 메시지 등) 회수 불가

---

### OT4: 대량 조회

#### 확인 방법
트랜잭션 내부에서 다음 패턴을 탐색한다:
- `findAll()` 또는 조건 없는 전체 조회
- QueryDSL `fetchResults()`, 페이징 없는 리스트 조회
- 반복문 내부의 개별 조회 (N+1 의심)

#### 판정
- **OK**: 조회 범위가 제한적 (PK 조회, 소량 IN 조회)
- **WARN**: 대량 데이터 조회가 쓰기 트랜잭션에 포함
- **N/A**: QueryDSL 미사용, 대량 조회 없음

---

### OT5: 긴 트랜잭션 유지

#### 확인 방법
1. TB3 트리에서 마지막 `[쓰기]` 작업 이후에 추가 작업이 있는지 확인한다
2. 불필요하게 넓은 범위의 메서드에 @Transactional이 걸려 있는지 확인한다

#### 판정
- **OK**: 쓰기 완료 후 즉시 반환 또는 Info 변환만 수행
- **WARN**: 쓰기 완료 후 추가 조회/계산이 트랜잭션 내에서 수행

#### 프로젝트 맥락
이 프로젝트에서 Entity → Info 변환은 getter 호출이므로 추가 쿼리 없이 OK이다.
단, Lazy Loading 연관이 있으면 Info 변환 시 추가 쿼리 발생 → PC3로 연결.

---

## 축 3: 영속성 컨텍스트 (PC1~PC5)

JPA 영속성 컨텍스트의 동작이 의도와 일치하는지 점검합니다.

### PC1: flush 시점

#### 확인 방법
1. 트랜잭션 내에서 Entity를 변경(setter, 상태 전이 메서드)한 뒤 JPQL/QueryDSL 조회가 있는지 확인한다
2. `FlushModeType`이 명시적으로 설정되었는지 확인한다

#### 동작 원리
- JPA 기본 FlushMode: `AUTO` — JPQL 실행 전에 자동 flush
- 변경 → JPQL 조회 순서이면 중간에 flush 발생 (의도된 것인지 확인 필요)
- `save()` 호출 ≠ INSERT 즉시 실행 (flush 시점에 SQL 발생)

#### 판정
- **OK**: flush 시점이 의도와 일치하거나, 순수 save 후 반환
- **WARN**: 변경 후 JPQL 조회가 있어 중간 flush 발생 가능
- **N/A**: Entity 변경 없는 조회 전용 메서드

---

### PC2: 변경 감지 범위

#### 확인 방법
1. 조회한 Entity 중 변경하지 않는 것이 있는지 확인한다
2. 조회 전용 Entity가 영속성 컨텍스트에 남아 dirty checking 대상이 되는지 판단한다

#### 판정
- **OK**: 모든 조회 Entity가 변경되거나, readOnly 트랜잭션에서 조회
- **WARN**: 변경하지 않는 Entity가 쓰기 트랜잭션에서 조회됨 (스냅샷 유지 비용)
- **N/A**: Entity 조회 없음

#### 코드 패턴
```java
// WARN — user는 조회만 하지만 dirty checking 대상
@Transactional
public OrderInfo placeOrder(...) {
    User user = userService.getById(userId);  // 조회만, 변경 안 함
    Order order = Order.create(user.getId(), ...);
    orderRepository.save(order);
    return OrderInfo.from(order);
}
```

---

### PC3: 지연 로딩

#### 확인 방법
1. Entity의 연관관계 매핑을 확인한다 (`@OneToMany`, `@ManyToOne` 등)
2. `FetchType.LAZY`인 연관관계가 트랜잭션 종료 시점 이후에 접근되는지 확인한다
3. Info/Response 변환 시 Lazy 연관에 접근하는지 확인한다

#### 판정
- **OK**: Lazy 연관이 트랜잭션 내에서 접근되거나, fetch join으로 미리 로딩
- **WARN**: Info 변환 시 Lazy 연관 접근 → 트랜잭션 내이므로 동작하나 N+1 위험
- **ISSUE**: 트랜잭션 외부에서 Lazy 연관 접근 → LazyInitializationException 위험
- **N/A**: 연관관계 없음

---

### PC4: readOnly 미적용

#### 확인 방법
1. 조회 전용 메서드에 `@Transactional(readOnly = true)`가 적용되었는지 확인한다
2. 프로젝트 규칙: 메서드 레벨 개별 선언 (architecture.md 참조)

#### 판정
- **OK**: 조회 메서드에 readOnly = true 적용
- **WARN**: 조회 전용인데 readOnly 없음 (성능 최적화 누락)
- **N/A**: 명령 메서드 (readOnly 불필요)

#### readOnly = true 효과
- Hibernate: 스냅샷 저장 생략 → 메모리 절약
- JDBC: 읽기 전용 힌트 → DB 최적화 가능 (MySQL: 트랜잭션 ID 할당 생략)

---

### PC5: DTO Projection 적합성

#### 확인 방법
1. 조회 결과가 Entity 전체가 아닌 일부 필드만 사용하는지 확인한다
2. 조회 후 변경 없이 DTO 변환만 수행하는지 확인한다

#### 판정
- **OK**: Entity 조회 후 상태 변경이 필요하거나, 전체 필드를 활용
- **WARN**: 조회 전용 + 일부 필드만 사용 → DTO Projection이 더 효율적
- **N/A**: 명령 메서드 (Entity 조회 후 변경 필요)

#### 프로젝트 맥락
이 프로젝트는 ApplicationService의 조회를 위한 조회(QueryService)에서 DTO Projection 검토가 적합하다.
명령용 조회는 Entity가 필요하므로 N/A이다.

---

## 축 4: 쿼리 실행 (QE1~QE3)

실제 발생하는 SQL 쿼리의 효율성과 동시성 제어를 점검합니다.

### QE1: N+1 쿼리

#### 확인 방법
1. `@OneToMany`, `@ManyToMany` 연관관계를 확인한다
2. 컬렉션을 순회하며 연관 Entity에 접근하는 코드를 찾는다
3. fetch join, `@EntityGraph`, `@BatchSize` 적용 여부를 확인한다

#### 판정
- **OK**: fetch join 또는 @EntityGraph로 연관 로딩, 또는 컬렉션 미접근
- **WARN**: @BatchSize 적용 (N+1은 아니지만 배치 쿼리 발생)
- **ISSUE**: 컬렉션 순회 시 Lazy 연관에 개별 접근 → N+1 발생
- **N/A**: 컬렉션 연관관계 없음

#### 코드 패턴
```java
// ISSUE — N+1 발생
List<Order> orders = orderRepository.findByUserId(userId);
for (Order order : orders) {
    order.getOrderItems().size();  // 주문마다 SELECT 발생
}

// OK — fetch join
@Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.userId = :userId")
List<Order> findByUserIdWithItems(@Param("userId") Long userId);
```

---

### QE2: 중복 조회

#### 확인 방법
1. 같은 Entity를 동일 트랜잭션 내에서 여러 번 조회하는지 확인한다
2. 영속성 컨텍스트의 1차 캐시로 커버되는지 판단한다

#### 판정
- **OK**: 동일 PK 조회 → 1차 캐시 히트 (실제 쿼리 발생 안 함)
- **WARN**: 다른 조건으로 같은 Entity 조회 → 추가 쿼리 발생 가능
- **N/A**: 중복 조회 패턴 없음

#### 영속성 컨텍스트 1차 캐시 동작
- `findById()` → PK 기반 조회 → 1차 캐시에 있으면 SQL 발생 안 함
- JPQL/QueryDSL → 항상 SQL 발생 → 결과를 1차 캐시와 병합

---

### QE3: 락 전략

#### 확인 방법
1. 동시성 이슈가 발생할 수 있는 지점을 식별한다:
   - 재고 차감 (lost update)
   - 좋아요 카운트 증감 (lost update)
   - 쿠폰 사용 (중복 사용)
   - 유니크 제약 위반 가능성 (check-then-act)
2. 해당 지점에 적절한 락이 적용되었는지 확인한다:
   - 비관적 락: `@Lock(PESSIMISTIC_WRITE)`, `FOR UPDATE`
   - 낙관적 락: `@Version`
   - DB unique constraint

#### 판정
- **OK**: 동시성 위험 지점에 적절한 락 적용
- **WARN**: 락이 있으나 전략이 부적절 (예: 낙관적 락인데 충돌 빈번 예상)
- **ISSUE**: 동시성 위험 지점에 락 없음
- **N/A**: 동시성 이슈 없는 유스케이스

#### 비관적 락 데드락 방지
여러 Entity에 비관적 락을 거는 경우 **ID 오름차순 정렬** 후 락을 획득해야 한다.
```java
// OK — ID 정렬 후 락 획득
List<Long> sortedIds = productIds.stream().sorted().toList();
List<Product> products = productRepository.findAllByIdInForUpdate(sortedIds);

// ISSUE — 정렬 없이 락 획득 → 데드락 위험
List<Product> products = productRepository.findAllByIdInForUpdate(productIds);
```

#### MEMORY.md 연계
프로젝트의 MEMORY.md에 Lock 재도입 조사 내용이 있으면 함께 참조한다.
