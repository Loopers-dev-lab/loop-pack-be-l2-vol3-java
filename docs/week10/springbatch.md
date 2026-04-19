# Spring Batch 핵심 개념

## 1. Spring Batch란?

Spring Batch는 **대용량 데이터를 안정적으로 처리하기 위한 배치 프레임워크**다.

일반적인 웹 요청-응답 흐름과 달리, 배치는 **정해진 시점에 대량의 데이터를 일괄 처리**하는 방식이다.

### 핵심 특징

| 특징 | 설명 |
|------|------|
| **청크 기반 처리** | 데이터를 N건씩 나눠 읽고/가공하고/저장 → 메모리 효율적 |
| **실행 이력 관리** | Job 실행 상태, 시작/종료 시각을 DB에 자동 저장 |
| **재시작 가능** | 실패 시 처음부터가 아니라 실패 지점부터 재시작 |
| **Skip / Retry** | 특정 건 실패 시 건너뛰거나 재시도하는 정책 설정 가능 |
| **병렬 처리** | Step 병렬 실행, 파티셔닝으로 처리량 확장 가능 |

### 언제 사용하는가?

**Spring Batch가 적합한 경우:**
- 수십만 건 이상의 데이터를 주기적으로 처리해야 할 때
- 실패 시 재시작이 필요한 중요한 데이터 처리 (정산, 통계 등)
- 처리 결과를 추적/감사해야 할 때
- 읽기 → 가공 → 쓰기 흐름이 명확한 ETL 작업

**Spring Batch가 과한 경우:**
- 처리 건수가 적고 단순한 주기 작업 (→ `@Scheduled`로 충분)
- 실시간 이벤트 처리 (→ Kafka 등 메시지 큐 적합)

---

## 2. Spring Batch vs Spring Scheduler

두 개념은 **목적이 다른 독립적인 기술**이다.

### Spring Scheduler

**"언제 실행할 것인가"** 를 담당한다.

```java
@Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
public void doSomething() {
    // 임의의 작업
}
```

- 특정 시간/주기에 메서드를 **자동 실행**시키는 트리거
- `@EnableScheduling` + `@Scheduled` 만으로 동작
- 단순 반복 작업에 적합 (캐시 갱신, 알림 발송 등)
- 실패 시 재시도, 재시작 등의 **운영 기능 없음**

### Spring Batch

**"무엇을 어떻게 처리할 것인가"** 를 담당한다.

```
Job
 └─ Step
      ├─ ItemReader   (데이터 읽기)
      ├─ ItemProcessor (가공)
      └─ ItemWriter   (저장)
```

- 대용량 데이터를 **청크 단위**로 처리하는 프레임워크
- Job 실행 이력, 재시작, 재시도, Skip 등 **운영 기능 내장**
- `JobLauncher`로 실행 시점을 직접 제어
- 실패한 지점부터 **재시작 가능** (JobRepository에 상태 저장)

### 두 개념의 관계

```
[Spring Scheduler] ──트리거──▶ [Spring Batch Job 실행]
       "언제"                         "무엇을 어떻게"
```

실무에서는 **Scheduler로 Batch를 실행**하는 구조를 많이 사용한다.

```java
@Scheduled(cron = "0 0 2 * * *")
public void runRankingJob() {
    jobLauncher.run(rankingJob, new JobParameters(...));
}
```

### 언제 무엇을 쓸까?

| 상황 | 선택 |
|------|------|
| 캐시 갱신, 간단한 집계 | Scheduler만 |
| 수십만 건 데이터 처리, 실패 재시작 필요 | Batch (+ Scheduler로 트리거) |
| 외부에서 실행 시점 제어 필요 | Batch + Quartz or 별도 트리거 |

---

## 3. 전체 구조 (Job → Step → Tasklet/Chunk)

```
Job
 └── Step 1
      └── Tasklet  (단순 작업)
           또는
      └── Chunk  (대용량 처리)
           ├── ItemReader
           ├── ItemProcessor
           └── ItemWriter
 └── Step 2
      └── ...
```

---

## 4. Job & JobBuilder

