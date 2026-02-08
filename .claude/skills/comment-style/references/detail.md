# Comment Style - 상세 레퍼런스

> 핵심 규칙: [../SKILL.md](../SKILL.md)

이 문서는 레이어별 전체 코드 예제, 인라인 주석 상세 예제, 안티패턴을 포함한다.

---

## 1. Domain Model 주석 패턴

### 필드 목록 Javadoc

```java

@Getter
@Builder(toBuilder = true)
public class Incentive {

	/**
	 * 인센티브
	 * - id: 인센티브 ID (PK)
	 * - tradeId: 거래 ID
	 * - tradeNumber: 거래 번호
	 * - partnerUuid: 인센티브 수령자 (상위 파트너) UUID
	 * - partnerType: 인센티브 수령자 타입
	 * - incentiveKind: 인센티브 종류 (EARN, REVERSAL)
	 * - createdAt: 생성일시
	 */

	private Long id;
	private Long tradeId;
	private String tradeNumber;
	private UUID partnerUuid;
	private PartnerType partnerType;
	private IncentiveKind incentiveKind;
	private LocalDateTime createdAt;

}
```

### 도메인 로직 메서드 주석

```java
/**
 * 도메인 로직
 * 1. 인센티브 생성 (EARN)
 * 2. 인센티브 취소 (REVERSAL)
 */

// 1. 인센티브 생성 (EARN)
public static Incentive createEarn(...) {

	// 거래 정보로 EARN 인센티브 생성
    ...
}


// 2. 인센티브 취소 (REVERSAL)
public static Incentive createReversal(...) {

	// 원본 인센티브 기반 REVERSAL 생성
    ...
}
```

---

## 2. Application Service / Facade 주석 패턴

의존성 그룹핑 + 클래스 Javadoc + 번호 매칭 전체 예제:

```java

@Service
@RequiredArgsConstructor
public class ItemFacade {

	// service
	private final ItemManagementService itemManagementService;
	// util
	private final ItemEventPublisher itemEventPublisher;


	/**
	 * 품목 관리 파사드
	 * 1. 품목 생성
	 * 2. 품목 수정
	 * 3. 품목 삭제
	 */

	// 1. 품목 생성
	@Transactional
	public void createItem(CreateItemInDto inDto) {

		// 입력 DTO → 도메인 모델 변환
		Items item = Items.from(inDto.toCommand());

		// 품목 저장
		Items saved = itemCommandRepository.save(item);

		// 생성 이벤트 발행
		itemEventPublisher.publishItemCreated(saved);
	}


	// 2. 품목 수정
	@Transactional
	public void updateItem(UpdateItemInDto inDto) {

		// 기존 품목 조회
		Items item = itemQueryRepository.findById(inDto.getId());

		// 품목 정보 수정
		item.update(inDto.toCommand());

		// 수정 저장
		itemCommandRepository.save(item);
	}


	// 3. 품목 삭제
	@Transactional
	public void deleteItem(Long itemId) {

		// 품목 존재 확인
		Items item = itemQueryRepository.findById(itemId);

		// 삭제 처리
		itemCommandRepository.delete(item);
	}

}
```

의존성 그룹핑 상세 예제:

```java
// ✅ CORRECT: 구조적 마커로 그룹핑
// service
private final UserManagementService userManagementService;
private final BranchManagementService branchManagementService;
// repository
private final UserQueryRepository userQueryRepository;
// port
private final PartnerRelationQueryPort partnerRelationQueryPort;
// util
private final ModelMapper modelMapper;
// event
private final TradeEventPublisher tradeEventPublisher;
```

```java
// ❌ WRONG: 그룹핑 없이 한국어 인라인 설명
private final UserManagementService userManagementService; // 유저 서비스
private final ModelMapper modelMapper; // 모델 매퍼
private final UserQueryRepository userQueryRepository; // 유저 조회
```

---

## 3. Controller 주석 패턴

