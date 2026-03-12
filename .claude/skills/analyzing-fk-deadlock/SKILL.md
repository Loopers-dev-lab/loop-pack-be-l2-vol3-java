---
name: detect-fk-deadlock
description: >
  접시 프로젝트 (Spring Boot + JPA + MySQL InnoDB) 코드를 탐색하여
  외래키(FK) 잠금전파로 인한 묵시적 데드락 발생 가능 지점을 점검하고,
  @Version 낙관락이 실제로 X lock을 잡는지 확인한다.
  문제가 없으면 학습용 재현 코드와 SHOW ENGINE INNODB STATUS 관찰 가이드를 제공한다.
  
  다음 키워드가 포함된 요청에 반드시 사용한다:
  FK 데드락, 외래키 잠금, 외래키 전파, lock propagation, FK lock,
  낙관락 데드락, @Version 락, S lock 업그레이드, lock upgrade,
  SHOW ENGINE INNODB STATUS, InnoDB 락 로그, 락 로그 확인,
  묵시적 락, implicit lock, FK 없이 데드락, 락 안잡았는데 데드락
---

## 📋 이 스킬의 목적

이 스킬은 3단계로 동작한다.

```
[PHASE 1] 실제 코드 점검
  → FK를 가진 @ManyToOne 엔티티를 찾는다
  → @Version 사용 여부를 확인한다
  → 같은 트랜잭션에서 FK INSERT + 부모 UPDATE가 함께 있는 패턴을 찾는다

[PHASE 2] 문제 발생 여부 판정
  → 위험 패턴이 있으면: 실제 데드락 발생 조건 설명
  → 없으면: 학습용으로 해당 패턴을 만들어 재현하는 코드를 제공

[PHASE 3] 직접 관찰 가이드
  → MySQL SHOW ENGINE INNODB STATUS로 락 상태를 실시간으로 읽는 방법
  → 어떤 줄을 봐야 하는지, 무엇을 의미하는지 해석 가이드
```

---

## PHASE 1 — 실제 프로젝트 코드 탐색

### Step 1-1. @ManyToOne (FK) 엔티티 목록 수집

아래 명령을 실행하여 FK를 가진 필드를 전부 찾는다.

```bash
# @ManyToOne이 선언된 파일과 라인을 모두 출력
grep -rn "@ManyToOne" src/ --include="*.java" -A 3

# @JoinColumn까지 같이 확인
grep -rn "@JoinColumn" src/ --include="*.java" -A 2
```

**체크 포인트:**
- `nullable = false` 인 FK 컬럼이 있는가? → InnoDB FK 체크가 반드시 발생
- 어떤 엔티티가 어떤 엔티티를 참조하는가? (자식→부모 방향 지도 그리기)

**출력 예시 해석:**
```
OrderItem → Product  (FK: product_id)
OrderItem → Order    (FK: order_id)
OwnedCoupon → Coupon (FK: coupon_id)
OwnedCoupon → User   (FK: user_id)
```
이 중 **부모 테이블에 UPDATE가 발생하는 관계**가 위험 대상이다.

---

### Step 1-2. 위험 패턴 탐색 — "FK INSERT + 부모 UPDATE"가 같은 트랜잭션에 있는가?

```bash
# @Transactional이 붙은 서비스 메서드에서
# 자식 엔티티 save + 부모 엔티티 필드 변경이 함께 있는 파일 찾기
grep -rn "save\|persist\|merge" src/ --include="*.java" -l | xargs grep -l "@Transactional"
```

수동으로 다음 패턴을 확인한다:

```java
// ⚠️ 위험 패턴 예시
@Transactional
public void placeOrder(...) {
    orderItemRepository.save(item);   // (1) FK INSERT → product에 S lock 자동 획득
    product.decreaseStock(quantity);  // (2) UPDATE → product에 X lock 필요
    // (1)과 (2)가 동시에 두 TX에서 실행되면 S lock 업그레이드 경합 → 데드락
}
```

