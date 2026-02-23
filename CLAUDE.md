## 개발 규칙
### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)
#### 1. Red Phase : 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 테스트 예시
- 테스트시 H2 인메모리 데이터베이스 활용 (개발, 테스트 환경 분리)
#### 2. Green Phase : 테스트를 통과하는 코드 작성
- Red Phase 의 테스트가 모두 통과할 수 있는 코드 작성
- 오버엔지니어링 금지
#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- /application/ 내부 모든 클래스 private 함수 금지
- domain 서비스 private 함수 금지
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

## 아키텍처 규칙
- **Facade → Service만 호출**. Repository를 직접 접근하지 않는다.
- **Service → 자기 도메인 Repository만 접근**. 다른 도메인의 Service나 Repository를 호출하지 않는다.
- **Service 간 직접 호출 금지**. 크로스 도메인 협력은 반드시 Facade를 통해 이루어진다.
- **트랜잭션 위치**: `@Transactional`은 Domain Service 및 Application Service에만 위치한다. Facade에는 절대 `@Transactional`을 두지 않는다. 크로스 도메인 쓰기 시 각 Service가 자기 도메인 내에서 개별 트랜잭션을 관리하고, 실패 시 Facade에서 보상 로직을 처리한다.

## 비즈니스 규칙
- 결제 시스템 없음. 주문 완료 = 결제 완료로 취급한다.
- 주문 시점의 상품 정보를 스냅샷으로 저장한다 (주문 후 상품 변경에 영향받지 않도록).
- 어드민 인증은 X-ROOPERS-LDAP 헤더 기반으로 처리한다.
- 포인트 충전 기능은 범위 외 (Scope-out).

## 주의사항
### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java의 경우, Optional 활용할 것)
- Optional 변수에 null 할당 금지 (값이 없으면 Optional.empty() 사용할 것)
- Optional은 반환 타입으로만 사용할 것 (생성자, 수정자, 메소드 파라미터로 전달 금지)
- Collection을 Optional로 감싸지 말 것 (빈 Collection 반환할 것)
- 단순히 값을 얻으려는 목적으로만 Optional 사용 금지
- println 코드 남기지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지
5. 코드 뎁스는 1로 제한