```java

@RestController
@RequiredArgsConstructor
public class TradeManagementController {

	// service
	private final CreateTradeFacade createTradeFacade;
	// util
	private final ModelMapper modelMapper;


	/**
	 * 거래 관리 컨트롤러
	 * 1. (admin/agency) 거래 생성
	 */

	// 1. (admin/agency) 거래 생성
	@Operation(summary = "거래 생성", description = "거래를 생성합니다.")
	@PostMapping("")
	public ResponseEntity<BaseResponse<Void>> createTrade(
		@Valid @RequestBody CreateTradeRequest request
	) {

		// 요청 → InDto 변환
		CreateTradeInDto inDto = request.toInDto();

		// 거래 생성 실행
		createTradeFacade.createTrade(inDto);

		return ResponseEntity.ok(BaseResponse.success());
	}

}
```

---

## 4. Repository Implementation 주석 패턴

```java

@Repository
@RequiredArgsConstructor
public class ItemCommandRepositoryImpl implements ItemCommandRepository {

	// jpa
	private final ItemJpaRepository itemJpaRepository;
	// util
	private final ModelMapper modelMapper;


	// item 저장
	@Override
	public Items save(Items item) {

		// mapping
		ItemEntity entity = modelMapper.map(item, ItemEntity.class);

		// 저장
		ItemEntity saved = itemJpaRepository.save(entity);
		return modelMapper.map(saved, Items.class);
	}

}
```

---

## 5. Event Handler 주석 패턴

```java

@Component
@RequiredArgsConstructor
public class TradeCreatedEventHandler {

	/**
	 * 거래 생성 이벤트 처리 핸들러
	 * 1. 거래 생성 이벤트 처리
	 */

	// 1. 거래 생성 이벤트 처리
	@EventListener(TradeCreatedEvent.class)
	public void handleTradeCreatedEvent(TradeCreatedEvent event) {
		
		// 이벤트에서 거래 정보 추출
		Trades trade = event.getTrade();

		// 후속 처리 실행
        ...
	}

}
```

---

## 6. Global / Config 주석 패턴

```java

@Configuration
public class SecurityConfig {

	// JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 배치
    http.addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter .class);
}
```

---

## 7. Inline Comment 상세 예제

### Sequential Processing (Facade/Service)

```java
// ❌ WRONG: 인라인 주석 없는 다중 행 메서드
// 1. 거래 생성
@Transactional
public CreateTradeOutDto createTrade(CreateTradeInDto inDto) {
	BigDecimal carbonReduction = tradeCalculationService.calculate(inDto);
	Long branchId = branchQueryService.getBranchId(inDto.getBuyerUuid());
	Trades trade = Trades.from(CreateTradeCommand.of(inDto, carbonReduction, branchId));
	Trades saved = tradeCommandRepository.save(trade);
	tradeEventPublisher.publishTradeCreated(saved, partnerRelations);
	return modelMapper.map(saved, CreateTradeOutDto.class);
}


// ✅ CORRECT: 각 논리적 단계 설명
// 1. 거래 생성
@Transactional
public CreateTradeOutDto createTrade(CreateTradeInDto inDto) {

	// 탄소 감소량 계산
	BigDecimal carbonReduction = tradeCalculationService.calculate(inDto);

	// 구매자 지점 정보 조회
	Long branchId = branchQueryService.getBranchId(inDto.getBuyerUuid());

	// 거래 생성
	Trades trade = Trades.from(CreateTradeCommand.of(inDto, carbonReduction, branchId));
	Trades saved = tradeCommandRepository.save(trade);

	// 거래 생성 이벤트 발행
	tradeEventPublisher.publishTradeCreated(saved, partnerRelations);
	return modelMapper.map(saved, CreateTradeOutDto.class);
}
```

### Conditional Branching (Domain)