**찾아야 할 조건:**
1. 같은 `@Transactional` 메서드 안에
2. 자식 엔티티(`OrderItem`, `OwnedCoupon` 등) **INSERT** 와
3. 그 FK가 참조하는 부모 엔티티(`Product`, `Coupon` 등)의 **UPDATE** 가 공존

---

### Step 1-3. @Version 낙관락 사용 확인

```bash
grep -rn "@Version" src/ --include="*.java" -B 2 -A 2
```

**확인 사항:**
- `@Version` 이 붙은 엔티티가 FK로 참조되는 **부모 테이블**인가?
- 해당 엔티티의 UPDATE가 자식 INSERT와 같은 트랜잭션에서 발생하는가?

**핵심 이해:**
> `@Version`은 충돌을 **감지**하는 것이지, **락을 없애는** 것이 아니다.
> JPA는 `UPDATE product SET stock=?, version=version+1 WHERE id=? AND version=?`
> 이 UPDATE를 실행할 때 여전히 InnoDB **X lock**을 획득한다.
> S lock(FK체크) → X lock(UPDATE) 업그레이드 경합은 @Version과 무관하게 발생한다.

---

## PHASE 2-A — 위험 패턴이 발견된 경우

### 데드락 조건 확인 체크리스트

```
□ 자식 INSERT와 부모 UPDATE가 같은 @Transactional 범위 안에 있는가?
□ 동일한 부모 레코드(같은 product_id 등)에 대해 동시 요청이 가능한가?
□ FK 컬럼에 nullable = false 인가? (true면 FK 체크 자체를 건너뜀)
□ 트랜잭션 격리 수준이 REPEATABLE_READ인가? (기본값, S lock 발생 조건)
```

### 현재 코드 기반 위험도 판정 출력 형식

```markdown
## FK 데드락 위험 분석 결과

### 발견된 위험 지점
- 파일: OrderService.java (placeOrder 메서드)
- 패턴: OrderItem INSERT (FK: product_id) + Product UPDATE (decreaseStock) 동일 TX
- 위험도: 🔴 HIGH — 동시 주문 시 데드락 재현 가능

### 데드락 발생 조건
1. 두 TX가 동일 product_id의 OrderItem을 동시에 INSERT
2. 두 TX 모두 FK 체크로 product(id=N)에 S lock 획득
3. 두 TX 모두 product UPDATE 시도 → X lock 필요
4. TX1은 TX2의 S lock을 기다리고, TX2는 TX1의 S lock을 기다림
→ InnoDB가 하나를 ROLLBACK (victim 선정)

### @Version 영향
현재 Product에 @Version이 있음.
→ S lock → X lock 업그레이드 경합은 @Version과 무관하게 발생 확인됨.
```

---

## PHASE 2-B — 위험 패턴이 없는 경우 (학습용 재현 코드)

실제 프로젝트에 위험 패턴이 없더라도 학습을 위해 다음 코드로 재현한다.

### 학습용 엔티티 구성

```java
// Product.java — FK 부모 테이블 (재고 보유)
@Entity
@Table(name = "product")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int stock;
    
    // 낙관락 시나리오용 — 이것만 추가해서 테스트
    @Version
    private Long version;
    
    public void decreaseStock(int qty) {
        if (this.stock < qty) throw new IllegalStateException("재고 부족");
        this.stock -= qty;
    }
}

// OrderItem.java — FK 자식 테이블 (여기서 S lock 전파됨)
@Entity
@Table(name = "order_item")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ← 이 FK INSERT가 product에 묵시적 S lock을 건다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    private int quantity;
}
```

### 학습용 서비스 — 위험 패턴을 의도적으로 만든 버전

