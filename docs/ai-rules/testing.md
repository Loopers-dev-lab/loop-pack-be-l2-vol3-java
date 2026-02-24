# 테스트 규칙

## 원칙
- TDD → Red > Green > Refactor 순서 준수
- 3A 원칙 → Arrange / Act / Assert
- 가능한 행위 검증보다 상태 검증을 우선한다
- 단위 테스트는 Classist 기준을 따른다 (테스트 대상 클래스 1개를 협력 객체와 격리)
- 커버리지 목표 → 80% 이상
- CI → 모든 테스트 통과 필수

## 테스트 작성
- 테스트명 → `메서드명_조건_기대결과` 형식
- 새 기능 → 레이어 정책에 맞는 테스트 함께 작성
- 버그 수정 → 재현 테스트 먼저 작성
- 외부 의존성 → `@MockitoBean` / `Mockito.mock()` 처리

## 프로젝트 필수 회귀 테스트
- 보안 회귀: `toString()` 민감정보 마스킹 테스트 필수
- 비밀번호 정책: raw/encoded 경로 분리 테스트 필수
- 회원가입 중복: 저장 시점 중복키 예외(동시성 경합) 테스트 필수
- 이름 마스킹 경계값: 1/2/3글자 테스트 필수

## 도메인별 레시피
- 공통 인덱스: `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/testing-recipes/README.md`
- 사용자 도메인: `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/testing-recipes/user.md`
- 주문 도메인: `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/testing-recipes/order.md`
- 상품/좋아요 도메인: `/Users/anseonghun/Documents/project/loop-pack-be-l2-vol3-java/docs/ai-rules/testing-recipes/product-like.md`

## 레이어별 테스트 전략
- Domain / Domain Service → 단위 테스트
- Application Service → 통합 테스트
- Controller → 통합 테스트
- 핵심 사용자 시나리오 → E2E 테스트 필수

## 단위 테스트(Classist) 규칙
- 테스트 대상(SUT)은 1개 클래스만 둔다
- 협력 객체(Repository, 외부 API, 메시징, Clock 등)는 Mock/Stub으로만 격리한다 (Fake 사용 금지)
- 단위 테스트에서 DB/네트워크/파일 I/O를 직접 사용하지 않는다
- 상태 검증을 우선하되, 외부 협력 호출은 필요한 경우에만 최소한으로 검증한다
- private 메서드/구현 디테일을 직접 검증하지 않는다

## Spring 테스트 도구 가이드
- 단위 테스트 → `@ExtendWith(MockitoExtension.class)`
- 통합 테스트 → `@SpringBootTest` + Testcontainers
- 통합 테스트 DB는 개발/운영과 동일한 엔진/버전 이미지를 사용한다
- E2E 테스트 → 실제 API 경로 기준으로 시나리오 검증
- DB 격리 → `@Transactional` 또는 `@Sql` 로 초기화
- API 검증 → `MockMvc` 사용

## 금지
- 통합 테스트에서 H2 인메모리 DB 사용 금지
- 테스트 간 상태 공유 금지
- `println` 디버깅 코드 남기지 말 것
