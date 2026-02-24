## ✅ Checklist

### 🏷 Product / Brand 도메인

- [ ]  상품 응답 DTO는 브랜드 정보, 좋아요 수를 포함한다 (엔티티 필드 직접 포함이 아님)
- [ ]  Product JPA 엔티티는 `brandId`(FK)로 Brand와 논리적으로 연결된다
- [ ]  상품의 정렬 조건(`latest`, `price_asc`, `likes_desc`) 을 고려한 조회 기능을 설계했다
- [ ]  상품은 재고를 가지고 있고, 주문 시 차감할 수 있어야 한다
- [ ]  재고의 음수 방지 처리는 도메인 레벨에서 처리된다
- [ ]  Brand name은 불변이고, 수정은 description/imageUrl만 허용한다
- [ ]  Product의 `brandId`는 수정 불가 규칙을 반영했다
- [ ]  고객 API는 Soft Delete 상품 제외, 어드민 API는 삭제 정보 포함 정책을 반영했다

### 👍 Like 도메인

- [ ]  좋아요는 유저와 상품 간의 관계로 별도 도메인으로 분리했다
- [ ]  상품의 좋아요 수는 상품 상세/목록 조회에서 함께 제공된다
- [ ]  단위 테스트에서 좋아요 등록/취소 흐름을 검증했다
- [ ]  좋아요는 토글이 아닌 등록(POST)/취소(DELETE)로 분리했다
- [ ]  `user_id + product_id` 유니크 제약으로 중복 좋아요를 방지한다
- [ ]  삭제된 상품 좋아요 요청 차단(400) 규칙을 반영했다

### 🛒 Order 도메인

- [ ]  주문은 여러 상품을 포함할 수 있으며, 각 상품의 수량을 명시한다
- [ ]  주문 시 상품의 재고 차감을 수행한다
- [ ]  재고 부족 예외 흐름을 고려해 설계되었다
- [ ]  단위 테스트에서 정상 주문 / 예외 주문 흐름을 모두 검증했다
- [ ]  주문 시점 스냅샷(상품명/가격/브랜드명)을 OrderItem에 저장한다
- [ ]  일부 상품 실패 시 전체 주문 실패(부분 성공 없음) 정책을 반영했다
- [ ]  주문 취소 시 상태 전이(ORDERED -> CANCELLED) 및 재고 복원을 반영했다
- [ ]  이미 취소된 주문 재취소는 409로 처리한다
- [ ]  주문 상세/목록 조회는 스냅샷 기준으로 응답한다
- [ ]  주문 목록 조회는 기간(startAt/endAt) + 페이지네이션을 반영한다

### 🧩 도메인 서비스

- [ ]  엔티티/VO 단독으로 표현하기 어려운 도메인 내부 규칙만 Domain Service에 둔다
- [ ]  엔티티/VO가 자체 책임질 수 있는 규칙은 Entity/VO에 둔다
- [ ]  상품 상세 조회 시 Product + Brand 정보 조합은 Application Layer 에서 처리했다
- [ ]  복합 유스케이스는 Application Layer에 존재하고, 도메인 로직은 위임되었다
- [ ]  도메인 서비스는 상태 없이, 동일한 도메인 경계 내의 도메인 객체의 협력 중심으로 설계되었다

### **🧱 소프트웨어 아키텍처 & 설계**

- [ ]  전체 프로젝트의 구성은 아래 아키텍처를 기반으로 구성되었다
    - Application → **Domain** ← Infrastructure
- [ ]  Application Layer는 도메인 객체를 조합해 흐름을 orchestration 했다
- [ ]  핵심 비즈니스 로직은 Entity, VO, Domain Service 에 위치한다
- [ ]  Repository Interface는 Domain Layer 에 정의되고, 구현체는 Infra에 위치한다
- [ ]  패키지는 계층 + 도메인 기준으로 구성되었다 (`/domain/order`, `/application/like` 등)
- [ ]  단일 Application Service 호출 유스케이스는 Controller -> Application Service로 직접 연결한다
- [ ]  Facade는 여러 Application Service 조합/오케스트레이션이 필요한 경우에만 사용한다
- [ ]  `@Transactional`은 Application Service에만 둔다
- [ ]  주문 유스케이스는 단일 트랜잭션 경계에서 재고확인 -> 차감 -> 주문생성 흐름을 보장한다

### 👤 User / 인증

- [ ]  회원가입 입력 규칙(loginId/password/name/email/birthDate)을 반영했다
- [ ]  사전 중복 체크 + 저장 시점 중복키 예외를 모두 409로 변환한다
- [ ]  비밀번호 정책 검증은 raw password에서만 수행한다
- [ ]  encoded password 경로는 정책 검증 대상에서 제외한다
- [ ]  이름 마스킹 규칙(1/2/3글자 경계 포함)을 반영했다
- [ ]  내 정보/비밀번호 변경은 `@AuthUser` 단일 인증으로 처리한다

### 🔐 Admin / 권한

- [ ]  어드민 API는 `X-ROOPERS-LDAP` 헤더 기반 인증을 사용한다
- [ ]  고객 API(`/api/v1`)와 어드민 API(`/api-admin/v1`) 경계를 분리했다
- [ ]  브랜드/상품/주문 어드민 조회 기능(목록/상세)을 반영했다

### 🧪 테스트 전략

- [ ]  Domain/Domain Service는 단위 테스트(Classist)로 검증한다
- [ ]  Application Service/Controller는 통합 테스트로 검증한다
- [ ]  핵심 시나리오는 E2E 테스트로 검증한다
- [ ]  통합 테스트는 Testcontainers 기반으로 운영과 동일 엔진/버전을 사용한다
- [ ]  보안 회귀: `toString` 민감정보 마스킹 테스트를 포함한다
- [ ]  경계값: 이름 마스킹 1/2/3글자 테스트를 포함한다