```java
// DeadlockDemoService.java
@Service
@RequiredArgsConstructor
public class DeadlockDemoService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * ⚠️ 데드락 재현용 메서드
     * 같은 productId로 동시에 두 스레드가 호출하면 데드락 발생
     */
    @Transactional
    public void placeOrderWithDeadlockRisk(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("상품 없음"));

        Order order = new Order(OrderStatus.PENDING);
        orderRepository.save(order);

        // (1) FK INSERT → InnoDB가 product(id=productId)에 S lock 자동 획득
        OrderItem item = new OrderItem(order, product, quantity, product.getPrice());
        orderItemRepository.save(item);

        // (2) 부모 엔티티 UPDATE → X lock 필요 → S lock 업그레이드 경합!
        product.decreaseStock(quantity);  // dirty checking → UPDATE 발생
        
        // @Version 있으면: UPDATE product SET stock=?, version=? WHERE id=? AND version=?
        // @Version 없어도: UPDATE product SET stock=? WHERE id=?
        // 어느 쪽이든 X lock 획득 시도 → S lock과 충돌
    }
}
```

### 동시성 테스트 코드

```java
// DeadlockTest.java
@SpringBootTest
class DeadlockTest {

    @Autowired
    private DeadlockDemoService service;

    @Test
    @DisplayName("FK S lock + 부모 UPDATE X lock 업그레이드 경합 → 데드락 재현")
    void fkDeadlockTest() throws InterruptedException {
        Long productId = 1L; // 미리 재고 100개로 세팅
        int threadCount = 2; // 딱 2개로도 재현됨
        
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        List<String> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGate.await(); // 동시 시작
                    service.placeOrderWithDeadlockRisk(productId, 1);
                    results.add("SUCCESS");
                } catch (Exception e) {
                    // DeadlockLoserDataAccessException 또는 OptimisticLockException
                    results.add("FAIL: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startGate.countDown(); // 동시 출발
        endLatch.await(10, TimeUnit.SECONDS);
        
        System.out.println("=== 결과 ===");
        results.forEach(System.out::println);
        // 예상: 하나는 SUCCESS, 하나는 FAIL (DeadlockLoserDataAccessException)
    }
}
```

---

## PHASE 3 — SHOW ENGINE INNODB STATUS 실시간 관찰 가이드

### 관찰 환경 세팅

테스트 실행 **중에** 또는 직후에 MySQL에 접속하여 다음을 실행한다.

```sql
-- MySQL 접속
mysql -u root -p your_db

-- 1. 현재 InnoDB 전체 상태 출력 (데드락 로그 포함)
SHOW ENGINE INNODB STATUS\G
```

### 데드락 발생 후 로그 읽는 법

`LATEST DETECTED DEADLOCK` 섹션을 찾아서 아래 항목을 순서대로 읽는다.

```
------------------------
LATEST DETECTED DEADLOCK
------------------------
날짜 시간

*** (1) TRANSACTION:
TRANSACTION [TX_ID], ACTIVE [초] [동작]

*** (1) HOLDS THE LOCK(S):      ← ✅ 이 TX가 현재 보유 중인 락
RECORD LOCKS space id N page no N n bits N
  index PRIMARY of table `db`.`product`  ← 어느 테이블/인덱스
  trx id [TX_ID] lock_mode S             ← S = 공유락 (FK 체크로 획득)
  lock held waiting                      ← 대기 중

*** (1) WAITING FOR THIS LOCK TO BE GRANTED:  ← ✅ 이 TX가 원하는 락
RECORD LOCKS space id N page no N n bits N
  index PRIMARY of table `db`.`product`
  trx id [TX_ID] lock_mode X             ← X = 배타락 (UPDATE를 위해 필요)
  lock held waiting

*** (2) TRANSACTION:
...

*** WE ROLL BACK TRANSACTION (2)   ← InnoDB가 TX2를 희생자로 선택
```

### 읽어야 할 핵심 키워드 해설