| | |
|--|--|
| `Job` | 배치 처리의 실행 단위 (무엇을 할지 정의된 설계도) |
| `JobBuilder` | 그 `Job`을 만드는 도구 |

### JobBuilder를 사용하는 이유

**1. Job 구성 요소가 많아서**

```java
// 생성자로 만든다면?
new Job(name, jobRepository, incrementer, steps, listeners, validator, ...);
// → 파라미터가 너무 많고, 어떤 값이 뭔지 알기 어려움

// 빌더 패턴
new JobBuilder(JOB_NAME, jobRepository)
    .incrementer(...)
    .start(step1)
    .listener(...)
    .build();
// → 뭘 설정하는지 명확하게 보임
```

**2. 선택적 구성이 자연스러워서**

`incrementer`, `listener`, `validator` 등은 필수가 아니다. 빌더 패턴은 필요한 것만 체이닝하면 되지만, 생성자는 모든 경우의 수마다 오버로딩을 만들어야 한다.

**3. Job 내부 구현이 복잡해서**

Spring Batch의 `Job` 구현체는 `SimpleJob`, `FlowJob` 등 여러 종류가 있다. `JobBuilder`가 내부적으로 설정에 맞는 구현체를 골라서 생성해준다. 사용자는 어떤 구현체인지 몰라도 된다.

### 사용 예시

```java
new JobBuilder(JOB_NAME, jobRepository)
    .incrementer(new RunIdIncrementer())  // 매 실행마다 고유 ID 부여
    .start(categorySyncStep())
    .listener(jobListener)
    .build();
```

| 요소 | 역할 |
|------|------|
| `JobRepository` | 배치 메타데이터(실행 이력, 상태)를 DB에 저장 |
| `RunIdIncrementer` | 동일 파라미터로 재실행 가능하게 run.id를 자동 증가 |
| `JobListener` | Job 시작/종료 훅 (`@BeforeJob`, `@AfterJob`) |

---

## 5. Step & StepBuilder

```java
new StepBuilder(STEP_NAME, jobRepository)
    .tasklet(demoTasklet, new ResourcelessTransactionManager())
    .listener(stepMonitorListener)
    .build();
```

- `@JobScope` → Job 실행 단위로 빈 생성 (JobParameters 주입 가능)
- `ResourcelessTransactionManager` → Tasklet에서 DB 트랜잭션이 불필요할 때 사용

---

## 6. Tasklet vs Chunk

### Tasklet

```java
@StepScope  // Step 실행 단위로 빈 생성 → JobParameters 주입 가능
@Component
public class DemoTasklet implements Tasklet {
    @Value("#{jobParameters['requestDate']}")  // SpEL로 파라미터 주입
    private String requestDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 작업 수행
        return RepeatStatus.FINISHED;  // CONTINUABLE 반환 시 반복
    }
}
```

**용도**: 단순 작업, 파일 이동, 알림 발송 등 1회성 처리

### Chunk (대용량 처리)

```java
new StepBuilder("chunkStep", jobRepository)
    .<Input, Output>chunk(100, transactionManager)  // 100건씩 처리
    .reader(itemReader)
    .processor(itemProcessor)
    .writer(itemWriter)
    .build();
```

**용도**: 수십만 건 이상의 데이터 읽기/가공/저장

- **reader** → 데이터 소스에서 1건씩 읽음 (null 반환 시 종료)
- **processor** → 읽은 데이터를 변환/필터링 (선택 사항)
- **writer** → chunk 단위로 묶어서 저장

### Tasklet 내에서 Chunk 처리 직접 구현

일반적으로 구현의 용이성 등을 이유로 Tasklet 내에서 로직 상으로 Chunk Oriented Processing을 구현하기도 한다.

#### 순수 Chunk 방식과 비교

```java
// Spring Batch 순수 Chunk 방식
new StepBuilder("step", jobRepository)
    .<Order, Result>chunk(100, transactionManager)
    .reader(itemReader)
    .processor(itemProcessor)
    .writer(itemWriter)
    .build();

// Tasklet 내에서 Chunk 로직 직접 구현
@Override
public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    List<Order> orders = orderRepository.findAll();

    Lists.partition(orders, 100).forEach(chunk -> {
        List<Result> results = chunk.stream()
            .map(this::process)
            .toList();
        resultRepository.saveAll(results);
    });

    return RepeatStatus.FINISHED;
}
```