```java
// ❌ WRONG: 분기 로직에 인라인 주석 없음
public void validateSearchAccess(UserType userType, UUID userUuid) {
	if (userType == UserType.ADMIN) {
		return;
	}
	if (userType == UserType.SELLER && this.sellerUuid.equals(userUuid)) {
		return;
	}
	if (userType == UserType.BUYER && this.buyerUuid.equals(userUuid)) {
		return;
	}
	throw new BaseException(BaseResponseStatus.NO_PERMISSION);
}


// ✅ CORRECT: 각 분기 설명
public void validateSearchAccess(UserType userType, UUID userUuid) {

	// 관리자는 모든 접근 허용
	if (userType == UserType.ADMIN) {
		return;
	}

	// 판매자는 본인 거래만 조회 가능
	if (userType == UserType.SELLER && this.sellerUuid.equals(userUuid)) {
		return;
	}

	// 구매자는 본인 거래만 조회 가능
	if (userType == UserType.BUYER && this.buyerUuid.equals(userUuid)) {
		return;
	}

	throw new BaseException(BaseResponseStatus.NO_PERMISSION);
}
```

---

## 8. Anti-Patterns

### given/when/then 누락 (테스트)

테스트에서 `// given`, `// when`, `// then` 구분이 없으면 가독성과 유지보수성이 저하된다.

```java
// ❌ WRONG
@Test
@DisplayName("성공: 품목 생성")
void createItem_Success() {
	CreateItemCommand cmd = fixture.createCommand();
	Items item = Items.from(cmd);
	assertThat(item).isNotNull();
}


// ✅ CORRECT
@Test
@DisplayName("성공: 품목 생성")
void createItem_Success() {
	// given
	CreateItemCommand cmd = fixture.createCommand();

	// when
	Items item = Items.from(cmd);

	// then
	assertThat(item).isNotNull();
}
```

### 번호 불일치 (Numbered Method Mismatch)

클래스 Javadoc의 번호 목록과 메서드 `// N.` 주석이 일치하지 않으면 탐색 패턴이 깨진다.

```java
// ❌ WRONG: 번호 누락 또는 불일치
/**
 * 유저 관리 서비스
 * 1. 유저 생성
 * 2. 유저 수정
 */

// 유저 생성
public void createUser(...) { ...}  // ← 번호 없음


// 1. 유저 수정
public void updateUser(...) { ...}  // ← 잘못된 번호

// ✅ CORRECT: 정확한 번호 매칭


/**
 * 유저 관리 서비스
 * 1. 유저 생성
 * 2. 유저 수정
 */

// 1. 유저 생성
public void createUser(...) { ...}


// 2. 유저 수정
public void updateUser(...) { ...}
```

### Javadoc 위치 오류 (Class Declaration 위)

```java
// ❌ WRONG: 클래스 선언 위에 Javadoc 배치

/**
 * 품목 관리 파사드
 * 1. 품목 생성
 */
@Service
@RequiredArgsConstructor
public class ItemFacade {

	// service
	private final ItemManagementService itemManagementService;


	// 1. 품목 생성
	public void createItem(...) { ...}

}

// ✅ CORRECT: 클래스 본문 내부, 의존성 필드 뒤에 배치
@Service
@RequiredArgsConstructor
public class ItemFacade {

	// service
	private final ItemManagementService itemManagementService;


	/**
	 * 품목 관리 파사드
	 * 1. 품목 생성
	 */

	// 1. 품목 생성
	public void createItem(...) { ...}

}
```

### 영어 비즈니스 설명

```java
// ❌ WRONG
/**
 * Trade management service
 * 1. Create trade with auto-generated trade number
 */

// ✅ CORRECT
/**
 * 거래 관리 서비스
 * 1. 거래 생성 (거래번호 자동생성, 탄소감소량 계산 포함)
 */
```

### Block Comment 사용

```java
// ❌ WRONG
/* 거래를 생성하고 이벤트를 발행한다 */
public void createTrade(...) { ...}


// ✅ CORRECT
// 1. 거래 생성
public void createTrade(...) { ...}
```

### Over-Commenting

```java
// ❌ WRONG
// 모델 매퍼 생성
private final ModelMapper modelMapper;


// ID를 가져온다
public Long getId() {return id;}


// ✅ CORRECT
// util
private final ModelMapper modelMapper;
```