| 키워드 | 의미 |
|--------|------|
| `lock_mode S` | 공유 락 — FK 체크 시 InnoDB가 자동으로 부모 레코드에 설정 |
| `lock_mode X` | 배타 락 — UPDATE 시 필요. S lock 보유 TX가 있으면 대기 |
| `lock_mode X locks rec but not gap waiting` | 갭 락 없이 레코드만 X lock 대기 중 |
| `HOLDS THE LOCK(S)` | 현재 이 TX가 보유한 락 목록 |
| `WAITING FOR THIS LOCK` | 이 TX가 획득하려고 대기 중인 락 |
| `WE ROLL BACK TRANSACTION (N)` | InnoDB가 선정한 데드락 희생자 |

### 데드락 없이 락 경합 상태 실시간 관찰

데드락이 발생하기 전, **락이 쌓이는 과정**을 보려면:

```sql
-- 현재 락 대기 중인 트랜잭션 목록
SELECT 
    r.trx_id AS waiting_trx,
    r.trx_mysql_thread_id AS waiting_thread,
    r.trx_query AS waiting_query,
    b.trx_id AS blocking_trx,
    b.trx_mysql_thread_id AS blocking_thread,
    b.trx_query AS blocking_query
FROM information_schema.innodb_lock_waits w
JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id
JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id;

-- 현재 보유 중인 모든 InnoDB 락 목록 (MySQL 8.x)
SELECT * FROM performance_schema.data_locks
WHERE OBJECT_NAME = 'product'  -- 테이블명 지정
ORDER BY ENGINE_TRANSACTION_ID;

-- 락 대기 그래프
SELECT * FROM performance_schema.data_lock_waits;
```

### 관찰 시나리오 — 단계별 체크포인트

```
[1단계] 테스트 실행 전
  → SHOW ENGINE INNODB STATUS → LATEST DETECTED DEADLOCK 섹션 없거나 오래됨

[2단계] 테스트 실행 직후 (빠르게 실행)
  → SELECT * FROM performance_schema.data_locks WHERE OBJECT_NAME='product'
  → lock_type: RECORD, lock_mode: S 가 두 rows 이상 보이면 FK S lock 확인

[3단계] 데드락 발생 후
  → SHOW ENGINE INNODB STATUS\G
  → LATEST DETECTED DEADLOCK 섹션 확인
  → (1) HOLDS S lock AND WAITING X lock 패턴 확인
  → (2) HOLDS S lock AND WAITING X lock 패턴 확인
  → WE ROLL BACK TRANSACTION 확인

[4단계] 애플리케이션 로그 확인]
  → DeadlockLoserDataAccessException 또는 아래 메시지 확인
  → "Deadlock found when trying to get lock; try restarting transaction"
```

---

## PHASE 3 보조 — application.yml 설정 (로그 레벨)

데드락과 SQL을 모두 콘솔에서 보려면:

```yaml
# application-dev.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql: TRACE  # 바인딩 파라미터 확인
    org.springframework.orm.jpa: DEBUG         # 트랜잭션 경계 확인
    com.zaxxer.hikari: DEBUG                   # 커넥션 풀 상태

# MySQL 드라이버 수준 쿼리 로그 (선택)
# datasource url에 추가: ?logger=com.mysql.cj.log.Slf4JLogger&profileSQL=true
```

---

## 정리 — FK 데드락의 핵심 메커니즘

```
자식 테이블 INSERT
    ↓
InnoDB FK 무결성 체크
    ↓
부모 레코드(product_id=N)에 S lock 자동 획득   ← @Lock 없어도 발생
    ↓
TX1과 TX2 모두 S lock 획득 성공 (공유 가능)
    ↓
TX1이 product UPDATE 시도 → X lock 필요 → TX2 S lock 대기
TX2가 product UPDATE 시도 → X lock 필요 → TX1 S lock 대기
    ↓
순환 대기 → DEADLOCK
    ↓
InnoDB가 한 TX를 victim으로 선정 → ROLLBACK
    ↓
애플리케이션: DeadlockLoserDataAccessException
```

> @Version 낙관락은 버전 불일치를 **감지**할 뿐, UPDATE 실행 시 X lock 획득은 동일하다.
> FK 잠금전파 데드락은 낙관락/비관락 여부와 **무관하게** 발생한다.