#### Tasklet 내 Chunk 구현을 선택하는 경우

**1. 데이터 소스가 ItemReader로 구현하기 까다로울 때**

외부 API 페이지네이션, 복잡한 커서 기반 쿼리 등 `ItemReader` 인터페이스에 맞추기 어려운 경우.

```java
@Override
public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    // 외부 API 페이지네이션을 직접 다루는 경우
    List<Order> orders = externalApiClient.fetchAllPages();

    Lists.partition(orders, 100).forEach(chunk -> {
        List<Result> results = chunk.stream()
            .map(this::process)
            .toList();
        resultRepository.saveAll(results);
    });

    return RepeatStatus.FINISHED;
}
```

**2. 여러 테이블/소스를 조합해서 처리할 때**

reader → processor → writer 의 1:1 흐름에 맞지 않는 복잡한 로직.

```java
@Override
public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    // 두 테이블을 조합해서 처리 - ItemReader 하나로 표현하기 어려움
    List<Order> orders = orderRepository.findAll();
    Map<Long, Product> productMap = productRepository.findAll()
        .stream()
        .collect(Collectors.toMap(Product::getId, p -> p));

    Lists.partition(orders, 100).forEach(chunk -> {
        List<Result> results = chunk.stream()
            .map(order -> process(order, productMap.get(order.getProductId())))
            .toList();
        resultRepository.saveAll(results);
    });

    return RepeatStatus.FINISHED;
}
```

**3. 트랜잭션 경계를 직접 제어하고 싶을 때**

Spring Batch Chunk는 chunk 단위로 트랜잭션이 자동으로 묶이는데, 더 세밀하게 제어하고 싶을 때.

```java
@Override
public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    List<Order> orders = orderRepository.findAll();

    Lists.partition(orders, 100).forEach(chunk -> {
        transactionTemplate.execute(status -> {
            // 직접 트랜잭션 경계 제어
            List<Result> results = chunk.stream()
                .map(this::process)
                .toList();
            resultRepository.saveAll(results);
            return null;
        });
    });

    return RepeatStatus.FINISHED;
}
```

#### 트레이드오프

| | Spring Batch Chunk | Tasklet 내 Chunk 구현 |
|--|-------------------|----------------------|
| 상태 관리 | 자동 (readCount, writeCount 등) | 직접 구현 필요 |
| 실패 재시작 | chunk 단위로 재시작 가능 | 처음부터 재시작 |
| Skip/Retry | 프레임워크 지원 | 직접 구현 필요 |
| 복잡한 데이터 소스 | ItemReader 구현 필요 | 자유롭게 구현 가능 |
| 코드 복잡도 | 낮음 | 높음 |

---

## 7. JobParameters

### 역할

| 역할 | 설명 |
|------|------|
| 실행 식별 | 동일 파라미터 중복 실행 방지 |
| 값 주입 | 실행 시점에 외부에서 조건 전달 |
| 재실행 허용 | `RunIdIncrementer`로 항상 새 실행으로 처리 |

### 1. Job 실행을 구분하는 식별자

Spring Batch는 **동일한 JobParameters로 이미 완료된 Job은 재실행을 막는다.**

```java
jobLauncher.run(rankingJob, params("date=2024-01-01"))  // 실행됨
jobLauncher.run(rankingJob, params("date=2024-01-01"))  // JobInstanceAlreadyCompleteException 발생
jobLauncher.run(rankingJob, params("date=2024-01-02"))  // 다른 파라미터 → 실행됨
```

날짜를 파라미터로 넘기는 이유가 바로 이 때문이다. 매일 다른 파라미터가 되어야 매일 실행이 가능하다.

### 2. 실행 시 외부에서 값 주입

같은 Job 코드를 **어떤 날짜/조건으로 실행할지 외부에서 제어**할 수 있다.

