---
name: create-endpoint
description: TDD 기반 REST 엔드포인트 생성 워크플로우. 새 API 엔드포인트를 처음부터 구현할 때 사용한다.
---

# Create Endpoint

## 1. 구현 순서 (Inside-Out TDD)

도메인 모델부터 시작하여 바깥 레이어로 확장한다.

```
1. Domain Model  →  2. Repository  →  3. Service  →  4. Facade  →  5. Controller  →  6. E2E Test
```

각 단계마다 **Red → Green → Refactor** 사이클을 반복한다.

## 2. 단계별 체크리스트

### 2.1 Domain Model

- [ ] 도메인 모델 클래스 생성
- [ ] `create()` 팩토리 메서드 (유효성 검증 포함, id = null)
- [ ] `reconstruct()` 팩토리 메서드 (검증 생략, id 포함)
- [ ] 생성자 `private` 제한
- [ ] 유효성 검증 순서: null → empty → 길이 → 포맷 → 비즈니스
- [ ] 입력값 정규화 (trim, toLowerCase 등) — `create()`에서만 수행
- [ ] 필요 시 Value Object 분리
- [ ] **테스트**: 정상 생성, 각 검증 규칙별 실패 케이스

### 2.2 Repository

- [ ] Command Repository 인터페이스 (save, delete)
- [ ] Query Repository 인터페이스 (find, exists)
- [ ] Entity 클래스 (`from(Domain)` + `toDomain()`)
- [ ] JPA Repository 인터페이스
- [ ] CommandRepositoryImpl 구현
- [ ] QueryRepositoryImpl 구현
- [ ] **테스트**: 저장 후 조회, 존재 여부 확인, 도메인 변환 정확성

### 2.3 Service

- [ ] CommandService 클래스 (`@Service`, `@Transactional`)
- [ ] 단일 도메인 비즈니스 로직 구현
- [ ] 필요 시 Domain Service 생성 (순수 Java, `@Bean` 등록)
- [ ] **테스트**: Mock 기반 단위 테스트, 정상/예외 케이스

### 2.4 Facade

- [ ] CommandFacade (`@Service`, `@Transactional`)
- [ ] QueryFacade (`@Service`, `@Transactional(readOnly = true)`)
- [ ] 유스케이스 오케스트레이션 (Service 호출 조합)
- [ ] 인증/인가 검증 (필요 시)
- [ ] **테스트**: Mock 기반 단위 테스트, 인증 실패 케이스 포함

### 2.5 Controller

- [ ] `@RestController` 클래스
- [ ] Request DTO (Jakarta Validation 적용)
- [ ] Response DTO (`from(OutDto)` 팩토리, 민감정보 제외)
- [ ] InDto / OutDto
- [ ] 엔드포인트 메서드 (`@PostMapping` 등)
- [ ] **테스트**: MockMvc 기반, 정상 응답 + Validation 실패 + 에러 응답

### 2.6 E2E Test

- [ ] `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- [ ] TestContainers Config Import
- [ ] `@AfterEach`에서 DB 초기화 (`DatabaseCleanUp.truncateAllTables()`)
- [ ] API 호출 헬퍼 메서드 작성 (테스트 데이터 생성)
- [ ] `@Nested` 클래스로 엔드포인트별 그룹화
- [ ] 정상 시나리오 + 모든 에러 시나리오 검증

## 3. 데이터 흐름 확인

```
Request
  → toInDto()
    → [Facade] 인증/인가 검증
      → [Service] 비즈니스 로직
        → [Domain] 도메인 검증 + 생성
          → [Repository] Entity.from(domain) → save → toDomain()
      → OutDto.from(domain)
    → [Controller] Response.from(outDto)
→ HTTP Response
```

## 4. 에러 타입 추가 (필요 시)

새 도메인의 비즈니스 에러가 필요한 경우:
1. `ErrorType` enum에 새 값 추가
2. `ErrorTypeTest` 업데이트 (provider + count)
3. 도메인 코드에서 `throw new CoreException(ErrorType.XXX)`
4. E2E 테스트에서 에러 응답 검증

## 5. 완료 기준

- [ ] 모든 레이어의 단위 테스트 통과
- [ ] E2E 테스트 통과 (정상 + 에러 시나리오)
- [ ] 코드 커버리지 목표 달성
- [ ] 불필요한 import, 디버그 코드 제거
- [ ] REQUIREMENTS.md의 해당 기능 요구사항과 1:1 매핑 확인
- [ ] 린트/포맷 검사 통과

## 6. 주의 사항

- 계층 건너뛰기 금지 (Controller → Facade → Service → Repository)
- 비즈니스 로직은 Domain Model/Domain Service에서만 작성
- Entity 업데이트 시 `Entity.from()` 사용 금지 (기존 엔티티 조회 후 수정)
- Response에 민감정보(password 등) 포함 금지
- 입력값 정규화는 Domain Model의 `create()` 팩토리에서만 수행
