# PROJECT CONTEXT

This is **loopers-java-spring-template**, a multi-module project based on Java 21 + Spring Boot 3.4.4.

## Tech Stack
- Java 21, Spring Boot 3.4.4, Spring Cloud 2024.0.1
- MySQL 8.0, Redis 7.0, Kafka 3.5.1
- JPA + QueryDSL, Lombok
- JUnit 5, Testcontainers, AssertJ, Mockito

## Module Structure
```
apps/           # Executable Spring Boot applications
  commerce-api      # REST API server
  commerce-batch    # Spring Batch application
  commerce-streamer # Kafka Consumer application
modules/        # Reusable configuration modules
  jpa, redis, kafka
supports/       # Add-on modules
  jackson, logging, monitoring
```

## Test Commands
```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :apps:commerce-api:test

# Run specific test class
./gradlew :apps:commerce-api:test --tests "ExampleModelTest"
```

## Test Conventions

| Test Type | File Suffix | Annotation | Purpose |
|-----------|-------------|------------|---------|
| Unit Test | `*Test.java` | None | Pure logic, domain models |
| Integration Test | `*IntegrationTest.java` | `@SpringBootTest` | Service + DB |
| E2E Test | `*E2ETest.java` | `@SpringBootTest(webEnvironment = RANDOM_PORT)` | Full API flow |

### Test Structure Pattern
```java
@DisplayName("기능을 수행할 때,")
@Nested
class MethodName {
    @DisplayName("조건이 충족되면, 기대 결과가 발생한다.")
    @Test
    void expectedBehavior_whenCondition() {
        // arrange
        // act
        // assert
    }
}
```

### Test Utilities
- **AssertJ only**: Use AssertJ for all assertions (do not mix with JUnit Assertions)
  - `assertThat()` for value assertions
  - `assertThatThrownBy()` for exception assertions
  - `assertThatCode()` for no-exception assertions
  - `assertAll()` to group multiple assertions (use JUnit 5's assertAll with AssertJ assertions inside)
- **DatabaseCleanUp**: Call `truncateAllTables()` in `@AfterEach`
- **RedisCleanUp**: Clean up after Redis tests
- **Testcontainers**: For MySQL, Redis, Kafka integration tests

## Local Environment
```bash
# Start local infrastructure (MySQL, Redis, Kafka)
docker-compose -f ./docker/infra-compose.yml up

# Connection info
# MySQL: localhost:3306 (application/application)
# Redis Master: localhost:6379
# Kafka: localhost:19092
```

# CODE QUALITY STANDARDS

- Eliminate duplication ruthlessly
- Express intent clearly through naming and structure
- Make dependencies explicit
- Keep methods small and focused on a single responsibility
- Minimize state and side effects
- Use the simplest solution that could possibly work
- Use Lombok (`@Getter`, `@RequiredArgsConstructor`, etc.) to reduce boilerplate
- Perform domain validation in entity constructors (throw `CoreException`)