```java
// 실행 시 파라미터 전달
new JobParametersBuilder()
    .addLocalDate("targetDate", LocalDate.of(2024, 1, 1))
    .addString("mode", "full")
    .toJobParameters();

// Tasklet/Step 내부에서 SpEL로 수신
@Value("#{jobParameters['targetDate']}")
private String targetDate;
```

### 3. RunIdIncrementer로 중복 실행 허용

파라미터가 동일해도 무조건 실행시키고 싶을 때는 `RunIdIncrementer`를 사용한다.

```java
new JobBuilder(JOB_NAME, jobRepository)
    .incrementer(new RunIdIncrementer())  // 실행마다 run.id를 자동 증가
    .start(step1)
    .build();
```

`run.id=1`, `run.id=2` ... 로 파라미터가 달라지므로 매번 새 실행으로 인식된다.

---

## 8. Listener 구조

| 리스너 | 어노테이션 | 시점 |
|--------|-----------|------|
| `JobListener` | `@BeforeJob`, `@AfterJob` | Job 시작/종료 |
| `StepListener` | `@BeforeStep`, `@AfterStep` | Step 시작/종료 |
| `ChunkListener` | `@BeforeChunk`, `@AfterChunk` | 청크 처리 전/후 |

### JobListener

```java
@Component
public class JobMonitorListener {

    @BeforeJob
    public void beforeJob(JobExecution jobExecution) {
        // Job 시작 시 호출
        log.info("Job 시작: {}", jobExecution.getJobInstance().getJobName());
    }

    @AfterJob
    public void afterJob(JobExecution jobExecution) {
        // Job 종료 시 호출 (성공/실패 모두)
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error("Job 실패: {}", jobExecution.getAllFailureExceptions());
        }
    }
}
```

```java
new JobBuilder(JOB_NAME, jobRepository)
    .listener(jobMonitorListener)
    .start(step1)
    .build();
```

### StepListener

```java
@Component
public class StepMonitorListener {

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        log.info("Step 시작: {}", stepExecution.getStepName());
    }

    @AfterStep
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("Step 종료 - 읽은 건수: {}, 저장 건수: {}",
            stepExecution.getReadCount(),
            stepExecution.getWriteCount());
        return stepExecution.getExitStatus();
    }
}
```

```java
new StepBuilder(STEP_NAME, jobRepository)
    .tasklet(...)
    .listener(stepMonitorListener)
    .build();
```

### ChunkListener

```java
@Component
public class ChunkMonitorListener {

    @BeforeChunk
    public void beforeChunk(ChunkContext context) {
        log.info("청크 처리 시작");
    }

    @AfterChunk
    public void afterChunk(ChunkContext context) {
        log.info("청크 처리 완료 - 처리 건수: {}",
            context.getStepContext().getStepExecution().getWriteCount());
    }
}
```

---

## 9. 배치 메타데이터 테이블

### 역할

Spring Batch는 Job 실행 시 `JobRepository`를 통해 **실행 이력을 DB에 자동으로 기록**한다.
개발자가 직접 INSERT/UPDATE 하지 않아도 프레임워크가 알아서 처리한다.

이 이력 덕분에 아래가 가능하다:
- **재시작**: 실패한 Job을 처음부터가 아니라 실패 지점부터 재실행
- **중복 실행 방지**: 동일 파라미터로 이미 완료된 Job은 재실행 불가
- **모니터링**: 언제 얼마나 걸렸는지, 몇 건 처리됐는지 추적

### 테이블 구조 및 저장 시점

```
Job 시작
  → BATCH_JOB_INSTANCE        INSERT  (Job명 + 파라미터 조합, 처음 실행 시에만)
  → BATCH_JOB_EXECUTION       INSERT  (상태: STARTED, 시작 시각 기록)
  → BATCH_STEP_EXECUTION      INSERT  (Step 시작 시각 기록)

Job 종료
  → BATCH_STEP_EXECUTION      UPDATE  (상태: COMPLETED/FAILED, 종료 시각, 처리 건수)
  → BATCH_JOB_EXECUTION       UPDATE  (상태: COMPLETED/FAILED, 종료 시각)
```

