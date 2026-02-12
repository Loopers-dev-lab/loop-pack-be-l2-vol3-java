# ADR (Architecture Decision Records)

### ADR-01: 크로스 도메인 트랜잭션 — Service 개별 트랜잭션 + Facade 보상

- **맥락**: 주문 생성 시 재고 차감(ProductService)과 주문 저장(OrderService)이 서로 다른 도메인에 걸친다.
- **결정**: 각 Service가 자기 도메인 내에서 개별 @Transactional. Facade에는 트랜잭션 없이 보상 로직으로 원자성 확보.
- **근거**: Facade에 트랜잭션을 두면 도메인 경계가 무너지고 트랜잭션 범위가 비대화됨.
- **기각된 대안**: Facade @Transactional로 단일 트랜잭션 → 도메인 독립성 훼손.

### ADR-02: OrderItem 스냅샷 패턴 — Product 런타임 참조 없음

- **맥락**: 주문 후 상품 가격/이름이 변경되면 주문 이력이 오염된다.
- **결정**: OrderItem에 주문 시점의 상품명/가격/브랜드명을 복사. Product와 FK 관계 없음.
- **근거**: Product 변경/삭제가 주문 이력에 영향을 주면 안 됨.
- **트레이드오프**: 역추적이 스냅샷 필드에 의존. 필요 시 productId를 참조용으로 추가 가능.

### ADR-03: likeCount 비정규화

- **맥락**: 상품 목록 정렬(likes_desc)에 좋아요 수가 필요. 매번 COUNT 쿼리 vs Product 필드 캐싱.
- **결정**: Product.likeCount 필드에 비정규화. 좋아요 등록/취소 시 UPDATE.
- **근거**: 상품 목록 조회마다 JOIN + COUNT는 성능 부담.
- **트레이드오프**: Like 테이블과 일시적 불일치 가능. 보정 배치로 해결 가능.