| 테이블 | 저장 내용 |
|--------|----------|
| `BATCH_JOB_INSTANCE` | Job명 + 파라미터 조합 (논리적 실행 단위) |
| `BATCH_JOB_EXECUTION` | 실제 실행 이력 (상태, 시작/종료 시각) |
| `BATCH_JOB_EXECUTION_PARAMS` | 실행 시 전달된 파라미터 |
| `BATCH_STEP_EXECUTION` | Step별 실행 이력 (readCount, writeCount 등) |
| `BATCH_JOB_EXECUTION_CONTEXT` | Job 레벨 임시 데이터 저장소 (재시작 시 활용) |
| `BATCH_STEP_EXECUTION_CONTEXT` | Step 레벨 임시 데이터 저장소 (재시작 시 활용) |

### 테이블 생성 방법

테이블은 **개발자가 준비**해야 한다. `initialize-schema` 설정으로 방식을 선택한다.

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: always    # 앱 시작 시 Spring Batch가 자동 생성 (local/test)
      initialize-schema: never     # 개발자가 미리 직접 생성 (운영)
      initialize-schema: embedded  # 임베디드 DB(H2 등)일 때만 자동 생성
```

| 환경 | 설정 | 이유 |
|------|------|------|
| local / test | `always` | 편의상 자동 생성 |
| 운영 | `never` | DBA가 DDL 검토 후 직접 생성, 앱이 스키마를 건드리지 못하게 통제 |

운영에서 `never`로 쓸 때는 Spring Batch jar 안에 내장된 DDL 스크립트를 꺼내 사용한다.

```
org/springframework/batch/core/schema-mysql.sql
org/springframework/batch/core/schema-postgresql.sql
```

---

## 10. 재시작(Restart) 메커니즘

### 재시작 흐름

Spring Batch는 `BATCH_STEP_EXECUTION_CONTEXT` 테이블에 **처리 위치를 자동으로 저장**하기 때문에 실패 지점을 알고 재시작할 수 있다.

```
1회차 실행 (실패)
  → BATCH_STEP_EXECUTION_CONTEXT 에 현재 처리 위치 저장
     예: { "FlatFileItemReader.read.count": 3500 }
                                          ↑ 3500번째에서 실패

2회차 실행 (재시작)
  → Spring Batch가 BATCH_JOB_EXECUTION 에서 FAILED 상태 확인
  → BATCH_STEP_EXECUTION_CONTEXT 에서 마지막 위치 읽어옴
  → 3500번째부터 이어서 처리
```

### 위치를 저장하는 주체

**ItemReader가 청크 완료마다 자동으로 저장**한다.

```
청크 1 (1~100건)   처리 완료 → Context에 { count: 100 } 저장
청크 2 (101~200건) 처리 완료 → Context에 { count: 200 } 저장
청크 3 (201~300건) 처리 중 실패 → Context에는 { count: 200 } 남아있음

재시작 시 → 201번째부터 재개
```

### Tasklet은 자동 저장 안 됨

Tasklet은 `execute()` 호출 전체가 **트랜잭션 1개**로 묶인다. 즉, **청크 1개짜리**로 동작한다.

```
Chunk 방식
  [청크1: 1~100건] commit → context flush
  [청크2: 101~200건] commit → context flush
  [청크3: 201~300건] 실패 → rollback, context는 200까지 보존

Tasklet 방식
  [execute() 전체: 1~10000건] 실패 → rollback, context도 같이 rollback
  → 재시작 시 처음부터
```

| 방식 | 실패 지점 저장 | 재시작 위치 |
|------|-------------|------------|
| Spring Batch Chunk | 자동 (청크 단위) | 마지막 완료 청크 다음부터 |
| Tasklet | 직접 구현 필요 | 기본적으로 처음부터 |

### ExecutionContext로 위치 저장

`ExecutionContext`는 `BATCH_STEP_EXECUTION_CONTEXT` 테이블과 연결된 **Key-Value 저장소**다. 청크 커밋 시점에 자동으로 DB에 flush된다.

```java
context.putInt("key", 1);
context.putLong("key", 1L);
context.putString("key", "value");
context.put("key", serializableObject);  // 직렬화 가능한 객체도 가능
```

단순히 `ExecutionContext`에 저장한다고 해서 Tasklet에서 위치가 보존되지는 않는다. Tasklet 전체가 하나의 트랜잭션이므로 실패 시 context 저장도 같이 롤백되기 때문이다.

**실제로 위치를 보존하려면 내부에서 직접 트랜잭션을 나눠야 한다.**

```java
@Override
public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    ExecutionContext context = chunkContext.getStepContext()
        .getStepExecution().getExecutionContext();

    int lastProcessed = context.getInt("lastProcessed", 0);  // 이전 위치 복원
    List<Order> orders = orderRepository.findAllFromOffset(lastProcessed);

    Lists.partition(orders, 100).forEach(chunk -> {
        transactionTemplate.execute(status -> {
            chunk.forEach(this::process);
            context.putInt("lastProcessed", lastProcessed + currentIndex);  // 청크 커밋마다 위치 저장
            return null;
        });
    });

    return RepeatStatus.FINISHED;
}
```

이렇게 하지 않으면 Tasklet에서 `ExecutionContext` 저장은 사실상 의미가 없다.

### 재시작 비활성화

재시작이 오히려 문제가 되는 경우 끌 수 있다.

**Job 레벨 - 재시작 자체를 막기**

```java
new JobBuilder(JOB_NAME, jobRepository)
    .preventRestart()  // 실패해도 재시작 불가, 재실행 시 JobRestartException 발생
    .start(step1)
    .build();
```

**Step 레벨 - 재시작 횟수 제한**

```java
new StepBuilder(STEP_NAME, jobRepository)
    .tasklet(...)
    .startLimit(1)  // 최대 1번만 실행, 초과 시 StartLimitExceededException 발생
    .build();
```

**재시작을 막는 경우:**

| 상황 | 이유 |
|------|------|
| 멱등성이 없는 작업 (외부 API 호출, 이메일 발송 등) | 재시작 시 중복 실행 위험 |
| 항상 처음부터 다시 처리해야 하는 배치 | 이전 실패 상태를 이어받으면 안 됨 |
| 실패 시 수동 개입이 필요한 경우 | 자동 재시작보다 알람 → 확인 → 수동 실행 흐름 선호 |

---

## 11. 병렬 처리 전략

기본 동작은 청크를 순차적으로 처리하지만, Spring Batch는 4가지 병렬 처리 전략을 제공한다.

### 기본 동작 (순차)

```
Step1 → Step2 → Step3

청크 방식도 기본은 순차:
[청크1] → [청크2] → [청크3] → ...
```

### 1. Multi-threaded Step - 청크를 멀티스레드로

```java
new StepBuilder("step", jobRepository)
    .<Order, Result>chunk(100, transactionManager)
    .reader(itemReader)
    .processor(itemProcessor)
    .writer(itemWriter)
    .taskExecutor(new SimpleAsyncTaskExecutor())  // 청크마다 별도 스레드
    .build();
```

```
[청크1] ─┐
[청크2] ─┼─ 동시 실행
[청크3] ─┘
```

주의: ItemReader가 thread-safe 해야 함

### 2. Parallel Steps - Step을 병렬로

```java
new JobBuilder(JOB_NAME, jobRepository)
    .start(splitFlow())
    .build();

Flow splitFlow() {
    return new FlowBuilder<SimpleFlow>("splitFlow")
        .split(new SimpleAsyncTaskExecutor())
        .add(flow1(), flow2())  // flow1, flow2 동시 실행
        .build();
}
```

```
       ┌─ Step1(상품 집계)
Job ───┤                    → Step3(최종 저장)
       └─ Step2(주문 집계)
```

### 3. Partitioning - 데이터를 나눠서 병렬로

```java
new StepBuilder("masterStep", jobRepository)
    .partitioner("workerStep", partitioner)  // 데이터를 N개 파티션으로 분할
    .step(workerStep())
    .gridSize(4)             // 4개 파티션 → 4개 스레드
    .taskExecutor(executor)
    .build();
```

```
           ┌─ Worker(1~2500건)
Master ────┼─ Worker(2501~5000건)   동시 실행
           ├─ Worker(5001~7500건)
           └─ Worker(7501~10000건)
```

대용량 처리에 가장 효과적인 방식

### 4. Remote Chunking - 처리를 다른 서버로 분산

```
Master 서버: Reader만 실행 → Kafka/MQ → Worker 서버들: Processor + Writer
```

여러 서버에 분산 처리할 때 사용. 인프라 복잡도가 높아 잘 쓰이지 않음

### 정리

| 전략 | 병렬 단위 | 적합한 상황 |
|------|----------|------------|
| Multi-threaded Step | 청크 | 단일 서버, 처리량 향상 |
| Parallel Steps | Step | 독립적인 Step을 동시 실행 |
| Partitioning | 데이터 범위 | 대용량 데이터 분할 처리 |
| Remote Chunking | 서버 | 분산 환경 |

---

## 12. Job을 여러 개로 나누는 것과 Step으로 나누는 것의 차이

### Job을 여러 개로 나누면

```
Job1(데이터 수집) → Job2(가공) → Job3(저장)
```

- Job 간 실행 순서를 **외부에서 직접 제어**해야 함 (Scheduler, 스크립트 등)
- Job1 성공 여부를 확인하고 Job2를 실행하는 로직을 **개발자가 직접 구현**해야 함
- Job 간 데이터 공유가 어려움 (별도 저장소 필요)
- 실패 시 어느 Job부터 재시작할지 **외부에서 판단**해야 함

### Step으로 나누면

```
Job
 └─ Step1(데이터 수집)
 └─ Step2(가공)
 └─ Step3(저장)
```

- 실행 순서, 성공/실패 분기, 재시작을 **Spring Batch가 자동 관리**
- Step 간 데이터를 `ExecutionContext`로 자연스럽게 공유 가능
- 실패 시 해당 Step부터 재시작이 **프레임워크 레벨에서 보장**

### 언제 Job을 나눌까?

그렇다고 모든 것을 하나의 Job에 넣을 필요는 없다.

| 상황 | 선택 |
|------|------|
| 논리적으로 하나의 작업 흐름 | 하나의 Job + 여러 Step |
| 완전히 독립적인 작업 | 별도 Job |
| 실행 주기가 다른 작업 | 별도 Job |
| 한 작업의 실패가 다른 작업에 영향 없을 때 | 별도 Job |

예를 들어 "주문 정산"과 "상품 랭킹 집계"는 서로 관련이 없으니 별도 Job으로 나누는 게 맞고,
"랭킹 집계 → 집계 결과 저장 → 캐시 갱신"은 하나의 흐름이니 하나의 Job에 Step으로 나누는 게 맞다.

---

## 13. @ConditionalOnProperty 패턴

하나의 배치 앱에 여러 Job을 정의하고 실행 시점에 하나만 활성화하는 패턴.

```yaml
# application.yml
spring:
  batch:
    job:
      name: ${job.name:NONE}
```

```java
// 해당 Job 이름일 때만 빈 등록
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = DemoJobConfig.JOB_NAME)
@Configuration
public class DemoJobConfig { ... }
```

```bash
# 실행 시 Job 지정
java -jar commerce-batch.jar --job.name=demoJob
```

---

## 14. 테스트 방법

```java
@SpringBootTest
@SpringBatchTest  // JobLauncherTestUtils, JobRepositoryTestUtils 자동 주입
@TestPropertySource(properties = "spring.batch.job.name=" + DemoJobConfig.JOB_NAME)
class DemoJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Test
    void success() throws Exception {
        // given
        jobLauncherTestUtils.setJob(job);
        var jobParameters = new JobParametersBuilder()
            .addLocalDate("requestDate", LocalDate.now())
            .toJobParameters();

        // when
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.COMPLETED.getExitCode());
    }
}
```

- `@SpringBatchTest` → `JobLauncherTestUtils` 자동 주입
- `jobLauncherTestUtils.launchJob(params)` → 실제 Job 실행 후 `JobExecution` 반환
- `ExitStatus.COMPLETED / FAILED` 로 성공/실패 검증
